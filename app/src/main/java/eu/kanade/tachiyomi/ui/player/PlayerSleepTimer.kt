package eu.kanade.tachiyomi.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A playback-session timer measured against a monotonic clock, including time spent paused. */
internal class PlayerSleepTimer(
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long,
    private val onExpired: () -> Unit,
) {
    private var job: Job? = null
    private val remaining = MutableStateFlow(0)
    val remainingTime = remaining.asStateFlow()

    fun start(seconds: Int) {
        job?.cancel()
        val duration = seconds.coerceAtLeast(0)
        remaining.value = duration
        if (duration == 0) return
        val deadline = nowMillis() + duration * 1000L
        job = scope.launch {
            while (true) {
                val millisLeft = deadline - nowMillis()
                remaining.value = ((millisLeft.coerceAtLeast(0L) + 999L) / 1000L).toInt()
                if (millisLeft <= 0L) break
                delay(minOf(millisLeft, 1000L))
            }
            onExpired()
        }
    }
}
