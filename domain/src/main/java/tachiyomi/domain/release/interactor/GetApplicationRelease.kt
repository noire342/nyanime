package tachiyomi.domain.release.interactor

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService
import java.time.Instant
import java.time.temporal.ChronoUnit

class GetApplicationRelease(
    private val service: ReleaseService,
    private val preferenceStore: PreferenceStore,
) {

    suspend fun await(arguments: Arguments): Result {
        val now = Instant.now()
        val checkKey = if (arguments.previewChannel == PreviewChannel.TV) {
            "last_app_check_tv"
        } else {
            "last_app_check"
        }
        val lastChecked: Preference<Long> = preferenceStore.getLong(Preference.appStateKey(checkKey), 0)

        // Limit checks to once every 3 days at most
        if (!arguments.forceCheck &&
            now.isBefore(
                Instant.ofEpochMilli(lastChecked.get()).plus(3, ChronoUnit.DAYS),
            )
        ) {
            return Result.NoNewUpdate
        }

        val release = service.latest(arguments) ?: return Result.NoNewUpdate

        lastChecked.set(now.toEpochMilli())

        // Check if latest version is different from current version
        val isNewVersion = isNewVersion(
            arguments.isPreview,
            arguments.commitCount,
            arguments.versionName,
            release.version,
            arguments.previewChannel,
        )
        return when {
            isNewVersion -> Result.NewUpdate(release)
            else -> Result.NoNewUpdate
        }
    }

    private fun isNewVersion(
        isPreview: Boolean,
        commitCount: Int,
        versionName: String,
        versionTag: String,
        previewChannel: PreviewChannel,
    ): Boolean {
        // Removes prefixes like "r" or "v"
        return if (isPreview) {
            // Preview builds from the fork are tagged as "r<fork commit count>".
            // Reject unrelated tag formats instead of accidentally extracting digits from them.
            val newCommitCount = previewChannel.tagRegex
                .matchEntire(versionTag)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            newCommitCount != null && newCommitCount > commitCount
        } else {
            // Release builds: based on releases in "tachiyomiorg/tachiyomi" repo
            // tagged as something like "v0.1.2"
            val newVersion = versionTag.replace("[^\\d.]".toRegex(), "")
            val oldVersion = versionName.replace("[^\\d.]".toRegex(), "")

            val newSemVer = newVersion.split(".").map { it.toInt() }
            val oldSemVer = oldVersion.split(".").map { it.toInt() }

            oldSemVer.mapIndexed { index, i ->
                if (newSemVer[index] > i) {
                    return true
                }
            }

            false
        }
    }

    data class Arguments(
        val isPreview: Boolean,
        val commitCount: Int,
        val versionName: String,
        val repository: String,
        val forceCheck: Boolean = false,
        val previewChannel: PreviewChannel = PreviewChannel.STANDARD,
    )

    sealed interface Result {
        data class NewUpdate(val release: Release) : Result
        data object NoNewUpdate : Result
        data object OsTooOld : Result
    }

    enum class PreviewChannel(val tagPrefix: String) {
        STANDARD("r"),
        TV("tv-r"),
        ;

        val tagRegex = Regex("^${Regex.escape(tagPrefix)}(\\d+)$", RegexOption.IGNORE_CASE)
    }
}
