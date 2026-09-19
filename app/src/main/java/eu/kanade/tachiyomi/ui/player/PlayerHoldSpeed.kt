package eu.kanade.tachiyomi.ui.player

/** A temporary local speed override, owned by one press and never persisted. */
internal class PlayerHoldSpeed(
    private val available: () -> Boolean,
    private val readSpeed: () -> Double,
    private val writeSpeed: (Double) -> Unit,
) {
    var originalSpeed: Double? = null
        private set

    fun start(): Boolean {
        if (originalSpeed != null || !available()) return false
        val speed = readSpeed()
        if (!speed.isFinite() || speed <= 0.0) return false
        originalSpeed = speed
        writeSpeed(2.0)
        return true
    }

    /** Safe for release, cancellation and disposal, including after native teardown. */
    fun finish() {
        val speed = originalSpeed ?: return
        originalSpeed = null
        if (available()) writeSpeed(speed)
    }
}
