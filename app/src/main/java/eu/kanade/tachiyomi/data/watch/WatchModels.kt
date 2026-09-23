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
    val problem: WatchProblem = WatchProblem.None,
    val upcoming: WatchMedia? = null,
    val canAdvance: Boolean = true,
    val preparedNextKey: String? = null,
    val nextProblem: WatchProblem = WatchProblem.None,
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
    fun advance(media: WatchMedia) {}
}

enum class WatchPhase {
    Idle,
    Connecting,
    Waiting,
    Playing,
    Paused,
    Buffering,
    Starting,
    Reconnecting,
    DifferentVideo,
    Closed,
    Failed,
}

@Serializable
enum class WatchProblem {
    None,
    Opening,
    Buffering,
    MissingSource,
    SourceError,
    DifferentEdition,
    LocalPause,
    Connection,
}

data class WatchMember(
    val id: String,
    val name: String,
    val ready: Boolean,
    val buffering: Boolean,
    val problem: WatchProblem = WatchProblem.None,
    val nextProblem: WatchProblem = WatchProblem.None,
    val positionSeconds: Double? = null,
    val reading: Boolean = false,
)

@Serializable
data class WatchSkip(val id: Long, val label: String, val target: Double, val deadline: Long? = null)

@Serializable
data class WatchNext(val id: Long, val media: WatchMedia, val deadline: Long? = null)

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
    val pendingPlaybackPaused: Boolean? = null,
    val message: String = "",
    val resumeSeconds: Int? = null,
    val skip: WatchSkip? = null,
    val skipSeconds: Int? = null,
    val upcoming: WatchMedia? = null,
    val next: WatchNext? = null,
    val nextSeconds: Int? = null,
    val localMemberId: String = "",
    val waitingSeconds: Int = 0,
    val commandFailed: Boolean = false,
    val activity: WatchActivity? = null,
    val readingSupported: Boolean = false,
    val readingVersion: Int = 0,
) {
    val wantsPlayback: Boolean get() = pendingPlaybackPaused?.not() ?: playRequested
    val preparingPlayback: Boolean get() = active &&
        !localHold &&
        wantsPlayback &&
        phase != WatchPhase.Playing &&
        resumeSeconds == null
    val playbackPreparationMessage: String get() = when {
        pendingPlaybackPaused == false -> "Richiesta di riproduzione inviata…"
        phase in listOf(
            WatchPhase.Connecting,
            WatchPhase.Reconnecting,
            WatchPhase.Buffering,
            WatchPhase.DifferentVideo,
        ) ->
            message.ifBlank { "Preparazione della riproduzione…" }
        else -> "Verifica che tutti siano pronti…"
    }
}

@Serializable
enum class WatchMessageType { Hello, Status, Ping, Pong, Command, Timeline, Leave, Closed }

@Serializable
data class WatchPeerStatus(
    val name: String,
    val ready: Boolean,
    val buffering: Boolean,
    val media: WatchMedia? = null,
    val problem: WatchProblem = WatchProblem.None,
    val canAdvance: Boolean = true,
    val preparedNextKey: String? = null,
    val nextProblem: WatchProblem = WatchProblem.None,
    val reading: Boolean = false,
    val positionSeconds: Double? = null,
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
    val invitation: String = "",
    val sharedControls: Boolean = true,
    val waitForEveryone: Boolean = true,
    val peers: Map<String, WatchPeerStatus> = emptyMap(),
    val acknowledgements: Map<String, Long> = emptyMap(),
    val coordinationVersion: Int = 1,
    val resumeAt: Long? = null,
    val pausedBy: String = "",
    val skip: WatchSkip? = null,
    val upcoming: WatchMedia? = null,
    val next: WatchNext? = null,
    val cueId: Long = 0,
    val problem: WatchProblem = WatchProblem.None,
    val canAdvance: Boolean = true,
    val preparedNextKey: String? = null,
    val nextProblem: WatchProblem = WatchProblem.None,
    val activity: WatchActivity? = null,
    val readingMode: Boolean = false,
    val readingVersion: Int = 0,
    val reading: eu.kanade.tachiyomi.data.reading.ReadingEnvelope? = null,
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
        invitation.length <= 5000 &&
        coordinationVersion in 1..2 &&
        readingVersion in 0..2 &&
        (resumeAt == null || resumeAt >= 0) &&
        pausedBy.length <= 32 &&
        cueId >= 0 &&
        (preparedNextKey == null || preparedNextKey.length <= 2200) &&
        (upcoming == null || upcoming.valid()) &&
        (
            skip == null ||
                (
                    skip.id > 0 &&
                        skip.label.length in 1..100 &&
                        skip.target.isFinite() &&
                        skip.target in 0.0..86_400.0 &&
                        (skip.deadline == null || skip.deadline >= 0)
                    )
            ) &&
        (next == null || (next.id > 0 && next.media.valid() && (next.deadline == null || next.deadline >= 0))) &&
        (media == null || media.valid()) &&
        (activity == null || activity.valid()) &&
        (reading == null || reading.valid()) &&
        peers.size <= 8 &&
        acknowledgements.size <= 8 &&
        peers.all { (key, value) ->
            key.matches(Regex("[0-9a-f]{64}")) &&
                value.name.length <= 32 &&
                (
                    value.positionSeconds == null ||
                        (value.positionSeconds.isFinite() && value.positionSeconds in 0.0..86_400.0)
                    ) &&
                (value.preparedNextKey == null || value.preparedNextKey.length <= 2200) &&
                (value.media == null || value.media.valid())
        } &&
        acknowledgements.all { (key, value) -> key.matches(Regex("[0-9a-f]{64}")) && value >= 0 }
}

interface WatchTransport {
    val publicKey: String
    fun start(onMessage: (String, WatchMessage) -> Unit, onConnection: (Int) -> Unit)
    fun send(message: WatchMessage)
    fun retryUnavailable() {}
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
