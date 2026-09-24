package eu.kanade.tachiyomi.ui.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import eu.kanade.tachiyomi.data.cast.CompanionClient
import eu.kanade.tachiyomi.data.cast.CompanionCrypto
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class TvCompanionView(
    val address: String = "",
    val pairingName: String = "",
    val pairingCode: String = "",
    val connectedName: String = "",
    val error: String = "",
)

interface TvCompanionPlayback {
    suspend fun execute(type: String, media: TvCompanionMedia?, value: JsonObject): Boolean
    fun status(): JsonObject
    fun stop()
}

/** LAN-only Companion receiver. Nothing runs when the TV activity is closed. */
class TvCompanionReceiver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val playback: TvCompanionPlayback,
) {
    private data class Session(
        val id: String,
        val name: String,
        val peer: String,
        val commitment: String,
        val crypto: CompanionCrypto,
        val expiresAt: Long,
        var keys: CompanionCrypto.Keys? = null,
        var approved: Boolean = false,
        var touchedAt: Long = System.currentTimeMillis(),
        var lastSeq: Long = 0,
        var lastRequest: String = "",
        var lastReply: JsonObject? = null,
        var media: TvCompanionMedia? = null,
        var revoked: Boolean = false,
        var clientKey: String = "",
        var clientNonce: String = "",
    )

    private val mutable = MutableStateFlow(TvCompanionView())
    val state: StateFlow<TvCompanionView> = mutable
    private val id = randomHex()
    private var session: Session? = null
    private var server: Server? = null
    private var discovery: MulticastSocket? = null
    private var discoveryThread: Thread? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val timer = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "nyanime-companion-expiry").apply { isDaemon = true }
    }
    private var pairWindow = 0L
    private var pairAttempts = 0

    fun start() {
        if (server != null) return
        val host = localAddress() ?: run {
            mutable.value = TvCompanionView(error = "Collega la TV alla stessa rete del telefono.")
            return
        }
        val listener = runCatching { Server(host, CompanionClient.PORT).also { it.start(5_000, false) } }
            .getOrElse { runCatching { Server(host, 0).also { it.start(5_000, false) } }.getOrNull() }
        if (listener == null) {
            mutable.value = TvCompanionView(error = "Collegamento dal telefono non disponibile.")
            return
        }
        server = listener
        mutable.value = TvCompanionView(address = "$host:${listener.listeningPort}")
        startDiscovery(host, listener.listeningPort)
        timer.scheduleAtFixedRate({ expire() }, 1, 1, TimeUnit.SECONDS)
    }

    @Synchronized
    fun approve() {
        val current = session ?: return
        if (current.keys == null || System.currentTimeMillis() >= current.expiresAt) return
        current.approved = true
        current.touchedAt = System.currentTimeMillis()
        mutable.value = mutable.value.copy(
            pairingName = "",
            pairingCode = "",
            connectedName = current.name,
        )
    }

    @Synchronized
    fun reject() {
        session?.keys?.clear()
        session = null
        mutable.value = mutable.value.copy(pairingName = "", pairingCode = "", connectedName = "")
    }

    @Synchronized
    fun disconnect() {
        session?.revoked = true
        session?.keys?.clear()
        session = null
        playback.stop()
        mutable.value = mutable.value.copy(pairingName = "", pairingCode = "", connectedName = "")
    }

    fun close() {
        disconnect()
        discovery?.close()
        discovery = null
        discoveryThread?.interrupt()
        discoveryThread = null
        runCatching { multicastLock?.release() }
        multicastLock = null
        server?.stop()
        server = null
        timer.shutdownNow()
        mutable.value = TvCompanionView()
    }

    @Synchronized
    private fun expire() {
        val current = session ?: return
        val now = System.currentTimeMillis()
        if ((!current.approved && now >= current.expiresAt) ||
            (current.approved && now - current.touchedAt > 60_000)
        ) {
            disconnect()
        }
    }

    private inner class Server(host: String, port: Int) : NanoHTTPD(host, port) {
        override fun serve(request: IHTTPSession): Response {
            val host = mutable.value.address
            val peer = request.remoteIpAddress.orEmpty().removePrefix("::ffff:")
            if (!CompanionClient.isLanIpv4(peer) ||
                request.headers["host"] != host ||
                request.headers.containsKey("origin")
            ) {
                return reply(403)
            }
            if (!request.uri.startsWith(CompanionClient.PATH)) return reply(404)
            val path = request.uri.removePrefix(CompanionClient.PATH.removeSuffix("/"))
            if (request.method == Method.GET && path == "/info") {
                return json(
                    buildJsonObject {
                        put("protocol", "nyanime-cast")
                        put("version", 1)
                        put("id", id)
                        put("name", "Nyanime TV")
                        put(
                            "capabilities",
                            buildJsonObject {
                                put(
                                    "formats",
                                    kotlinx.serialization.json.buildJsonArray {
                                        add(JsonPrimitive("hls"))
                                        add(JsonPrimitive("mp4"))
                                        add(JsonPrimitive("webm"))
                                        add(JsonPrimitive("mkv"))
                                    },
                                )
                                put("volume", true)
                                put("brightness", true)
                                put("subtitles", true)
                            },
                        )
                    },
                )
            }
            if (request.method != Method.POST ||
                !request.headers["content-type"].orEmpty().startsWith("application/json")
            ) {
                return reply(405)
            }
            val size = request.headers["content-length"]?.toLongOrNull() ?: return reply(400)
            if (size !in 1..96 * 1024) return reply(413)
            val input = try {
                val parts = mutableMapOf<String, String>()
                request.parseBody(parts)
                Json.parseToJsonElement(parts["postData"].orEmpty()).jsonObject
            } catch (_: Exception) {
                return reply(400)
            }
            return try {
                when (path) {
                    "/pair" -> pair(peer, input)
                    "/reveal" -> reveal(peer, input)
                    else -> if (path.startsWith("/session/")) {
                        exchange(peer, path.substringAfterLast('/'), input)
                    } else {
                        reply(404)
                    }
                }
            } catch (_: Exception) {
                reply(403)
            }
        }
    }

    @Synchronized
    private fun pair(peer: String, input: JsonObject): NanoHTTPD.Response {
        val now = System.currentTimeMillis()
        if (now - pairWindow > 60_000) {
            pairWindow = now
            pairAttempts = 0
        }
        if (++pairAttempts > 5) return reply(429)
        if (session?.let { !it.revoked && (it.approved || now < it.expiresAt) } == true) return reply(409)
        require(input["version"]?.jsonPrimitive?.content == "1")
        require(input["receiverId"]?.jsonPrimitive?.content == id)
        val name = input.getValue("name").jsonPrimitive.content
        require(name.isNotBlank() && name.length <= 64 && name.none { it.code < 32 || it.code == 127 })
        val commitment = input.getValue("commitment").jsonPrimitive.content
        CompanionCrypto.decode(commitment, 32)
        val next = Session(randomHex(), name, peer, commitment, CompanionCrypto(), now + 120_000)
        session?.keys?.clear()
        session = next
        mutable.value = mutable.value.copy(pairingName = "", pairingCode = "", connectedName = "")
        return json(
            buildJsonObject {
                put("version", 1)
                put("receiverId", id)
                put("sessionId", next.id)
                put("commitment", next.crypto.commitment)
            },
        )
    }

    @Synchronized
    private fun reveal(peer: String, input: JsonObject): NanoHTTPD.Response {
        val current = session ?: return reply(401)
        if (current.peer != peer ||
            current.approved ||
            System.currentTimeMillis() >= current.expiresAt ||
            input["sessionId"]?.jsonPrimitive?.content != current.id
        ) {
            return reply(401)
        }
        val clientKey = input.getValue("publicKey").jsonPrimitive.content
        val clientNonce = input.getValue("nonce").jsonPrimitive.content
        if (current.keys == null) {
            current.keys = current.crypto.completeAsReceiver(
                id,
                current.id,
                current.name,
                clientKey,
                clientNonce,
                current.commitment,
            )
            current.clientKey = clientKey
            current.clientNonce = clientNonce
            mutable.value = mutable.value.copy(
                pairingName = current.name,
                pairingCode = current.keys!!.code,
            )
        } else {
            require(clientKey == current.clientKey && clientNonce == current.clientNonce)
        }
        return json(
            buildJsonObject {
                put("version", 1)
                put("receiverId", id)
                put("sessionId", current.id)
                put("publicKey", current.crypto.publicKey)
                put("nonce", current.crypto.nonce)
            },
        )
    }

    @Synchronized
    private fun exchange(peer: String, sessionId: String, envelope: JsonObject): NanoHTTPD.Response {
        val current = session ?: return reply(401)
        if (current.peer != peer ||
            current.id != sessionId ||
            current.keys == null ||
            (!current.approved && System.currentTimeMillis() >= current.expiresAt)
        ) {
            return reply(401)
        }
        val seq = envelope.getValue("seq").jsonPrimitive.long
        val serialized = envelope.toString()
        if (seq == current.lastSeq && serialized == current.lastRequest) {
            return current.lastReply?.let(::json) ?: reply(409)
        }
        if (seq <= current.lastSeq || seq > 9_007_199_254_740_991L) return reply(403)
        val keys = current.keys!!
        val command = CompanionCrypto.open(keys.client, current.id, "client", seq, envelope)
        current.lastSeq = seq
        current.lastRequest = serialized
        current.lastReply = null
        current.touchedAt = System.currentTimeMillis()
        val result = try {
            execute(current, command)
        } catch (error: Exception) {
            buildJsonObject {
                put("ok", false)
                put("error", error.message?.take(180) ?: "Comando non disponibile")
            }
        }
        val sealed = CompanionCrypto.seal(keys.server, current.id, "server", seq, result)
        current.lastReply = sealed
        return json(sealed)
    }

    private fun execute(current: Session, command: JsonObject): JsonObject {
        val type = command.getValue("type").jsonPrimitive.content
        if (type == "pairStatus") {
            return buildJsonObject {
                put("ok", true)
                put("approved", current.approved)
            }
        }
        if (type == "cancelPair" && current.media == null) {
            reject()
            return buildJsonObject { put("ok", true) }
        }
        require(current.approved && !current.revoked) { "Conferma il collegamento sulla TV" }
        if (type == "status") {
            return buildJsonObject {
                put("ok", true)
                put("playback", playback.status())
            }
        }
        require(type in setOf("load", "pause", "seek", "volume", "brightness", "subtitle", "stop"))
        val media = if (type == "load") {
            TvCompanionMediaValidator.parse(command.getValue("media").jsonObject, current.peer)
        } else {
            null
        }
        if (media == null) require(command["mediaId"]?.jsonPrimitive?.content == current.media?.id)
        when (type) {
            "pause" -> command.getValue("value").jsonPrimitive.boolean
            "seek" -> require(command.getValue("value").jsonPrimitive.long in 0..604_800_000L)
            "volume", "brightness" -> require(command.getValue("value").jsonPrimitive.double in 0.0..1.0)
            "subtitle" -> require(
                command.getValue("value").jsonPrimitive.content.toInt() in
                    -1 until (current.media?.subtitles?.size ?: 0),
            )
        }
        val accepted = runBlocking {
            withTimeout(8_000) { playback.execute(type, media, command) }
        }
        require(accepted) { "La TV non ha eseguito il comando" }
        if (media != null) current.media = media
        if (type == "stop") current.revoked = true
        return buildJsonObject {
            put("ok", true)
            put("playback", playback.status())
        }
    }

    private fun json(body: JsonObject): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", body.toString())
            .apply { addHeader("Cache-Control", "no-store") }

    private fun reply(code: Int): NanoHTTPD.Response {
        val status = NanoHTTPD.Response.Status.lookup(code) ?: NanoHTTPD.Response.Status.BAD_REQUEST
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", "{}")
            .apply { addHeader("Cache-Control", "no-store") }
    }

    private fun startDiscovery(host: String, port: Int) {
        runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("nyanime-companion").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        discoveryThread = Thread({
            runCatching {
                MulticastSocket(1900).use { socket ->
                    discovery = socket
                    socket.reuseAddress = true
                    socket.soTimeout = 1000
                    socket.joinGroup(InetAddress.getByName("239.255.255.250"))
                    val data = ByteArray(2048)
                    while (!socket.isClosed) {
                        val packet = DatagramPacket(data, data.size)
                        try {
                            socket.receive(packet)
                        } catch (_: java.net.SocketTimeoutException) {
                            continue
                        }
                        val text = String(packet.data, packet.offset, packet.length)
                        if (!text.contains("M-SEARCH", true) ||
                            !text.contains(CompanionClient.DISCOVERY, true)
                        ) {
                            continue
                        }
                        val reply = "HTTP/1.1 200 OK\r\nST: ${CompanionClient.DISCOVERY}\r\n" +
                            "USN: uuid:$id::${CompanionClient.DISCOVERY}\r\n" +
                            "LOCATION: http://$host:$port${CompanionClient.PATH}info\r\n" +
                            "CACHE-CONTROL: max-age=30\r\n\r\n"
                        val bytes = reply.toByteArray()
                        socket.send(DatagramPacket(bytes, bytes.size, packet.address, packet.port))
                    }
                }
            }
        }, "nyanime-companion-discovery").apply {
            isDaemon = true
            start()
        }
    }

    private fun localAddress(): String? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val preferred = manager.allNetworks.asSequence().filter { network ->
            manager.getNetworkCapabilities(network)?.let { capabilities ->
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } == true
        }.flatMap { network ->
            manager.getLinkProperties(network)?.linkAddresses.orEmpty().asSequence()
        }.mapNotNull { it.address as? Inet4Address }
            .map { it.hostAddress.orEmpty() }
            .firstOrNull(CompanionClient::isLanIpv4)
        if (preferred != null) return preferred
        return NetworkInterface.getNetworkInterfaces()?.toList()
            ?.filter {
                it.isUp &&
                    !it.isLoopback &&
                    (it.name.startsWith("wlan") || it.name.startsWith("eth"))
            }
            ?.flatMap { it.inetAddresses.toList() }
            ?.filterIsInstance<Inet4Address>()
            ?.map { it.hostAddress.orEmpty() }
            ?.firstOrNull(CompanionClient::isLanIpv4)
    }

    private fun randomHex(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }
}
