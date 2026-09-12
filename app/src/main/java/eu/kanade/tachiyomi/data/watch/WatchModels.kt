package eu.kanade.tachiyomi.data.watch

import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** Catalog references identify content; resolved stream URLs, cookies, headers and video bytes never enter the room. */
@Serializable
data class WatchMedia(
    val title: String,
    val episode: String,
    val number: Double,
    val duration: Double,
    val sourceId: Long = 0,
    val animeUrl: String = "",
    val episodeUrl: String = "",
) {
    val key: String get() = if (animeUrl.isNotEmpty() && episodeUrl.isNotEmpty()) {
        sourceId.toString() + ":" + animeUrl + ":" + episodeUrl
    } else {
        normalize(title) + ":" + if (number > 0) number else normalize(episode)
    }

    fun matches(other: WatchMedia): Boolean = key == other.key && compatibleDuration(other)

    fun compatibleDuration(other: WatchMedia): Boolean =
        duration > 0 &&
            other.duration > 0 &&
            abs(duration - other.duration) <= maxOf(3.0, minOf(duration, other.duration) * 0.005)

    fun valid(): Boolean = title.length in 1..240 &&
        episode.length <= 240 &&
        number.isFinite() &&
        number in -1.0..100_000.0 &&
        duration.isFinite() &&
        duration in 0.0..86_400.0 &&
        animeUrl.length <= 1024 &&
        episodeUrl.length <= 1024

    companion object {
        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    }
}

data class WatchPlayback(
    val media: WatchMedia?,
    val position: Double,
    val paused: Boolean,
    val ready: Boolean,
    val buffering: Boolean,
    val speed: Double,
    val ended: Boolean = false,
)

/** A source may refresh an episode URL. Only the exact locally resolved entry inherits the room identity. */
data class WatchSelection(val remote: WatchMedia, val localKey: String, val animeId: Long, val episodeId: Long) {
    fun applyTo(sample: WatchPlayback): WatchPlayback = if (sample.media?.key == localKey) {
        sample.copy(media = remote.copy(duration = sample.media.duration))
    } else {
        sample
    }
}

/** Called only on the controller's dispatcher (the Android main thread in production). */
interface WatchPlayer {
    fun sample(): WatchPlayback
    fun pause(paused: Boolean)
    fun seek(seconds: Double)
    fun speed(value: Double)
    fun userResumed() {}
}

enum class WatchPhase {
    Idle,
    Connecting,
    Waiting,
    Playing,
    Paused,
    Buffering,
    Reconnecting,
    DifferentVideo,
    Closed,
    Failed,
}

data class WatchMember(val id: String, val name: String, val ready: Boolean, val buffering: Boolean)

data class WatchRoomState(
    val phase: WatchPhase = WatchPhase.Idle,
    val active: Boolean = false,
    val host: Boolean = false,
    val invite: String = "",
    val media: WatchMedia? = null,
    val members: List<WatchMember> = emptyList(),
    val relayCount: Int = 0,
    val sharedControls: Boolean = true,
    val waitForEveryone: Boolean = true,
    val latencyMs: Long? = null,
    val driftMs: Long? = null,
    val localHold: Boolean = false,
    val playRequested: Boolean = false,
    val message: String = "",
)

@Serializable
enum class WatchMessageType { Hello, Status, Ping, Pong, Command, Timeline, Leave, Closed }

@Serializable
data class WatchPeerStatus(
    val name: String,
    val ready: Boolean,
    val buffering: Boolean,
    val media: WatchMedia? = null,
)

@Serializable
data class WatchMessage(
    val version: Int = 1,
    val type: WatchMessageType,
    val sequence: Long,
    val at: Long,
    val media: WatchMedia? = null,
    val position: Double = 0.0,
    val paused: Boolean = true,
    val playRequested: Boolean = false,
    val speed: Double = 1.0,
    val seekRevision: Long = 0,
    val ready: Boolean = false,
    val buffering: Boolean = false,
    val name: String = "",
    val target: String = "",
    val ping: Long = 0,
    val command: String = "",
    val sharedControls: Boolean = true,
    val waitForEveryone: Boolean = true,
    val peers: Map<String, WatchPeerStatus> = emptyMap(),
    val acknowledgements: Map<String, Long> = emptyMap(),
) {
    fun valid(): Boolean = version == 1 &&
        sequence > 0 &&
        at >= 0 &&
        position.isFinite() &&
        position in 0.0..86_400.0 &&
        speed.isFinite() &&
        speed in 0.25..3.0 &&
        seekRevision >= 0 &&
        ping >= 0 &&
        name.length <= 32 &&
        target.length <= 64 &&
        command.length <= 16 &&
        (media == null || media.valid()) &&
        peers.size <= 8 &&
        acknowledgements.size <= 8 &&
        peers.all { (key, value) ->
            key.matches(Regex("[0-9a-f]{64}")) &&
                value.name.length <= 32 &&
                (value.media == null || value.media.valid())
        } &&
        acknowledgements.all { (key, value) -> key.matches(Regex("[0-9a-f]{64}")) && value >= 0 }
}

interface WatchTransport {
    val publicKey: String
    fun start(onMessage: (String, WatchMessage) -> Unit, onConnection: (Int) -> Unit)
    fun send(message: WatchMessage)
    fun close()
}

/** Minimum-RTT samples reduce the influence of relay queueing; all timestamps are monotonic. */
class WatchClock {
    private data class Sample(val rtt: Long, val offset: Long)
    private val samples = ArrayDeque<Sample>()
    val ready: Boolean get() = samples.isNotEmpty()
    val latency: Long? get() = samples.minOfOrNull { it.rtt }
    val offset: Long get() = samples.minByOrNull { it.rtt }?.offset ?: 0

    fun accept(sent: Long, received: Long, remote: Long): Boolean {
        val rtt = received - sent
        if (sent < 0 || remote < 0 || rtt !in 0..10_000) return false
        samples.addLast(Sample(rtt, remote - (sent + rtt / 2)))
        if (samples.size > 8) samples.removeFirst()
        return true
    }
}

data class WatchCorrection(val seek: Double? = null, val speed: Double)

object WatchSynchronizer {
    fun correct(position: Double, target: Double, speed: Double, paused: Boolean, canSeek: Boolean): WatchCorrection {
        val drift = target - position
        if (canSeek && abs(drift) > if (paused) 0.18 else 1.5) return WatchCorrection(target, speed)
        val factor = when {
            paused || abs(drift) < 0.25 -> 1.0
            drift > 0 -> 1.03
            else -> 0.97
        }
        return WatchCorrection(speed = speed * factor)
    }
}
