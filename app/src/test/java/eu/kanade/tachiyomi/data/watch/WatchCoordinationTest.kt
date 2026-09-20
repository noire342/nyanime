package eu.kanade.tachiyomi.data.watch

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class WatchCoordinationTest {
    @Test
    fun smoothCorrectionConvergesWithoutSeekingOrSpeedSteps() {
        val corrector = WatchDriftCorrector()
        var local = 99.2
        var remote = 100.0
        var speed = 1.0
        repeat(600) { tick ->
            val correction = corrector.correct(local, remote, 1.0, false, true, tick * 250L)
            assertNull(correction.seek)
            assertTrue(abs(correction.speed - speed) <= 0.0031)
            assertTrue(correction.speed in 0.97..1.03)
            speed = correction.speed
            local += 0.25 * speed
            remote += 0.25
        }
        assertTrue(abs(local - remote) < 0.2)
    }

    @Test
    fun jitterDoesNotKeepChangingSpeedAndPersistentLargeErrorsSeek() {
        val corrector = WatchDriftCorrector()
        repeat(100) { tick ->
            val correction = corrector.correct(
                100.0,
                100.0 +
                    if (tick % 2 ==
                        0
                    ) {
                        0.2
                    } else {
                        -0.2
                    },
                1.5,
                false,
                true,
                tick * 250L,
            )
            assertEquals(1.5, correction.speed)
            assertNull(correction.seek)
        }
        corrector.reset()
        assertNull(corrector.correct(10.0, 13.0, 1.0, false, true, 0).seek)
        assertNull(corrector.correct(10.0, 13.0, 1.0, false, true, 1000).seek)
        assertEquals(13.0, corrector.correct(10.0, 13.0, 1.0, false, true, 1500).seek)
        assertEquals(1.0, corrector.correct(13.0, 13.0, 1.0, true, true, 2000).speed)
    }

    @Test
    fun everyInterruptionInvalidatesTheSharedStartDeadline() {
        val gate = WatchStartGate()
        assertFalse(gate.update(true, true, 0))
        assertFalse(gate.update(true, true, 1000))
        assertEquals(3000L, gate.deadline)
        assertFalse(gate.update(false, true, 2500))
        assertNull(gate.deadline)
        assertFalse(gate.update(true, true, 3000))
        assertFalse(gate.update(true, true, 4000))
        assertFalse(gate.update(true, true, 5999))
        assertTrue(gate.update(true, true, 6000))
        assertFalse(gate.update(true, false, 6100))
        assertNull(gate.deadline)
    }

    @Test
    fun linksAndQrPreserveTheExactInvitationWithoutNetworking() {
        val identity = WatchIdentity()
        val now = 1_800_000_000_000L
        val invitation = WatchInvite.create(identity.publicKey, now)
        val link = invitation.link()
        assertEquals(invitation.encode(), WatchInvite.codeFromLink(link, now))
        val qr = WatchQr.encode(link)
        val pixels = IntArray(qr.width * qr.height) {
            if (qr[it % qr.width, it / qr.width]) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(qr.width, qr.height, pixels)))
        val decoded = QRCodeReader().decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to true)).text
        assertEquals(link, decoded)
        assertEquals(invitation.secret, WatchInvite.parse(decoded, now).secret)
        identity.clear()
    }

    @Test
    fun externalLinksMustMatchTheExactRouteAndDoNotAcceptCredentialsOrQueries() {
        val now = 1_800_000_000_000L
        val identity = WatchIdentity()
        val code = WatchInvite.create(identity.publicKey, now).encode()
        for (link in listOf(
            "https://watch/v1#" + code,
            "nyanime://other/v1#" + code,
            "nyanime://user@watch/v1#" + code,
            "nyanime://watch/v1?redirect=elsewhere#" + code,
            "nyanime://watch/v2#" + code,
        )) {
            assertThrows(IllegalArgumentException::class.java) { WatchInvite.codeFromLink(link, now) }
        }
        identity.clear()
    }
}
