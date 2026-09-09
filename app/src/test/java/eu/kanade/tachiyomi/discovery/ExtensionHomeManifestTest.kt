package eu.kanade.tachiyomi.discovery

import eu.kanade.tachiyomi.data.discovery.ExtensionHomeManifest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtensionHomeManifestTest {
    private val film = """{"id":"films","title":"Film","source":{"name":"Ciao","lang":"it"},
        "sections":[{"id":"popular","title":"Più visti"}]}"""
    private fun wrap(homes: String) = """{"version":1,"homes":[$homes]}"""

    @Test fun arbitraryExtensionsCanDeclareNewContentKinds() {
        val result = ExtensionHomeManifest.parse(wrap(film)).single()
        assertEquals("films", result.id)
        assertEquals("Ciao", result.source.name)
        assertEquals("Film", result.title)
    }

    @Test fun missingMalformedOversizedAndUnsupportedVersionsAreIgnored() {
        for (invalid in listOf(
            "",
            "{}",
            "<html>",
            wrap(film).replace("\"version\":1", "\"version\":2"),
            "x".repeat(65_537),
        )) {
            assertTrue(ExtensionHomeManifest.parse(invalid).isEmpty())
        }
    }

    @Test fun invalidSiblingDoesNotSuppressValidHome() {
        assertEquals("films", ExtensionHomeManifest.parse(wrap("{},$film")).single().id)
    }

    @Test fun duplicateIdentifiersInsideOneExtensionAreAmbiguous() {
        assertTrue(ExtensionHomeManifest.parse(wrap("$film,$film")).isEmpty())
    }

    @Test fun identifiersLabelsAndResourceLimitsAreValidated() {
        for (invalid in listOf(
            film.replace("\"films\"", "\"../films\""),
            film.replace("\"Film\"", "\"\""),
            film.replace("\"Film\"", "\"${"é".repeat(101)}\""),
            film.replace("\"popular\"", "\"search\""),
        )) {
            assertTrue(ExtensionHomeManifest.parse(wrap(invalid)).isEmpty())
        }
        assertTrue(ExtensionHomeManifest.parse(wrap(List(9) { film }.joinToString(","))).isEmpty())
    }

    @Test fun forwardCompatibleUnknownMetadataDoesNotBecomeExecutableConfiguration() {
        val input = wrap(
            film,
        ).replace("\"version\":1", "\"version\":1,\"remoteUrl\":\"https://example.invalid/ignored\"")
        assertEquals(ExtensionHomeManifest.parse(wrap(film)), ExtensionHomeManifest.parse(input))
    }
}
