package eu.kanade.tachiyomi.data.watch

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class WatchRoomTest {
    private class Player(private val now: () -> Long) : WatchPlayer {
        var media: WatchMedia? = WatchMedia("Test Animation", "Episode 1", 1.0, 1400.0, 7, "/title", "/1")
        var ready = true
        var buffering = false
        var paused = true
        var speed = 1.0
        var seeks = 0
        var resumed = 0
        private var position = 40.0
        private var last = now()
        override fun sample(): WatchPlayback {
            val at = now()
            if (!paused && ready && !buffering) position += (at - last) / 1000.0 * speed
            last = at
            return WatchPlayback(media, position, paused, ready, buffering, speed)
        }
        override fun pause(paused: Boolean) {
            sample()
            this.paused = paused
        }
        override fun seek(seconds: Double) {
            sample()
            position = seconds
            seeks++
        }
        override fun speed(value: Double) {
            sample()
            speed = value
        }
        override fun userResumed() {
            resumed++
        }
    }

    private class Network {
        val endpoints = mutableListOf<Endpoint>()
        var intercept: ((Endpoint, WatchMessage, () -> Unit) -> Unit)? = null
        fun factory(
            invite: WatchInvite,
            identity: WatchIdentity,
        ): WatchTransport = Endpoint(invite, identity.publicKey).also(endpoints::add)
        inner class Endpoint(val invite: WatchInvite, override val publicKey: String) : WatchTransport {
            var receiver: (String, WatchMessage) -> Unit = { _, _ -> }
            var connection: (Int) -> Unit = {}
            var online = true
            var closed = false
            val sent = mutableListOf<WatchMessage>()
            override fun start(onMessage: (String, WatchMessage) -> Unit, onConnection: (Int) -> Unit) {
                receiver = onMessage
                connection = onConnection
                connection(2)
            }
            override fun send(message: WatchMessage) {
                sent.add(message)
                if (!online || closed) return
                val delivery = {
                    endpoints.filter { it !== this && !it.closed && it.online && it.invite.topic == invite.topic }
                        .forEach { it.receiver(publicKey, message) }
                }
                intercept?.invoke(this, message, delivery) ?: delivery()
            }
            override fun close() {
                closed = true
            }
        }
    }

    private class Pairing(val scope: TestScope) {
        val network = Network()
        val hostPlayer = Player { scope.testScheduler.currentTime }
        val guestPlayer = Player { scope.testScheduler.currentTime }
        val host =
            WatchRoomController(scope.backgroundScope, hostPlayer, { scope.testScheduler.currentTime }, {
                1_800_000_000_000L +
                    scope.testScheduler.currentTime
            }, network::factory)
        val guest =
            WatchRoomController(scope.backgroundScope, guestPlayer, { scope.testScheduler.currentTime + 90_000 }, {
                1_800_000_000_000L +
                    scope.testScheduler.currentTime
            }, network::factory)
        fun join() {
            host.create("Host")
            scope.runCurrent()
            guest.join(host.state.value.invite, "Friend")
            scope.advanceTimeBy(5000)
            scope.runCurrent()
        }
        fun advance(ms: Long = 3000) {
            scope.advanceTimeBy(ms)
            scope.runCurrent()
        }
    }

    @Test
    fun codeJoinsRoomAndCorrectsDifferentDeviceClocks() = runTest {
        val room = Pairing(this)
        room.join()
        assertEquals(2, room.host.state.value.members.size)
        assertNotNull(room.guest.state.value.latencyMs)
        room.host.resumeByUser()
        room.advance()
        assertFalse(room.hostPlayer.paused)
        assertFalse(room.guestPlayer.paused)
        assertTrue(abs(room.hostPlayer.sample().position - room.guestPlayer.sample().position) < 0.4)
    }

    @Test
    fun bothParticipantsControlPlaybackWithoutEchoes() = runTest {
        val room = Pairing(this)
        room.join()
        room.guest.resumeByUser()
        room.advance()
        assertFalse(room.hostPlayer.paused)
        assertFalse(room.guestPlayer.paused)
        room.guest.requestPause(true)
        room.advance()
        assertTrue(room.hostPlayer.paused)
        assertTrue(room.guestPlayer.paused)
        room.guest.requestSeek(300.5)
        room.advance()
        assertEquals(300.5, room.hostPlayer.sample().position, 0.2)
        assertEquals(300.5, room.guestPlayer.sample().position, 0.2)
        room.guest.requestSpeed(1.5)
        room.advance()
        assertEquals(1.5, room.hostPlayer.speed, 0.001)
        assertEquals(1.5, room.guestPlayer.speed, 0.001)
        assertEquals(4, room.network.endpoints[1].sent.count { it.type == WatchMessageType.Command })
    }

    @Test
    fun guestCanSeekWhileHostStillSeesItsPreviousBufferingStatus() = runTest {
        val room = Pairing(this)
        room.join()
        room.host.resumeByUser()
        room.advance()
        room.guestPlayer.buffering = true
        room.guestPlayer.ready = false
        room.advance()
        assertTrue(room.hostPlayer.paused)
        // Releasing a seek gesture sends its command before the next presence update.
        room.guestPlayer.buffering = false
        room.guestPlayer.ready = true
        room.guest.requestSeek(400.0)
        room.advance()
        assertTrue(room.hostPlayer.sample().position >= 400.0)
        assertTrue(abs(room.hostPlayer.sample().position - room.guestPlayer.sample().position) < 0.4)
        assertFalse(room.hostPlayer.paused)
        assertFalse(room.guestPlayer.paused)
    }

    @Test
    fun bufferingPausesGroupUntilGuestIsReady() = runTest {
        val room = Pairing(this)
        room.join()
        room.host.resumeByUser()
        room.advance()
        room.guestPlayer.buffering = true
        room.advance()
        assertTrue(room.hostPlayer.paused)
        assertTrue(room.guestPlayer.paused)
        room.guestPlayer.buffering = false
        room.advance()
        assertFalse(room.hostPlayer.paused)
        assertFalse(room.guestPlayer.paused)
    }

    @Test
    fun localSafetyHoldsCannotBeClearedRemotely() = runTest {
        val room = Pairing(this)
        room.join()
        room.guest.hold()
        room.advance()
        room.host.resumeByUser()
        room.advance()
        assertTrue(room.guestPlayer.paused)
        assertTrue(room.hostPlayer.paused)
        room.guest.resumeByUser()
        room.advance()
        assertFalse(room.guestPlayer.paused)
        assertFalse(room.hostPlayer.paused)
        assertTrue(room.guestPlayer.resumed > 0)
    }

    @Test
    fun oldReadinessCannotReleaseNewEpisode() = runTest {
        val room = Pairing(this)
        room.join()
        room.host.resumeByUser()
        room.advance()
        room.hostPlayer.media = room.hostPlayer.media!!.copy(episode = "Episode 2", number = 2.0, episodeUrl = "/2")
        room.advance()
        assertTrue(room.hostPlayer.paused)
        assertTrue(room.guestPlayer.paused)
        assertEquals(WatchPhase.DifferentVideo, room.guest.state.value.phase)
        room.guestPlayer.media = room.hostPlayer.media
        room.advance()
        assertFalse(room.hostPlayer.paused)
        assertFalse(room.guestPlayer.paused)
    }

    @Test
    fun lostCommandRetriesSurviveLaterStatusMessages() = runTest {
        val room = Pairing(this)
        room.join()
        var lost = false
        room.network.intercept = { _, message, deliver ->
            if (message.type == WatchMessageType.Command && !lost) lost = true else deliver()
        }
        room.guest.resumeByUser()
        room.advance(7000)
        assertTrue(lost)
        assertFalse(room.hostPlayer.paused)
        assertFalse(room.guestPlayer.paused)
        assertTrue(room.network.endpoints[1].sent.count { it.type == WatchMessageType.Command } >= 2)
    }

    @Test
    fun creatorDisappearancePausesAndLeavingRestoresSpeed() = runTest {
        val room = Pairing(this)
        room.join()
        room.host.resumeByUser()
        room.advance()
        room.network.endpoints[0].online = false
        room.advance(14_000)
        assertTrue(room.guestPlayer.paused)
        assertEquals(WatchPhase.Reconnecting, room.guest.state.value.phase)
        room.guest.leave()
        assertFalse(room.guest.active)
        assertEquals(1.0, room.guestPlayer.speed, 0.0001)
        assertTrue(room.guestPlayer.paused)
    }

    @Test
    fun creatorClosingTerminatesRemoteControl() = runTest {
        val room = Pairing(this)
        room.join()
        room.host.leave()
        room.advance()
        assertFalse(room.guest.active)
        assertEquals(WatchPhase.Closed, room.guest.state.value.phase)
    }

    @Test
    fun roomCanExistBeforeChoosingVideo() = runTest {
        val room = Pairing(this)
        room.hostPlayer.media = null
        room.hostPlayer.ready = false
        room.guestPlayer.media = null
        room.guestPlayer.ready = false
        room.join()
        assertTrue(room.host.active)
        assertTrue(room.guest.active)
        assertEquals(WatchPhase.Waiting, room.guest.state.value.phase)
        room.hostPlayer.media = WatchMedia("Test Animation", "Episode 1", 1.0, 1400.0)
        room.hostPlayer.ready = true
        room.advance()
        assertEquals("Test Animation", room.guest.state.value.media?.title)
    }

    @Test
    fun differentCutDoesNotGetForcedIntoSynchronization() = runTest {
        val room = Pairing(this)
        room.guestPlayer.media = room.guestPlayer.media!!.copy(duration = 1000.0)
        room.join()
        room.host.resumeByUser()
        room.advance()
        assertTrue(room.guestPlayer.paused)
        assertTrue(room.hostPlayer.paused)
        assertEquals(WatchPhase.DifferentVideo, room.guest.state.value.phase)
    }

    @Test
    fun disabledGuestControlsStillAllowLocalSafetyHold() = runTest {
        val room = Pairing(this)
        room.join()
        room.host.setSharedControls(false)
        room.advance()
        room.guest.resumeByUser()
        room.advance()
        assertTrue(room.hostPlayer.paused)
        room.host.resumeByUser()
        room.advance()
        room.guest.hold()
        room.advance()
        assertTrue(room.guestPlayer.paused)
        assertTrue(room.hostPlayer.paused)
    }

    @Test
    fun correctionDeadbandAndBoundedRates() {
        assertEquals(1.0, WatchSynchronizer.correct(20.0, 20.1, 1.0, false, true).speed)
        assertEquals(1.03, WatchSynchronizer.correct(20.0, 20.8, 1.0, false, true).speed)
        assertEquals(0.97, WatchSynchronizer.correct(20.0, 19.2, 1.0, false, true).speed)
        assertEquals(50.0, WatchSynchronizer.correct(20.0, 50.0, 1.0, false, true).seek)
        assertEquals(null, WatchSynchronizer.correct(20.0, 50.0, 1.0, false, false).seek)
    }

    @Test
    fun delayedOlderSeekCannotUndoLatestSeek() = runTest {
        val room = Pairing(this)
        room.join()
        var delayed: (() -> Unit)? = null
        room.network.intercept = { _, message, deliver ->
            if (message.type == WatchMessageType.Command && message.position == 100.0) delayed = deliver else deliver()
        }
        room.guest.requestSeek(100.0)
        room.guest.requestSeek(400.0)
        room.advance()
        delayed?.invoke()
        room.advance()
        assertEquals(400.0, room.hostPlayer.sample().position, 0.2)
        assertEquals(400.0, room.guestPlayer.sample().position, 0.2)
    }

    @Test
    fun playIntentCanBeCancelledWhileBuffering() = runTest {
        val room = Pairing(this)
        room.join()
        room.guestPlayer.buffering = true
        room.host.resumeByUser()
        room.advance()
        assertTrue(room.guest.state.value.playRequested)
        room.guest.requestPause(true)
        room.advance()
        room.guestPlayer.buffering = false
        room.advance()
        assertTrue(room.hostPlayer.paused)
        assertTrue(room.guestPlayer.paused)
        assertFalse(room.host.state.value.playRequested)
    }

    @Test
    fun closedRoomIgnoresQueuedMessagesFromPreviousSession() = runTest {
        val room = Pairing(this)
        room.join()
        val previousEndpoint = room.network.endpoints[1]
        room.guest.leave()
        room.guest.create("New host")
        val newCode = room.guest.state.value.invite
        previousEndpoint.receiver(
            room.network.endpoints[0].publicKey,
            WatchMessage(type = WatchMessageType.Closed, sequence = 9999, at = 0),
        )
        room.advance()
        assertTrue(room.guest.active)
        assertTrue(room.guest.state.value.host)
        assertEquals(newCode, room.guest.state.value.invite)
    }

    @Test
    fun roomLimitRejectsAnExtraGuestClearly() = runTest {
        val room = Pairing(this)
        room.join()
        val guests = (1..7).map {
            WatchRoomController(
                backgroundScope,
                Player { testScheduler.currentTime },
                { testScheduler.currentTime },
                { 1_800_000_000_000L + testScheduler.currentTime },
                room.network::factory,
            ).also {
                it.join(room.host.state.value.invite, "Friend")
                room.advance()
            }
        }
        assertEquals(8, room.host.state.value.members.size)
        assertFalse(guests.last().active)
        assertEquals(WatchPhase.Failed, guests.last().state.value.phase)
    }
}
