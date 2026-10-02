package eu.kanade.tachiyomi.ui.gestures

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sin

class BackTapDetectorTest {
    private fun trace(
        gravity: DoubleArray = doubleArrayOf(0.0, 9.81, 0.0),
        impulse: (Int) -> DoubleArray,
        rotation: (Int) -> Double = { 0.0 },
        engine: BackTapDetector = BackTapDetector(),
        start: Int = 1000,
        end: Int = 2400,
    ): List<BackTapDetector.Gesture> = buildList {
        for (ms in start..end step 10) {
            val time = ms * 1_000_000L
            engine.gyroscope(time, rotation(ms), 0.0, 0.0)
            val v = impulse(ms)
            engine.accelerometer(time, gravity[0] + v[0], gravity[1] + v[1], gravity[2] + v[2])?.let(::add)
        }
    }

    private fun tap(ms: Int, at: Int, strength: Double = 6.0): Double = when (ms - at) {
        0 -> strength
        10 -> strength * 0.65
        20 -> -strength * 0.3
        30 -> -strength * 0.12
        else -> 0.0
    }

    private fun pair(ms: Int, second: Int = 1700) = doubleArrayOf(0.05, 0.05, tap(ms, 1400) + tap(ms, second))

    @Test fun portraitLandscapeAndFlatUseTheSameScreenNormalAxis() {
        for (gravity in listOf(
            doubleArrayOf(0.0, 9.81, 0.0),
            doubleArrayOf(9.81, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 9.81),
        )) {
            val gestures = trace(gravity, { pair(it) })
            assertEquals(1, gestures.size)
            assertTrue(gestures.single().timestampNanos >= 1_700_000_000L)
        }
    }

    @Test fun singleTapAndSeparatedTapsDoNothing() {
        assertTrue(trace(impulse = { doubleArrayOf(0.0, 0.0, tap(it, 1400)) }).isEmpty())
        assertTrue(trace(impulse = { pair(it, 2000) }).isEmpty())
    }

    @Test fun continuousSpeakerVibrationAndShakingDoNotBecomeGestures() {
        assertTrue(trace(impulse = { doubleArrayOf(0.0, 0.0, 0.4 * sin(it.toDouble())) }).isEmpty())
        assertTrue(
            trace(impulse = {
                doubleArrayOf(7.0 * sin(it / 35.0), 5.0 * sin(it / 45.0), 6.0 * sin(it / 40.0))
            }).isEmpty(),
        )
        assertTrue(trace(impulse = { doubleArrayOf(0.0, 0.0, if (it in 1400..1900) 5.0 else 0.0) }).isEmpty())
    }

    @Test fun turningThePhoneAndSettingItDownInvalidatesThePair() {
        assertTrue(trace(impulse = { pair(it) }, rotation = { if (it in 1430..1750) 2.0 else 0.0 }).isEmpty())
        assertTrue(trace(impulse = { doubleArrayOf(0.0, 0.0, tap(it, 1400, 42.0) + tap(it, 1700)) }).isEmpty())
    }

    @Test fun rapidRingingAndThirdImpulseAreRejected() {
        assertTrue(trace(impulse = { pair(it, 1470) }).isEmpty())
        assertTrue(trace(impulse = { pair(it).also { v -> v[2] += tap(it, 1770) } }).isEmpty())
    }

    @Test fun touchAndMissingGyroscopeCannotTriggerAnAction() {
        val engine = BackTapDetector()
        val detected = mutableListOf<BackTapDetector.Gesture>()
        for (ms in 1000..2400 step 10) {
            val time = ms * 1_000_000L
            if (ms == 1650) engine.suppress(time, 350)
            engine.gyroscope(time, 0.0, 0.0, 0.0)
            engine.accelerometer(time, 0.0, 9.81, pair(ms)[2])?.let(detected::add)
        }
        assertTrue(detected.isEmpty())
        val noGyro = BackTapDetector()
        for (ms in 1000..2400 step 10) {
            assertNull(noGyro.accelerometer(ms * 1_000_000L, 0.0, 9.81, pair(ms)[2]))
        }
    }

    @Test fun cooldownDeduplicatesVibrationButAllowsTheNextIntentionalPair() {
        val gestures = trace(
            impulse = {
                doubleArrayOf(
                    0.0,
                    0.0,
                    tap(it, 1400) + tap(it, 1700) + tap(it, 1950) + tap(it, 2200) + tap(it, 3000) + tap(it, 3300),
                )
            },
            end = 3600,
        )
        assertEquals(2, gestures.size)
    }

    @Test fun invalidAndOutOfOrderSamplesAreIgnored() {
        val engine = BackTapDetector()
        engine.gyroscope(1_000_000_000, 0.0, 0.0, 0.0)
        assertNull(engine.accelerometer(1_000_000_000, 0.0, 9.81, 0.0))
        assertNull(engine.accelerometer(1_000_000_000, 0.0, 9.81, 30.0))
        assertNull(engine.accelerometer(990_000_000, 0.0, 9.81, 30.0))
        assertNull(engine.accelerometer(1_010_000_000, Double.NaN, 0.0, 0.0))
    }

    @Test fun independentGyroTimestampsDoNotSuppressValidTapsAndResponseStaysBelow200Millis() {
        for (offset in listOf(-15, 0, 15)) {
            val engine = BackTapDetector()
            val deliveryTimes = mutableListOf<Int>()
            for (ms in 1000..2300 step 10) {
                engine.gyroscope((ms + offset) * 1_000_000L, 0.0, 0.0, 0.0)
                if (engine.accelerometer(ms * 1_000_000L, 0.0, 9.81, pair(ms)[2]) != null) {
                    deliveryTimes.add(ms)
                }
            }
            assertEquals(1, deliveryTimes.size)
            assertTrue(deliveryTimes.single() - 1700 in 0..200)
        }
    }

    @Test fun slowerSensorStreamsAndDifferentTapStrengthsKeepWorking() {
        for (interval in listOf(10, 20)) {
            for (strength in listOf(3.0, 6.0, 12.0)) {
                val engine = BackTapDetector()
                var detected = 0
                for (ms in 1000..2400 step interval) {
                    val time = ms * 1_000_000L
                    engine.gyroscope(time, 0.0, 0.0, 0.0)
                    if (engine.accelerometer(time, 0.0, 9.81, tap(ms, 1400, strength) + tap(ms, 1700, strength)) !=
                        null
                    ) {
                        detected++
                    }
                }
                assertEquals(1, detected, "interval=$interval, strength=$strength")
            }
        }
    }

    @Test fun calibrationUsesTheMedianAndRejectsInvalidInputs() {
        assertEquals(2.7, BackTapDetector.calibratedThreshold(listOf(5.0, 6.0, 30.0)))
        assertNull(BackTapDetector.calibratedThreshold(listOf(6.0, 6.0)))
        assertNull(BackTapDetector.calibratedThreshold(listOf(6.0, Double.NaN, 6.0)))
        assertNotNull(BackTapDetector.calibratedThreshold(listOf(4.0, 5.0, 6.0)))
        assertEquals(0.09, checkNotNull(BackTapDetector.calibratedThreshold(listOf(0.18, 0.2, 0.25))), 0.00001)
        assertNull(BackTapDetector.calibratedThreshold(listOf(0.0, 0.2, 0.25)))
    }

    private fun sampledTrace(
        periodMicros: Int,
        threshold: Double,
        impulse: (Int) -> DoubleArray,
    ): List<BackTapDetector.Gesture> = buildList {
        val engine = BackTapDetector(threshold)
        engine.reset(1_000_000_000L, 1000)
        for (micros in 1_000_000..3_200_000 step periodMicros) {
            val time = micros * 1000L
            engine.gyroscope(time, 0.0, 0.0, 0.0)
            val v = impulse(micros)
            engine.accelerometer(time, v[0], 9.81 + v[1], v[2])?.let(::add)
        }
    }

    @Test fun gentlePairsCanTeachCalibrationAndWorkWithTheLearnedThreshold() {
        for (period in listOf(10_000, 5000, 2500)) {
            val impulse: (Int) -> DoubleArray = {
                doubleArrayOf(0.0, 0.0, tap(it / 1000, 2400, 0.2) + tap(it / 1000, 2700, 0.2))
            }
            assertTrue(sampledTrace(period, 2.4, impulse).isEmpty())
            val gestures = sampledTrace(period, BackTapDetector.CALIBRATION_THRESHOLD, impulse)
            assertEquals(1, gestures.size, "calibration period=$period")
            val threshold = checkNotNull(BackTapDetector.calibratedThreshold(List(3) { gestures.single().strength }))
            assertEquals(1, sampledTrace(period, threshold, impulse).size, "learned period=$period")
        }
    }

    @Test fun fastSamplingCapturesBriefImpulsesBetweenTheOldSamplingInstants() {
        val impulse: (Int) -> DoubleArray = { micros ->
            fun shortTap(at: Int) = when (micros - at) {
                in 0..2499 -> 0.22
                in 2500..4999 -> -0.11
                else -> 0.0
            }
            doubleArrayOf(0.0, 0.0, shortTap(2_403_000) + shortTap(2_703_000))
        }
        assertTrue(sampledTrace(10_000, BackTapDetector.CALIBRATION_THRESHOLD, impulse).isEmpty())
        for (period in listOf(5000, 2500)) {
            assertEquals(1, sampledTrace(period, BackTapDetector.CALIBRATION_THRESHOLD, impulse).size)
        }
    }

    @Test fun learnedGentleThresholdStillRejectsNoiseVibrationAndShakingAtEveryRate() {
        for (period in listOf(10_000, 5000, 2500)) {
            for ((name, impulse) in listOf<Pair<String, (Int) -> DoubleArray>>(
                "noise" to { doubleArrayOf(0.01 * sin(it / 7000.0), 0.02 * sin(it / 9000.0), 0.03 * sin(it / 8000.0)) },
                "vibration" to { doubleArrayOf(0.0, 0.0, 0.4 * sin(it / 3000.0)) },
                "shaking" to
                    { doubleArrayOf(7.0 * sin(it / 35_000.0), 5.0 * sin(it / 45_000.0), 6.0 * sin(it / 40_000.0)) },
                "single impact" to { doubleArrayOf(0.0, 0.0, tap(it / 1000, 2400, 0.2)) },
            )) {
                assertTrue(sampledTrace(period, 0.09, impulse).isEmpty(), "$name at period=$period")
            }
        }
    }

    @Test fun shortRockingDuringATapDoesNotLookLikeSustainedTurning() {
        val gestures = trace(
            impulse = { pair(it) },
            rotation = { if (it in 1410..1450 || it in 1710..1750) 2.8 else 0.0 },
        )
        assertEquals(1, gestures.size)
    }

    @Test fun firmTapsAreNotRejectedByTheirAmplitudeOrFilteredGyroTail() {
        for (strength in listOf(24.0, 42.0, 60.0)) {
            val gestures = trace(
                impulse = { doubleArrayOf(0.1, 0.2, tap(it, 1400, strength) + tap(it, 1700, strength)) },
                rotation = { if (it in 1410..1470 || it in 1710..1770) 6.0 else 0.0 },
            )
            assertEquals(1, gestures.size, "strength=$strength")
        }
    }

    @Test fun briefLateralRecoilAfterANormalImpulseDoesNotCancelTheTap() {
        val gestures = trace(impulse = {
            val recoil = if (it in 1430..1440 || it in 1730..1740) 8.0 else 0.0
            doubleArrayOf(recoil, 0.1, tap(it, 1400, 12.0) + tap(it, 1700, 12.0))
        })
        assertEquals(1, gestures.size)
        // Sideways impacts with no screen-normal dominance remain invalid.
        assertTrue(
            trace(impulse = {
                doubleArrayOf(tap(it, 1400, 18.0) + tap(it, 1700, 18.0), 0.0, tap(it, 1400) + tap(it, 1700))
            }).isEmpty(),
        )
    }

    @Test fun bipolarRecoilBelongsToItsTapAndDoesNotCancelThePair() {
        val gestures = trace(impulse = {
            val first = tap(it, 1400) - tap(it, 1460, 3.5)
            val second = -tap(it, 1690, 2.8) + tap(it, 1730, 6.0) - tap(it, 1780, 3.0)
            doubleArrayOf(0.1, 0.2, first + second)
        })
        assertEquals(1, gestures.size)
        assertTrue(gestures.single().timestampNanos >= 1_730_000_000L)
    }

    @Test fun handheldRecordingsWorkDuringCalibrationAndNormalUse() {
        // Relative timestamps and sensor values only: no device identity, wall time or app data.
        val recordings = checkNotNull(javaClass.getResourceAsStream("/gestures/back-tap-handheld.csv"))
            .bufferedReader().useLines { lines ->
                lines.filter { !it.startsWith("#") && it.isNotBlank() }.map { it.split(',') }.toList()
            }.groupBy { it[0] }
        assertEquals(4, recordings.size)
        for ((name, samples) in recordings) {
            for (threshold in listOf(1.4, 2.4)) {
                val detector = BackTapDetector(threshold)
                var detections = 0
                for (sample in samples) {
                    val time = sample[2].toLong()
                    val values = sample.drop(3).map(String::toDouble)
                    if (sample[1] == "4") {
                        detector.gyroscope(time, values[0], values[1], values[2])
                    } else if (detector.accelerometer(time, values[0], values[1], values[2]) != null) {
                        detections++
                    }
                }
                assertEquals(1, detections, "recording=$name, threshold=$threshold")
            }
        }
    }
}
