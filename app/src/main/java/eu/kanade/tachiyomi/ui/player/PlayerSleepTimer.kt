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
    private var deadline: Long? = null
    private val remaining = MutableStateFlow(0)
    val remainingTime = remaining.asStateFlow()

    fun start(seconds: Int) {
        startMillis(seconds.coerceAtLeast(0) * 1000L)
    }

    /** Extend the actual deadline, without rounding seconds or reviving an expired timer. */
    fun extend(seconds: Int) {
        val millisLeft = (deadline ?: return) - nowMillis()
        if (millisLeft <= 0L || seconds <= 0) return
        startMillis((millisLeft + seconds * 1000L).coerceAtMost(Int.MAX_VALUE * 1000L))
    }

    private fun startMillis(duration: Long) {
        job?.cancel()
        deadline = null
        remaining.value = ((duration + 999L) / 1000L).toInt()
        if (duration == 0L) return
        val target = nowMillis() + duration
        deadline = target
        job = scope.launch {
            while (true) {
                val millisLeft = target - nowMillis()
                remaining.value = ((millisLeft.coerceAtLeast(0L) + 999L) / 1000L).toInt()
                if (millisLeft <= 0L) break
                delay(minOf(millisLeft, 1000L))
            }
            deadline = null
            onExpired()
        }
    }
}
