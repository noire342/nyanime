package eu.kanade.tachiyomi.extension.anime.api

import android.content.Context
import eu.kanade.tachiyomi.extension.ExtensionUpdate
import eu.kanade.tachiyomi.extension.ExtensionUpdateCheckGate
import eu.kanade.tachiyomi.extension.ExtensionUpdateKind
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionLoader
import mihon.domain.extension.anime.interactor.UpdateAnimeExtensionStores
import mihon.domain.extension.anime.repository.AnimeExtensionStoreRepository
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

internal class AnimeExtensionApi {

    private val repository: AnimeExtensionStoreRepository by injectLazy()

    private val preferenceStore: PreferenceStore by injectLazy()
    private val updateExtensionStores: UpdateAnimeExtensionStores by injectLazy()
    private val animeExtensionManager: AnimeExtensionManager by injectLazy()

    private val updateGate by lazy { ExtensionUpdateCheckGate(preferenceStore) }

    suspend fun findExtensions(): List<AnimeExtension.Available> {
        return withIOContext { repository.fetchExtensions() }
    }

    suspend fun checkForUpdates(
        context: Context,
        fromAvailableExtensionList: Boolean = false,
    ): List<AnimeExtension.Installed>? = updateGate.run(ExtensionUpdateKind.ANIME) {
        // Update extension repo details
        updateExtensionStores()

        val extensions = if (fromAvailableExtensionList) {
            animeExtensionManager.availableExtensionsFlow.value
        } else {
            findExtensions()
        }

        val installedExtensions = AnimeExtensionLoader.loadExtensions(context)
            .filterIsInstance<AnimeLoadResult.Success>()
            .map { it.extension }

        val extensionsWithUpdate = mutableListOf<AnimeExtension.Installed>()
        val updates = mutableListOf<ExtensionUpdate>()
        for (installedExt in installedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensions.find { it.pkgName == pkgName } ?: continue

            val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
            val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
            val hasUpdate = hasUpdatedVer || hasUpdatedLib
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
                updates.add(
                    ExtensionUpdate(
                        pkgName,
                        availableExt.versionCode.toLong(),
                        availableExt.libVersion,
                        installedExt.name,
                    ),
                )
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            ExtensionUpdateNotifier(context).promptUpdates(
                updates = updates,
                anime = true,
            )
        }

        extensionsWithUpdate
    }
}
