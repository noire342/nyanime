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
        var ended = false
        var upcoming: WatchMedia? = null
        var canAdvance = true
        var preparedNextKey: String? = null
        var problem = WatchProblem.None
        var nextProblem = WatchProblem.None
        var advances = 0
        private var position = 40.0
        private var last = now()
        override fun sample(): WatchPlayback {
            val at = now()
            if (!paused && ready && !buffering) position += (at - last) / 1000.0 * speed
            last = at
            return WatchPlayback(
                media, position, paused, ready, buffering, speed, ended, problem, upcoming,
                canAdvance, preparedNextKey, nextProblem,
            )
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
        override fun advance(media: WatchMedia) {
            advances++
            this.media = media.copy(duration = 1400.0)
            upcoming = null
            ended = false
            seek(0.0)
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
        fun advance(ms: Long = 5000) {
            scope.advanceTimeBy(ms)
            scope.runCurrent()
        }
    }

    @Test
    fun hostPlayShowsPreparationBeforeTheNextTickAndCanBeCancelled() = runTest {
        val room = Pairing(this)
        room.join()
        room.host.resumeByUser()
        assertTrue(room.host.state.value.preparingPlayback)
        assertTrue(room.hostPlayer.paused)
        assertEquals(null, room.host.state.value.resumeSeconds)
        room.host.requestPause(true)
        assertFalse(room.host.state.value.preparingPlayback)
        room.advance()
        assertTrue(room.hostPlayer.paused)
        assertTrue(room.guestPlayer.paused)
    }

    @Test
    fun guestPlayShowsPendingFeedbackUntilAcknowledgedWithoutStartingEarly() = runTest {
        val room = Pairing(this)
        room.join()
        room.network.intercept = { _, message, deliver ->
            if (message.type != WatchMessageType.Command) deliver()
        }
        room.guest.resumeByUser()
        assertTrue(room.guest.state.value.preparingPlayback)
        assertTrue(room.guest.state.value.wantsPlayback)
        assertEquals(false, room.guest.state.value.pendingPlaybackPaused)
        room.advance(1000)
        assertTrue(room.guest.state.value.preparingPlayback)
        assertTrue(room.guestPlayer.paused)
        assertTrue(room.hostPlayer.paused)
        room.network.intercept = null
        room.advance(8000)
        assertEquals(null, room.guest.state.value.pendingPlaybackPaused)
        assertFalse(room.guest.state.value.preparingPlayback)
        assertFalse(room.guestPlayer.paused)
        assertTrue(abs(room.hostPlayer.sample().position - room.guestPlayer.sample().position) < 0.4)
    }

    @Test
    fun pendingPlayCancellationAndTimeoutDoNotLeaveAnIndefinitePreparationCue() = runTest {
        val room = Pairing(this)
        room.join()
        room.network.intercept = { _, message, deliver ->
            if (message.type != WatchMessageType.Command) deliver()
        }
        room.guest.resumeByUser()
        room.guest.requestPause(true)
        assertFalse(room.guest.state.value.wantsPlayback)
        assertFalse(room.guest.state.value.preparingPlayback)
        room.guest.resumeByUser()
        assertTrue(room.guest.state.value.preparingPlayback)
        room.advance(9000)
        assertFalse(room.guest.state.value.preparingPlayback)
        assertEquals(null, room.guest.state.value.pendingPlaybackPaused)
        assertTrue(room.guest.state.value.message.contains("non confermato"))
        assertTrue(room.guestPlayer.paused)
        room.guest.leave()
        assertFalse(room.guest.state.value.preparingPlayback)
    }

    @Test
    fun inactiveRoomControlsNeverReadOrModifyThePlayer() = runTest {
        val player = object : WatchPlayer {
            override fun sample(): WatchPlayback = error("Inactive room sampled the player")
            override fun pause(paused: Boolean): Unit = error("Inactive room changed pause")
            override fun seek(seconds: Double): Unit = error("Inactive room sought")
            override fun speed(value: Double): Unit = error("Inactive room changed speed")
            override fun userResumed(): Unit = error("Inactive room changed local safety")
        }
        val controller = WatchRoomController(backgroundScope, player, { testScheduler.currentTime })
        controller.playerAttached()
        controller.hold()
        controller.resync()
        controller.confirmSameVideo()
        controller.offerSkip("unused", "Skip", 90.0)
        controller.setSharedControls(false)
        controller.setWaitForEveryone(false)
        assertFalse(controller.requestPause(true))
        assertFalse(controller.requestPause(false))
        assertFalse(controller.requestSeek(30.0))
        assertFalse(controller.requestSpeed(1.5))
        assertFalse(controller.resumeByUser())
        controller.cancelSkip()
        controller.cancelNext()
        controller.leave()
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(controller.active)
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

    @Test
    fun stableReadinessAndSharedCountdownAreCancelledByPause() = runTest {
        val room = Pairing(this)
        room.join()
        room.host.resumeByUser()
        room.advance(1500)
        assertTrue(room.hostPlayer.paused)
        assertEquals(WatchPhase.Starting, room.host.state.value.phase)
        assertEquals(room.host.state.value.resumeSeconds, room.guest.state.value.resumeSeconds)
        room.guest.requestPause(true)
        room.advance()
        assertTrue(room.hostPlayer.paused)
        assertTrue(room.guestPlayer.paused)
        assertEquals(null, room.host.state.value.resumeSeconds)
        assertTrue(room.host.state.value.message.contains("Friend"))
        assertTrue(room.guest.state.value.message.contains("Friend"))
    }

    @Test
    fun readinessFlappingNeverProducesRepeatedStarts() = runTest {
        val room = Pairing(this)
        room.join()
        room.host.resumeByUser()
        repeat(4) {
            room.guestPlayer.buffering = true
            room.advance(500)
            room.guestPlayer.buffering = false
            room.advance(750)
            assertTrue(room.hostPlayer.paused)
        }
        room.advance()
        assertFalse(room.hostPlayer.paused)
        assertFalse(room.guestPlayer.paused)
    }

    @Test
    fun sharedSkipIsAppliedOnceAndLateDuplicateIsHarmless() = runTest {
        val room = Pairing(this)
        room.join()
        room.host.offerSkip("opening-1", "Salta apertura", 120.0)
        room.advance()
        room.guest.requestSkip()
        room.guest.requestSkip()
        room.advance()
        assertEquals(120.0, room.hostPlayer.sample().position, 0.2)
        assertEquals(120.0, room.guestPlayer.sample().position, 0.2)
        val seeks = room.hostPlayer.seeks
        room.host.offerSkip("opening-1", "Salta apertura", 120.0)
        room.advance()
        assertEquals(null, room.host.state.value.skip)
        assertEquals(seeks, room.hostPlayer.seeks)
    }

    @Test
    fun automaticSkipWaitsForPlaybackAndGuestCanCancelItForEveryone() = runTest {
        val room = Pairing(this)
        room.join()
        room.host.offerSkip("opening-1", "Salta apertura", 120.0, 3)
        room.advance(8000)
        assertEquals(40.0, room.hostPlayer.sample().position, 0.2)
        room.host.resumeByUser()
        room.advance(3750)
        assertNotNull(room.guest.state.value.skipSeconds)
        room.guest.cancelSkip()
        room.advance(7000)
        assertEquals(null, room.host.state.value.skip)
        assertTrue(room.hostPlayer.sample().position < 60)
    }

    @Test
    fun nextEpisodeWaitsForPreparationAndCancellationIsShared() = runTest {
        val room = Pairing(this)
        room.join()
        val next = room.hostPlayer.media!!.copy(episodeUrl = "/2", episode = "Episode 2", number = 2.0, duration = 0.0)
        room.hostPlayer.upcoming = next
        room.hostPlayer.ended = true
        room.advance()
        assertEquals(next, room.guest.state.value.next?.media)
        assertEquals(null, room.host.state.value.nextSeconds)
        room.guestPlayer.preparedNextKey = next.key
        room.advance(1000)
        assertNotNull(room.host.state.value.nextSeconds)
        room.guest.cancelNext()
        room.advance(15_000)
        assertEquals(0, room.hostPlayer.advances)
        assertEquals(null, room.guest.state.value.next)
    }

    @Test
    fun nextEpisodeRespectsTimerHoldAndAdvancesOnlyOnceAfterAllAreReady() = runTest {
        val room = Pairing(this)
        room.join()
        val next = room.hostPlayer.media!!.copy(episodeUrl = "/2", episode = "Episode 2", number = 2.0, duration = 0.0)
        room.hostPlayer.upcoming = next
        room.hostPlayer.ended = true
        room.guestPlayer.preparedNextKey = next.key
        room.guestPlayer.canAdvance = false
        room.advance(15_000)
        assertEquals(0, room.hostPlayer.advances)
        room.guestPlayer.canAdvance = true
        room.advance(15_000)
        assertEquals(1, room.hostPlayer.advances)
        room.advance(15_000)
        assertEquals(1, room.hostPlayer.advances)
        assertTrue(room.hostPlayer.paused)
    }

    @Test
    fun reconnectedGuestWaitsForFreshHostStateInsteadOfReusingPlayIntent() = runTest {
        val room = Pairing(this)
        room.join()
        room.host.resumeByUser()
        room.advance()
        assertFalse(room.guestPlayer.paused)
        val guestEndpoint = room.network.endpoints[1]
        guestEndpoint.online = false
        guestEndpoint.connection(0)
        room.advance(1000)
        assertTrue(room.guestPlayer.paused)
        room.network.endpoints[0].online = false
        guestEndpoint.online = true
        guestEndpoint.connection(2)
        room.advance(1000)
        assertTrue(room.guestPlayer.paused)
        assertEquals(WatchPhase.Waiting, room.guest.state.value.phase)
        room.network.endpoints[0].online = true
        room.advance(6000)
        assertFalse(room.hostPlayer.paused)
        assertFalse(room.guestPlayer.paused)
    }

    @Test
    fun connectionLossDiscardsNextEpisodeDeadlineBeforeReconnecting() = runTest {
        val room = Pairing(this)
        room.join()
        val next = room.hostPlayer.media!!.copy(episodeUrl = "/2", episode = "Episode 2", number = 2.0, duration = 0.0)
        room.hostPlayer.upcoming = next
        room.hostPlayer.ended = true
        room.guestPlayer.preparedNextKey = next.key
        room.advance(1000)
        assertNotNull(room.host.state.value.nextSeconds)
        val endpoint = room.network.endpoints[0]
        endpoint.online = false
        endpoint.connection(0)
        room.advance(14_000)
        assertEquals(0, room.hostPlayer.advances)
        assertEquals(null, room.host.state.value.nextSeconds)
        assertEquals(null, room.guest.state.value.nextSeconds)
        assertEquals(null, room.guest.state.value.next?.deadline)
        endpoint.online = true
        endpoint.connection(2)
        room.advance(4500)
        assertEquals(0, room.hostPlayer.advances)
        assertNotNull(room.host.state.value.nextSeconds)
        room.advance(12_000)
        assertEquals(1, room.hostPlayer.advances)
    }

    @Test
    fun unresolvedGuestCanCancelTheSharedNextCue() = runTest {
        val room = Pairing(this)
        room.join()
        val next = room.hostPlayer.media!!.copy(episodeUrl = "/2", episode = "Episode 2", number = 2.0, duration = 0.0)
        room.hostPlayer.upcoming = next
        room.hostPlayer.ended = true
        room.guestPlayer.media = null
        room.guestPlayer.ready = false
        room.advance()
        assertEquals(WatchPhase.DifferentVideo, room.guest.state.value.phase)
        assertNotNull(room.guest.state.value.next)
        room.guest.cancelNext()
        room.advance()
        assertEquals(null, room.host.state.value.next)
        assertEquals(null, room.guest.state.value.next)
        assertEquals(0, room.hostPlayer.advances)
    }

    @Test
    fun silentlyLostGuestReadinessCannotAdvanceAnEpisode() = runTest {
        val room = Pairing(this)
        room.join()
        val next = room.hostPlayer.media!!.copy(episodeUrl = "/2", episode = "Episode 2", number = 2.0, duration = 0.0)
        room.hostPlayer.upcoming = next
        room.hostPlayer.ended = true
        room.guestPlayer.preparedNextKey = next.key
        room.advance(1000)
        assertNotNull(room.host.state.value.nextSeconds)
        room.network.endpoints[1].online = false
        room.advance(11_000)
        assertEquals(0, room.hostPlayer.advances)
        assertEquals(null, room.host.state.value.nextSeconds)
        assertEquals(WatchProblem.Connection, room.host.state.value.members.last().problem)
        assertTrue(room.host.state.value.message.contains("Friend"))
        assertTrue(room.host.state.value.message.contains("Riconnessione"))
    }

    @Test
    fun automaticSkipPublishesTheNewPositionAndSeeksOnlyOnce() = runTest {
        val room = Pairing(this)
        room.join()
        room.host.offerSkip("opening-1", "Salta apertura", 120.0, 3)
        room.host.resumeByUser()
        room.advance(12_000)
        assertEquals(1, room.hostPlayer.seeks)
        assertEquals(null, room.host.state.value.skip)
        assertTrue(room.hostPlayer.sample().position >= 120)
        assertTrue(abs(room.hostPlayer.sample().position - room.guestPlayer.sample().position) < 0.4)
        val timeline = room.network.endpoints[0].sent.last { it.type == WatchMessageType.Timeline }
        assertTrue(timeline.position >= 120)
    }

    @Test
    fun actionablePeerProblemsAreVisibleWithoutSharingSourceErrors() = runTest {
        val room = Pairing(this)
        room.join()
        room.guestPlayer.ready = false
        room.guestPlayer.problem = WatchProblem.MissingSource
        room.advance()
        assertTrue(room.host.state.value.message.contains("Friend"))
        assertTrue(room.host.state.value.message.contains("Estensione"))
        assertEquals(WatchProblem.MissingSource, room.host.state.value.members.last().problem)
    }
}
