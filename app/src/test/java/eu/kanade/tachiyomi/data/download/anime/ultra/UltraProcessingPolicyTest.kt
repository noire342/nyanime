package eu.kanade.tachiyomi.data.download.anime.ultra

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UltraProcessingPolicyTest {
    private val cool = UltraProcessingPolicy.Environment(
        interactive = false, screenOffOnly = true, playerActive = false, charging = true, chargingOnly = true,
        batteryPercent = 85, batteryCelsius = 32f, thermalStatus = 0, thermalHeadroom = 0.2f, powerSave = false,
    )

    @Test
    fun `moderate pressure pauses before severe and resumes only after cooling`() {
        val policy = UltraProcessingPolicy()
        assertEquals(UltraProcessingPolicy.COOLING, policy.waitingReason(cool.copy(thermalStatus = 2)))
        assertEquals(UltraProcessingPolicy.COOLING, policy.waitingReason(cool.copy(batteryCelsius = 37.5f)))
        assertEquals(UltraProcessingPolicy.COOLING, policy.waitingReason(cool.copy(thermalHeadroom = 0.7f)))
        assertNull(policy.waitingReason(cool))
    }

    @Test
    fun `resuming a warm suspended job does not bypass cooling hysteresis`() {
        val policy = UltraProcessingPolicy(cooling = true)
        assertEquals(UltraProcessingPolicy.COOLING, policy.waitingReason(cool.copy(batteryCelsius = 38f)))
        assertNull(policy.waitingReason(cool.copy(batteryCelsius = 36.5f)))
    }

    @Test
    fun `low memory defers allocation and releases admission after recovery`() {
        val policy = UltraProcessingPolicy()
        assertEquals(UltraProcessingPolicy.MEMORY, policy.waitingReason(cool.copy(lowMemory = true)))
        assertNull(policy.waitingReason(cool))
    }

    @Test
    fun `battery sensor and thermal forecast independently stop an export`() {
        assertEquals(
            UltraProcessingPolicy.COOLING,
            UltraProcessingPolicy().waitingReason(cool.copy(batteryCelsius = 39f)),
        )
        assertEquals(
            UltraProcessingPolicy.COOLING,
            UltraProcessingPolicy().waitingReason(cool.copy(thermalHeadroom = 0.8f)),
        )
        assertNull(UltraProcessingPolicy().waitingReason(cool.copy(batteryCelsius = null, thermalHeadroom = Float.NaN)))
    }

    @Test
    fun `player always has priority including when screen on processing is allowed`() {
        assertEquals(
            UltraProcessingPolicy.PLAYER,
            UltraProcessingPolicy().waitingReason(cool.copy(playerActive = true, screenOffOnly = false)),
        )
        assertEquals(UltraProcessingPolicy.SCREEN, UltraProcessingPolicy().waitingReason(cool.copy(interactive = true)))
        assertNull(UltraProcessingPolicy().waitingReason(cool.copy(interactive = true, screenOffOnly = false)))
    }

    @Test
    fun `unplugging and energy saving suspend active work`() {
        assertEquals(UltraProcessingPolicy.CHARGING, UltraProcessingPolicy().waitingReason(cool.copy(charging = false)))
        assertEquals(
            UltraProcessingPolicy.BATTERY,
            UltraProcessingPolicy().waitingReason(
                cool.copy(charging = false, chargingOnly = false, batteryPercent = 29),
            ),
        )
        assertEquals(UltraProcessingPolicy.BATTERY, UltraProcessingPolicy().waitingReason(cool.copy(powerSave = true)))
    }

    @Test
    fun `pacing budgets actual completed GPU work without overflow`() {
        assertEquals(40_000_000L, UltraProcessingPolicy.restNanos(10_000_000, false))
        assertEquals(90_000_000L, UltraProcessingPolicy.restNanos(10_000_000, true))
        assertEquals(0L, UltraProcessingPolicy.restNanos(-1, false))
        assertTrue(UltraProcessingPolicy.restNanos(Long.MAX_VALUE, true) > 0)
        for (width in listOf(320, 720, 1920, 3840)) {
            val height = UltraProcessingPolicy.stripHeight(width)
            assertTrue(height * width <= 262_144)
            val rows = (0 until 2161 step height).flatMap { y -> (y until minOf(2161, y + height)).toList() }
            assertEquals((0 until 2161).toList(), rows)
        }
    }
}
