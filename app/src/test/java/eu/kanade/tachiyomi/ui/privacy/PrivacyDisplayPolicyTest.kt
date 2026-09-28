package eu.kanade.tachiyomi.ui.privacy

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrivacyDisplayPolicyTest {
    @Test fun libraryProtectionAndNsfwProtectionRemainIndependent() {
        val libraries = PrivacyDisplayPolicy(true, setOf(PrivacyArea.LIBRARY))
        assertTrue(libraries.permits(PrivacyArea.LIBRARY))
        assertFalse(libraries.permits(PrivacyArea.NSFW, PrivacyArea.LIBRARY))
        assertFalse(libraries.copy(enabled = false).permits(PrivacyArea.LIBRARY))

        val nsfwOnly = libraries.copy(selectedAreas = setOf(PrivacyArea.NSFW))
        assertFalse(nsfwOnly.permits(PrivacyArea.LIBRARY))
        assertTrue(nsfwOnly.permits(PrivacyArea.NSFW, PrivacyArea.LIBRARY))

        val temporaryVideoOverride = libraries.copy(temporaryOverrides = mapOf(PrivacyArea.VIDEO to false))
        assertTrue(temporaryVideoOverride.permits(PrivacyArea.LIBRARY))
    }

    @Test fun masterOptInControlsOrdinaryAndNsfwDeclarations() {
        val policy = PrivacyDisplayPolicy(false, PrivacyArea.entries.toSet())
        assertTrue(PrivacyArea.entries.none { policy.permits(it) })
        val active = policy.copy(enabled = true, selectedAreas = setOf(PrivacyArea.NSFW, PrivacyArea.READER))
        assertTrue(active.permits(PrivacyArea.NSFW, PrivacyArea.VIDEO))
        assertTrue(active.permits(PrivacyArea.READER))
        assertFalse(active.permits(PrivacyArea.VIDEO))
    }

    @Test fun temporaryVideoChoiceAlsoControlsItsNsfwRegionAndDoesNotAffectReading() {
        val policy = PrivacyDisplayPolicy(true, PrivacyArea.entries.toSet())
        val disabled = policy.copy(temporaryOverrides = mapOf(PrivacyArea.VIDEO to false))
        assertFalse(disabled.permits(PrivacyArea.VIDEO))
        assertFalse(disabled.permits(PrivacyArea.NSFW, PrivacyArea.VIDEO))
        assertTrue(disabled.permits(PrivacyArea.NSFW, PrivacyArea.READER))
        assertTrue(disabled.permits(PrivacyArea.READER))
        // A new window has no temporary exception and uses the persisted policy.
        assertTrue(policy.permits(PrivacyArea.VIDEO))
    }

    @Test fun explicitTemporaryOptInWorksWithoutChangingTheGlobalPreference() {
        val policy = PrivacyDisplayPolicy(false, PrivacyArea.entries.toSet())
        val enabled = policy.copy(temporaryOverrides = mapOf(PrivacyArea.VIDEO to true))
        assertTrue(enabled.permits(PrivacyArea.VIDEO))
        assertFalse(enabled.permits(PrivacyArea.READER))
        assertFalse(policy.permits(PrivacyArea.VIDEO))
    }
}
