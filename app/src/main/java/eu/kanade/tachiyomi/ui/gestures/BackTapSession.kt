package eu.kanade.tachiyomi.ui.gestures

/** Main-thread delivery gate: a queued gesture cannot escape its foreground owner. */
internal class BackTapSession {
    private var generation = 0L
    private var lastAccepted = Long.MIN_VALUE
    fun renew(): Long = ++generation
    fun accept(token: Long, gestureTime: Long, now: Long): Boolean {
        if (token != generation || now - gestureTime !in 0..250_000_000L) return false
        if (lastAccepted != Long.MIN_VALUE && now - lastAccepted < 1_000_000_000L) return false
        lastAccepted = now
        return true
    }
}
