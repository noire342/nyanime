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
    private val rotation = DoubleArray(3)
    private var rotatingSince = 0L
    private var blockedUntil = 0L
    private var noise = 0.12
    private var pulse: Pulse? = null
    private var first: Pulse? = null
    private var pair: PendingPair? = null
    private var quietSince = 0L
    private var previousNormal = 0.0

    private data class Pulse(
        val start: Long,
        var peakTime: Long,
        var z: Double,
        var lateral: Double,
        var returned: Boolean = false,
    )

    private data class PendingPair(val gesture: Gesture, val second: Pulse)

    fun reset(now: Long, quietMillis: Long = 250) {
        lastSample = 0
        gyroTime = 0
        rotation.fill(0.0)
        rotatingSince = 0L
        previousNormal = 0.0
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
        val gap = time - gyroTime
        val alpha = if (gyroTime == 0L || gap > 80 * MS) 0.0 else exp(-gap.toDouble() / (80 * MS))
        gyroTime = time
        rotation[0] = alpha * rotation[0] + (1 - alpha) * x
        rotation[1] = alpha * rotation[1] + (1 - alpha) * y
        rotation[2] = alpha * rotation[2] + (1 - alpha) * z
        // A tap rocks a handheld phone. Reject sustained turning, not each short gyro excursion.
        val magnitude = sqrt(rotation.sumOf { it * it })
        val instantaneous = sqrt(x * x + y * y + z * z)
        // The low-pass tail of a strong tap can last >100 ms after the phone has stopped turning.
        // Both signals must still indicate rotation throughout the rejection window.
        if (magnitude > 1.35 && instantaneous > 1.35) {
            if (rotatingSince == 0L) rotatingSince = time
            if (time - rotatingSince >= 100 * MS) suppress(time, 250)
        } else {
            rotatingSince = 0L
        }
    }

    fun accelerometer(time: Long, x: Double, y: Double, z: Double): Gesture? {
        if (time <= lastSample || !x.isFinite() || !y.isFinite() || !z.isFinite()) return null
        val values = doubleArrayOf(x, y, z)
        val gap = time - lastSample
        if (lastSample == 0L || gap > 80 * MS) {
            values.copyInto(gravity)
            lastSample = time
            previousNormal = 0.0
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
        val risePerSecond = (normal - previousNormal) * 1_000_000_000 / gap
        previousNormal = normal
        if (normal < limit * 0.45 && lateral < limit * 0.45) {
            noise += (normal - noise) * 0.025
        }
        // The two sensor streams are not phase-locked: the last gyro sample may be slightly newer.
        if (time < blockedUntil || time - gyroTime !in -30 * MS..80 * MS) return null
        // Classify the direction at the impulse peak. A hand can recoil sideways afterwards.
        if (pulse == null && pair == null && lateral > maxOf(5.0, limit * 2.0) && lateral > normal) {
            suppress(time, 250)
            return null
        }

        pair?.let { pending ->
            val second = pending.second
            if (isThirdImpulse(second, time, linear[2], risePerSecond, limit)) {
                suppress(time, 350)
                return null
            }
            if (normal < maxOf(limit * 0.6, abs(second.z) * 0.5)) {
                if (quietSince == 0L) quietSince = time
            } else {
                quietSince = 0
            }
            // Give the whole impulse time to finish, including opposite-polarity recoil.
            if (time - second.peakTime >= 110 * MS && quietSince != 0L && time - quietSince >= 20 * MS) {
                suppress(time, 1000)
                return pending.gesture
            }
            if (time - second.peakTime > 200 * MS) suppress(time, 250)
            return null
        }

        val active = pulse
        if (active == null) {
            if (first?.let { time - it.peakTime > 550 * MS } == true) first = null
            // Recoil is part of the first impulse; it cannot become the second tap.
            if (first?.let { time - it.peakTime < 120 * MS } == true) return null
            if (normal >= limit && normal > lateral * 1.4 && risePerSecond >= limit * 12) {
                pulse = Pulse(time, time, linear[2], lateral)
            }
            return null
        }
        if (isThirdImpulse(active, time, linear[2], risePerSecond, limit)) {
            suppress(time, 350)
            return null
        }
        if (normal > abs(active.z)) {
            active.z = linear[2]
            active.peakTime = time
            active.lateral = lateral
        }
        if (normal < abs(active.z) * 0.4) active.returned = true
        if (time - active.start > 125 * MS) {
            suppress(time, 200)
            return null
        }
        if (time - active.start >= 80 * MS && normal < abs(active.z) * 0.5) {
            pulse = null
            // A hard ceiling on amplitude rejects firm taps (and sensor saturation). Single
            // impacts still need a second, comparable impulse to form a gesture.
            if (active.lateral > abs(active.z) * 0.9) {
                first = null
                return null
            }
            val previous = first
            val interval = previous?.let { active.peakTime - it.peakTime } ?: 0
            if (previous != null && interval in 120 * MS..550 * MS && abs(active.z / previous.z) in 0.2..4.0) {
                pair = PendingPair(Gesture(active.peakTime, minOf(abs(previous.z), abs(active.z))), active)
                quietSince = 0
                first = null
            } else {
                first = active
            }
        }
        return null
    }

    private fun isThirdImpulse(active: Pulse, time: Long, z: Double, rise: Double, limit: Double) =
        active.returned &&
            time - active.peakTime >= 60 * MS &&
            z * active.z > 0 &&
            abs(z) > abs(active.z) * 0.8 &&
            rise > limit * 15

    companion object {
        private const val MS = 1_000_000L

        /** Three successful pairs, robust to one unusually strong gesture. */
        fun calibratedThreshold(strengths: List<Double>): Double? {
            if (strengths.size != 3 || strengths.any { !it.isFinite() || it < 1.0 }) return null
            return (strengths.sorted()[1] * 0.45).coerceIn(1.2, 8.0)
        }
    }
}
