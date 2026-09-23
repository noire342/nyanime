package eu.kanade.tachiyomi.data.watch

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import eu.kanade.tachiyomi.data.reading.ReadingEdit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A room is resumable only until its invitation expires; it never enters Android or Nyanime backups. */
internal class WatchRoomArchive(context: Context) {
    @Serializable
    data class Saved(
        val invite: WatchInvite,
        val identity: String,
        val name: String,
        val edits: List<ReadingEdit> = emptyList(),
    )

    private val file = AtomicFile(File(context.noBackupFilesDir, "watch-room.pending"))
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<Pair<Long, Saved>>(Channel.CONFLATED)
    private var generation = 0L
    private var saved: Saved? = null
    private val mutableSaveFailed = MutableStateFlow(false)
    val saveFailed = mutableSaveFailed.asStateFlow()
    private val alias = "nyanime.watch.room.v1"

    init {
        scope.launch {
            for ((revision, snapshot) in writes) {
                val failed = runCatching {
                    synchronized(lock) {
                        if (revision == generation) write(snapshot)
                    }
                }.isFailure
                mutableSaveFailed.value = failed
                if (failed) {
                    delay(5000)
                    writes.trySend(revision to snapshot)
                }
            }
        }
    }

    fun load(): Saved? = synchronized(lock) {
        saved?.let { cached ->
            if (runCatching { cached.invite.validate(System.currentTimeMillis()) }.isSuccess) {
                return@synchronized cached
            }
            clearLocked()
        }
        if (!file.baseFile.exists()) return@synchronized null
        val result = runCatching {
            val bytes = file.readFully()
            require(bytes.size in 29..8_000_000)
            val decoded = watchJson.decodeFromString<Saved>(open(bytes).decodeToString())
            decoded.invite.validate(System.currentTimeMillis())
            require(decoded.identity.matches(Regex("[0-9a-f]{64}")))
            require(decoded.name.length <= 32 && decoded.edits.size <= 8192)
            require(decoded.edits.all(ReadingEdit::valid))
            decoded
        }.getOrNull()
        if (result == null) clearLocked() else saved = result
        result
    }

    fun begin(invite: WatchInvite, identity: WatchIdentity, name: String) = synchronized(lock) {
        val secret = identity.sessionSecret()
        val previous = load()
        val edits = previous?.edits?.takeIf {
            previous.invite.secret == invite.secret && previous.identity == secret.watchHex()
        }.orEmpty()
        saved = Saved(invite, secret.watchHex(), name, edits)
        secret.fill(0)
        generation++
        write(saved!!)
        mutableSaveFailed.value = false
    }

    fun update(edits: List<ReadingEdit>) = synchronized(lock) {
        val current = saved ?: return@synchronized
        if (edits.size > 8192) return@synchronized
        saved = current.copy(edits = edits.toList())
        writes.trySend(generation to saved!!)
    }

    fun clear() = synchronized(lock) { clearLocked() }

    private fun clearLocked() {
        generation++
        saved = null
        mutableSaveFailed.value = false
        file.delete()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun write(snapshot: Saved) {
        val bytes = watchJson.encodeToString(Saved.serializer(), snapshot).toByteArray()
        require(bytes.size <= 7_900_000)
        val sealed = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key())
            iv + doFinal(bytes)
        }
        val output = file.startWrite()
        try {
            output.write(sealed)
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    private fun open(bytes: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOf(12)))
        doFinal(bytes.copyOfRange(12, bytes.size))
    }
}
