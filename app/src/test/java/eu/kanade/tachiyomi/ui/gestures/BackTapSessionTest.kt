package eu.kanade.tachiyomi.ui.gestures

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackTapSessionTest {
    @Test fun ownerChangesInvalidateEvenARecentlyDetectedGesture() {
        val session = BackTapSession()
        val player = session.renew()
        session.renew()
        assertFalse(session.accept(player, 1_000_000_000, 1_100_000_000))
    }

    @Test fun expiredFutureAndDuplicateGesturesNeverReachTheTarget() {
        val session = BackTapSession()
        val token = session.renew()
        assertFalse(session.accept(token, 1_000_000_000, 1_300_000_000))
        assertFalse(session.accept(token, 1_100_000_000, 1_000_000_000))
        assertTrue(session.accept(token, 1_000_000_000, 1_100_000_000))
        assertFalse(session.accept(token, 1_100_000_000, 1_200_000_000))
        assertTrue(session.accept(token, 2_100_000_000, 2_200_000_000))
    }
}
