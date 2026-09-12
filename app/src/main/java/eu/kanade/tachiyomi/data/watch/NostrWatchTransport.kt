package eu.kanade.tachiyomi.data.watch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Outgoing TLS connections only: NAT traversal is unnecessary. Independent relays see random room
 * tags and short encrypted events, never a stream. Each connection has one bounded subscription.
 * All mutable network state and crypto run on the actor, away from the UI/player thread.
 */
class NostrWatchTransport(
    private val invite: WatchInvite,
    private val identity: WatchIdentity,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS).build(),
    private val wallMillis: () -> Long = System::currentTimeMillis,
) : WatchTransport {
    override val publicKey: String = identity.publicKey
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inbox = Channel<() -> Unit>(64)
    private val crypto = WatchCrypto(invite, identity)
    private val sockets = mutableMapOf<String, WebSocket>()
    private val connected = mutableSetOf<String>()
    private val retries = mutableMapOf<String, Int>()
    private val retryJobs = mutableMapOf<String, Job>()
    private val seen = LinkedHashSet<String>()
    private val subscription = watchBase64(watchRandom(12))

    @Volatile private var closed = false
    private var onMessage: (String, WatchMessage) -> Unit = { _, _ -> }
    private var onConnection: (Int) -> Unit = {}

    override fun start(onMessage: (String, WatchMessage) -> Unit, onConnection: (Int) -> Unit) {
        this.onMessage = onMessage
        this.onConnection = onConnection
        scope.launch {
            for (action in inbox) {
                if (closed) break
                runCatching(action)
            }
        }
        inbox.trySend { invite.relays.forEach(::connect) }
    }

    private fun connect(url: String) {
        if (closed) return
        val socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    enqueue {
                        if (sockets[url] !== webSocket) return@enqueue
                        webSocket.send(
                            buildJsonArray {
                                add(JsonPrimitive("REQ"))
                                add(JsonPrimitive(subscription))
                                add(
                                    buildJsonObject {
                                        put("kinds", JsonArray(listOf(JsonPrimitive(WatchCrypto.KIND))))
                                        put("#x", JsonArray(listOf(JsonPrimitive(invite.topic))))
                                        put("limit", 0)
                                    },
                                )
                            }.toString(),
                        )
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.length > 16_384) return
                    enqueue {
                        if (sockets[url] !== webSocket) return@enqueue
                        receive(url, text)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    enqueue { if (sockets[url] === webSocket) disconnected(url) }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                    enqueue { if (sockets[url] === webSocket) disconnected(url) }
                }
            },
        )
        sockets[url] = socket
        // A TCP/WebSocket connection alone does not prove that the subscription is usable.
        retryJobs[url]?.cancel()
        retryJobs[url] = scope.launch {
            delay(12_000)
            enqueue { if (sockets[url] === socket && url !in connected) disconnected(url) }
        }
    }

    private fun receive(url: String, text: String) {
        val array = watchJson.parseToJsonElement(text) as? JsonArray ?: return
        when (array.firstOrNull()?.jsonPrimitive?.content) {
            "EOSE" -> if (array.getOrNull(1)?.jsonPrimitive?.content == subscription) {
                connected.add(url)
                retries[url] = 0
                retryJobs.remove(url)?.cancel()
                onConnection(connected.size)
            }
            "CLOSED" -> disconnected(url)
            "OK" -> if (array.getOrNull(2)?.jsonPrimitive?.booleanOrNull == false) {
                val reason = array.getOrNull(3)?.jsonPrimitive?.content.orEmpty()
                if (!reason.startsWith("duplicate:") && !reason.startsWith("mute:")) disconnected(url)
            }
            "EVENT" -> {
                if (array.size != 3 || array[1].jsonPrimitive.content != subscription) return
                val event = watchJson.decodeFromString<WatchEvent>(array[2].jsonObject.toString())
                if (event.pubkey == publicKey || event.id in seen) return
                val message = crypto.open(event, wallMillis()) ?: return
                seen.add(event.id)
                if (seen.size > 512) seen.remove(seen.first())
                onMessage(event.pubkey, message)
            }
        }
    }

    private fun disconnected(url: String) {
        sockets.remove(url)?.cancel()
        connected.remove(url)
        onConnection(connected.size)
        retryJobs.remove(url)?.cancel()
        val attempt = (retries[url] ?: 0).coerceAtMost(5)
        retries[url] = attempt + 1
        retryJobs[url] = scope.launch {
            delay(minOf(30_000L, 1000L shl attempt) + Random.nextLong(500))
            enqueue { connect(url) }
        }
    }

    override fun send(message: WatchMessage) {
        enqueue {
            if (connected.isEmpty()) return@enqueue
            val event = crypto.seal(message, wallMillis())
            val payload = """["EVENT",""" + watchJson.encodeToString(event) + "]"
            connected.toList().forEach { url ->
                if (sockets[url]?.send(payload) != true) disconnected(url)
            }
        }
    }

    private fun enqueue(action: () -> Unit) {
        if (!closed) inbox.trySend(action)
    }

    override fun close() {
        if (closed) return
        // Preserve ordering so an explicit leave can reach the sockets before shutting them down.
        inbox.trySend {
            closed = true
            sockets.values.forEach { it.close(1000, null) }
            sockets.clear()
            identity.clear()
            inbox.close()
            scope.cancel()
            client.connectionPool.evictAll()
        }.also {
            if (it.isFailure) {
                closed = true
                scope.cancel()
                client.dispatcher.cancelAll()
                identity.clear()
            }
        }
    }
}
