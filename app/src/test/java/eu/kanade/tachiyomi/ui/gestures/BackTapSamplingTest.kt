package eu.kanade.tachiyomi.ui.gestures

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackTapSamplingTest {
    @Test fun samplingUsesTheSlowerSensorWithoutRequestingAnUnsupportedRate() {
        assertEquals(2500, BackTapSampling.periodMicros(2404, 2404))
        assertEquals(10_000, BackTapSampling.periodMicros(5000, 10_000))
        assertEquals(2500, BackTapSampling.periodMicros(0, 0))
    }

    @Test fun fallbackNeverRequestsMoreThan200Hz() {
        assertEquals(5000, BackTapSampling.periodMicros(2404, 2404, highRate = false))
        assertEquals(10_000, BackTapSampling.periodMicros(5000, 10_000, highRate = false))
    }
}
