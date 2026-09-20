package eu.kanade.tachiyomi.data.watch

import kotlinx.serialization.Serializable

/** Optional host-authenticated presentation metadata; never an executable playback command. */
@Serializable
data class WatchActivity(
    val id: Long,
    val at: Long,
    val actorId: String,
    val name: String,
    val command: String,
    val mediaKey: String,
    val value: Double = 0.0,
) {
    fun valid(): Boolean = id > 0 &&
        at >= 0 &&
        actorId.matches(Regex("[0-9a-f]{64}")) &&
        name.length in 1..32 &&
        command in listOf("play", "pause", "seek", "speed") &&
        mediaKey.length in 1..2200 &&
        value.isFinite() &&
        value in 0.0..86_400.0

    val label: String get() = when (command) {
        "pause" -> "$name ha messo in pausa"
        "play" -> "$name ha premuto Play"
        "seek" -> "$name è andato a ${watchPositionLabel(value)}"
        "speed" -> "$name ha cambiato la velocità"
        else -> ""
    }
}

private fun watchPositionLabel(value: Double): String {
    val seconds = value.toInt().coerceAtLeast(0)
    val minutes = seconds / 60
    return if (minutes >= 60) {
        "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
    } else {
        "$minutes:${(seconds % 60).toString().padStart(2, '0')}"
    }
}

enum class WatchRecovery { None, Command, Connection, Details }

val WatchRoomState.showPreparationFeedback: Boolean get() = preparingPlayback ||
    active &&
    phase in listOf(WatchPhase.Connecting, WatchPhase.Reconnecting)

val WatchRoomState.recovery: WatchRecovery get() = when {
    !active -> WatchRecovery.None
    commandFailed && (!sharedControls && !host || localHold) -> WatchRecovery.Details
    commandFailed -> WatchRecovery.Command
    waitingSeconds >= 12 && (relayCount == 0 || phase == WatchPhase.Reconnecting) -> WatchRecovery.Connection
    waitingSeconds >= 15 && (phase == WatchPhase.DifferentVideo || preparingPlayback) -> WatchRecovery.Details
    else -> WatchRecovery.None
}

val WatchRoomState.waitingFor: WatchMember? get() = members.firstOrNull {
    it.id != localMemberId && (!it.ready || it.buffering || it.problem != WatchProblem.None)
}.takeIf { waitForEveryone }

fun WatchRoomState.preparationCaption(localLoading: Boolean = false): String = when {
    commandFailed && recovery == WatchRecovery.Details -> "La stanza è in pausa"
    commandFailed -> "Conferma non ricevuta. Tocca per riprovare"
    recovery == WatchRecovery.Connection -> "Connessione interrotta. Tocca per riprovare"
    phase == WatchPhase.Reconnecting || relayCount == 0 -> "Ritroviamo il collegamento…"
    localHold -> "In pausa su questo telefono"
    localLoading -> "Prepariamo il video…"
    waitingFor != null -> {
        val friend = waitingFor!!
        when (friend.problem) {
            WatchProblem.Connection -> "Aspettiamo ${friend.name}: si sta ricollegando"
            WatchProblem.MissingSource, WatchProblem.SourceError, WatchProblem.DifferentEdition ->
                "${friend.name} deve controllare il video"
            WatchProblem.LocalPause -> "${friend.name} è in pausa"
            else -> "Aspettiamo ${friend.name}…"
        }
    }
    phase == WatchPhase.DifferentVideo -> if (recovery == WatchRecovery.Details) {
        "Controlliamo l'episodio"
    } else {
        "Prepariamo l'episodio della stanza…"
    }
    preparingPlayback && members.size <= 1 -> "In attesa del tuo amico…"
    else -> "Ci siamo quasi…"
}
