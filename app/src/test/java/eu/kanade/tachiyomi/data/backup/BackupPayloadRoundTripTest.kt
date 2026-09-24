package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import eu.kanade.tachiyomi.data.backup.models.BackupHiddenResume
import eu.kanade.tachiyomi.data.backup.models.BackupHiddenResumeState
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupTvEpisode
import eu.kanade.tachiyomi.data.backup.models.BackupTvProfile
import eu.kanade.tachiyomi.data.backup.models.BackupTvProfiles
import eu.kanade.tachiyomi.data.backup.models.BackupTvTitle
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BackupPayloadRoundTripTest {
    @Test fun olderPayloadWithoutNewOptionalFieldsStillDecodes() {
        val olderPayload = Backup(
            isLegacy = false,
            backupAnime = listOf(BackupAnime(source = 42, url = "/older/show")),
        )

        val decoded = ProtoBuf.decodeFromByteArray(
            Backup.serializer(),
            ProtoBuf.encodeToByteArray(Backup.serializer(), olderPayload),
        )

        assertEquals("/older/show", decoded.backupAnime.single().url)
        assertNull(decoded.backupHiddenResume)
        assertNull(decoded.backupTvProfiles)
    }

    @Test fun familyProfilesAndIndependentProgressRoundTripWithoutPins() {
        val snapshot = BackupTvProfiles(
            profiles = listOf(
                BackupTvProfile("main", "Principale", 0),
                BackupTvProfile("person-2", "Ospite", 3),
            ),
            titles = listOf(BackupTvTitle("person-2", 42, "/title", "Title", true, "In corso")),
            episodes = listOf(
                BackupTvEpisode(
                    "person-2", 42, "/title", "/episode-1",
                    "Episode 1", false, true, 42_000, 1_400_000, 123456,
                ),
            ),
        )
        val bytes = ProtoBuf.encodeToByteArray(
            Backup.serializer(),
            Backup(isLegacy = false, backupTvProfiles = snapshot),
        )
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)

        assertEquals(snapshot, decoded.backupTvProfiles)
        assertEquals(42_000L, decoded.backupTvProfiles?.episodes?.single()?.positionMs)
    }

    @Test fun progressAndSourceReferencesSurviveTheExistingBackupFormat() {
        val backup = Backup(
            isLegacy = false,
            backupAnime = listOf(
                BackupAnime(
                    source = 42,
                    url = "/show/one",
                    title = "Show",
                    backgroundUrl = "https://example.invalid/background.jpg",
                    episodes = listOf(
                        BackupEpisode(
                            url = "/show/one/episode/2",
                            name = "Episode 2",
                            lastSecondSeen = 812,
                            totalSeconds = 1440,
                        ),
                    ),
                ),
            ),
            backupManga = listOf(
                BackupManga(
                    source = 73,
                    url = "/comic/one",
                    title = "Comic",
                    chapters = listOf(
                        BackupChapter(url = "/comic/one/chapter/3", name = "Chapter 3", lastPageRead = 17),
                    ),
                ),
            ),
            backupHiddenResume = BackupHiddenResumeState(listOf(BackupHiddenResume(42, "/show/one"))),
        )

        val restored = ProtoBuf.decodeFromByteArray(
            Backup.serializer(),
            ProtoBuf.encodeToByteArray(Backup.serializer(), backup),
        )

        assertFalse(restored.isLegacy)
        assertEquals(42L, restored.backupAnime.single().source)
        assertEquals("https://example.invalid/background.jpg", restored.backupAnime.single().backgroundUrl)
        assertEquals(812L, restored.backupAnime.single().episodes.single().lastSecondSeen)
        assertEquals(1440L, restored.backupAnime.single().episodes.single().totalSeconds)
        assertEquals(73L, restored.backupManga.single().source)
        assertEquals(17L, restored.backupManga.single().chapters.single().lastPageRead)
        assertEquals("/show/one", restored.backupHiddenResume?.entries?.single()?.url)
    }
}
