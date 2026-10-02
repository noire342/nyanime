package eu.kanade.tachiyomi.ui.gestures

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Original, orientation-independent impulse detector. All timestamps use the sensor's monotonic clock.
 * The screen-normal axis remains Z in portrait and landscape; gravity is removed before classification.
 * No sensor samples are persisted or transmitted. Call only from the sensor worker.
 */
class BackTapDetector(private val threshold: Double = 2.4) {
    data class Gesture(val timestampNanos: Long, val strength: Double)

    private val gravity = DoubleArray(3)
    private var lastSample = 0L
    private var gyroTime = 0L
    private var gyroMagnitude = 0.0
    private var blockedUntil = 0L
    private var noise = 0.12
    private var pulse: Pulse? = null
    private var first: Pulse? = null
    private var pair: Gesture? = null
    private var quietSince = 0L

    private data class Pulse(
        val start: Long,
        var peakTime: Long,
        var z: Double,
        var lateral: Double,
        var quietSince: Long = 0,
    )

    fun reset(now: Long, quietMillis: Long = 250) {
        lastSample = 0
        gyroTime = 0
        gyroMagnitude = 0.0
        noise = 0.12
        suppress(now, quietMillis)
    }

    fun suppress(now: Long, millis: Long) {
        blockedUntil = maxOf(blockedUntil, now + millis * MS)
        pulse = null
        first = null
        pair = null
        quietSince = 0
    }

    fun gyroscope(time: Long, x: Double, y: Double, z: Double) {
        if (time <= gyroTime || !x.isFinite() || !y.isFinite() || !z.isFinite()) return
        gyroTime = time
        gyroMagnitude = sqrt(x * x + y * y + z * z)
        // Turning, walking and putting the phone down must not combine with a previous tap.
        if (gyroMagnitude > 1.35) suppress(time, 350)
    }

    fun accelerometer(time: Long, x: Double, y: Double, z: Double): Gesture? {
        if (time <= lastSample || !x.isFinite() || !y.isFinite() || !z.isFinite()) return null
        val values = doubleArrayOf(x, y, z)
        val gap = time - lastSample
        if (lastSample == 0L || gap > 80 * MS) {
            values.copyInto(gravity)
            lastSample = time
            suppress(time, 250)
            return null
        }
        lastSample = time
        val alpha = exp(-gap.toDouble() / (180 * MS))
        val linear = DoubleArray(3) { i ->
            gravity[i] = alpha * gravity[i] + (1 - alpha) * values[i]
            values[i] - gravity[i]
        }
        val normal = abs(linear[2])
        val lateral = sqrt(linear[0] * linear[0] + linear[1] * linear[1])
        val limit = maxOf(threshold, noise * 6.0)
        if (normal < limit * 0.45 && lateral < limit * 0.45) {
            noise += (normal - noise) * 0.025
        }
        // The two sensor streams are not phase-locked: the last gyro sample may be slightly newer.
        if (time < blockedUntil || time - gyroTime !in -30 * MS..80 * MS || gyroMagnitude > 1.35) return null
        if (lateral > maxOf(5.0, limit * 2.0)) {
            suppress(time, 350)
            return null
        }

        pair?.let { pending ->
            if (normal < limit * 0.45 && lateral < limit) {
                if (quietSince == 0L) quietSince = time
                if (time - quietSince >= 35 * MS) {
                    suppress(time, 1000)
                    return pending
                }
            } else {
                // A third impulse/continuous vibration is not a double tap.
                suppress(time, 350)
            }
            return null
        }

        val active = pulse
        if (active == null) {
            if (normal >= limit && normal > lateral * 1.4) {
                pulse = Pulse(time, time, linear[2], lateral)
            }
            if (first?.let { time - it.peakTime > 500 * MS } == true) first = null
            return null
        }
        active.lateral = maxOf(active.lateral, lateral)
        if (normal > abs(active.z)) {
            active.z = linear[2]
            active.peakTime = time
        }
        if (time - active.start > 90 * MS) {
            suppress(time, 350)
            return null
        }
        if (normal < limit * 0.4) {
            if (active.quietSince == 0L) active.quietSince = time
            if (time - active.quietSince >= 20 * MS) {
                pulse = null
                if (active.quietSince - active.start < 6 * MS ||
                    active.lateral > abs(active.z) * 0.7 ||
                    abs(active.z) > 35
                ) {
                    first = null
                    return null
                }
                val previous = first
                val interval = previous?.let { active.peakTime - it.peakTime } ?: 0
                if (previous != null &&
                    interval in 120 * MS..500 * MS &&
                    previous.z * active.z > 0 &&
                    abs(active.z / previous.z) in 0.35..2.85
                ) {
                    pair = Gesture(active.peakTime, minOf(abs(previous.z), abs(active.z)))
                    quietSince = time
                    first = null
                } else {
                    first = if (previous != null && interval < 120 * MS) null else active
                }
            }
        } else {
            active.quietSince = 0
        }
        return null
    }

    companion object {
        private const val MS = 1_000_000L

        /** Three successful pairs, robust to one unusually strong gesture. */
        fun calibratedThreshold(strengths: List<Double>): Double? {
            if (strengths.size != 3 || strengths.any { !it.isFinite() || it < 1.0 }) return null
            return (strengths.sorted()[1] * 0.45).coerceIn(1.2, 8.0)
        }
    }
}
