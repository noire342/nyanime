package eu.kanade.tachiyomi.data.releases

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class ReleaseReminderPlanTest {
    private val at = 20 * ReleasePolicy.DAY
    private fun event(id: Long = 1, episode: Int = 3, catalog: Long = 42, time: Long = at) =
        AiringEvent(id, episode, time, catalog)

    private fun plan(
        vararg events: AiringEvent,
        delivered: Set<Pair<String, String>> = emptySet(),
        advance: Boolean = true,
    ) = ReleaseReminderPlan.pending(events.toList(), delivered, advance)

    @Test fun fallbackAloneSchedulesThePreviousDayAndTheBroadcast() {
        val reminders = plan(event())
        assertEquals(listOf(ReleaseReminderKind.ADVANCE, ReleaseReminderKind.AIRING), reminders.map { it.kind })
        assertEquals(listOf(at - ReleasePolicy.DAY, at), reminders.map { it.at })
    }

    @Test fun disablingAdvanceKeepsTheBroadcastReminder() {
        assertEquals(ReleaseReminderKind.AIRING, plan(event(), advance = false).single().kind)
    }

    @Test fun duplicateEditionsNotifyOnceAndWatchingEitherCanSuppressTheGroup() {
        val reminders = plan(event(id = 7), event(id = 2))
        assertEquals(2, reminders.size)
        assertEquals(listOf(2L, 7L), reminders.first().events.map { it.entryId })
        assertEquals(setOf("catalog:42:3", "entry:2:3", "entry:7:3"), reminders.first().keys)
    }

    @Test fun differentCataloguesAndUnverifiedEntriesNeverMerge() {
        assertEquals(
            8,
            plan(event(), event(id = 2, catalog = 43), event(id = 3, catalog = 0), event(id = 4, catalog = 0)).size,
        )
    }

    @Test fun advanceReceiptDoesNotConsumeBroadcastAndSurvivesRestartAndDateChanges() {
        val receipt = plan(event()).first().keys.map { it to ReleaseReminderKind.ADVANCE.name }.toSet()
        assertEquals(
            ReleaseReminderKind.AIRING,
            plan(event(time = at + ReleasePolicy.HOUR), delivered = receipt).single().kind,
        )
        assertEquals(ReleaseReminderKind.AIRING, plan(event(id = 2), delivered = receipt).single().kind)
    }

    @Test fun entryAliasProtectsAgainstGainingOrLosingACatalogueMapping() {
        val receipt = setOf("entry:1:3" to "ADVANCE")
        assertEquals(ReleaseReminderKind.AIRING, plan(event(catalog = 0), delivered = receipt).single().kind)
        assertEquals(ReleaseReminderKind.AIRING, plan(event(catalog = 42), delivered = receipt).single().kind)
    }

    @Test fun oldBroadcastAcknowledgementsDoNotProduceAnAdvanceAlertOnUpgrade() {
        assertTrue(plan(event().copy(remindedAt = 1)).isEmpty())
        assertTrue(plan(event(id = 2), event().copy(remindedAt = 1)).isEmpty())
    }

    @Test fun providerBroadcastWinsOverDuplicateFallbackAndSwitchingRetainsDelivery() {
        val preferred = event(id = 2, time = at + ReleasePolicy.HOUR).copy(
            variants = listOf(ScheduleBroadcast(3, at + ReleasePolicy.HOUR, ScheduleAirType.SUB)),
        )
        val reminders = plan(event(), preferred)
        assertEquals(preferred, reminders.first().event)
        assertEquals(preferred.airingAt - ReleasePolicy.DAY, reminders.first().at)
        val receipt = reminders.first().keys.map { it to "ADVANCE" }.toSet()
        assertEquals(ReleaseReminderKind.AIRING, plan(event(), delivered = receipt).single().kind)
    }

    @Test fun nextAlarmUsesReminderTimeInsteadOfEpisodeTimeOrInputOrder() {
        val reminders = plan(
            event(time = at + 10 * ReleasePolicy.HOUR),
            event(
                id = 2,
                catalog = 43,
                time =
                at - ReleasePolicy.HOUR,
            ),
        )
        assertEquals(2L, reminders.first().event.entryId)
        assertEquals(reminders.map { it.at }.sorted(), reminders.map { it.at })
    }

    @Test fun batteryDelayedAlarmsHaveABoundedRecoveryWindow() {
        val reminders = plan(event())
        val advance = reminders.first()
        val broadcast = reminders.last()
        assertFalse(advance.expired(advance.at + ReleasePolicy.HOUR))
        assertFalse(broadcast.expired(broadcast.at + ReleasePolicy.HOUR))
        assertTrue(advance.expired(advance.at + 6 * ReleasePolicy.HOUR + 1))
        assertTrue(broadcast.expired(broadcast.at + 2 * ReleasePolicy.HOUR + 1))
    }

    @Test fun missedAlertsDoNotFireAfterOfflinePeriodsAndPostponedDatesCanBeRescheduled() {
        assertTrue(plan(event()).all { it.expired(at + 3 * ReleasePolicy.DAY) })
        val rescheduled = plan(event(time = at + 5 * ReleasePolicy.DAY))
        assertFalse(rescheduled.first().expired(at + 3 * ReleasePolicy.DAY))
    }

    @Test fun advanceIs24ElapsedHoursEvenAcrossDaylightSavingChanges() {
        val airing = ZonedDateTime.parse("2026-10-25T12:00:00+01:00[Europe/Rome]").toInstant().toEpochMilli()
        val reminder = plan(event(time = airing)).first()
        assertEquals(24 * ReleasePolicy.HOUR, airing - reminder.at)
    }

    @Test fun malformedOrUnknownDatesCannotScheduleAnAlarm() {
        assertTrue(plan(event(time = 0), event(episode = 0)).isEmpty())
    }
}
