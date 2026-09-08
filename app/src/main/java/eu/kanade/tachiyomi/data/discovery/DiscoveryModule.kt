package eu.kanade.tachiyomi.data.discovery

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import eu.kanade.tachiyomi.network.NetworkHelper
import tachiyomi.data.discovery.CachedAnimeCatalogRepository
import tachiyomi.data.discovery.DiscoveryDatabase
import tachiyomi.data.discovery.SqlDiscoveryStore
import tachiyomi.domain.discovery.AnimeCatalogCache
import tachiyomi.domain.discovery.AnimeCatalogRemote
import tachiyomi.domain.discovery.AnimeCatalogRepository
import tachiyomi.domain.discovery.AnimeSourceLinkRepository
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class DiscoveryModule(private val app: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory {
            DiscoveryDatabase(AndroidSqliteDriver(DiscoveryDatabase.Schema, app, "discovery.db"))
        }
        addSingletonFactory { SqlDiscoveryStore(get()) }
        addSingletonFactory<AnimeCatalogCache> { PrivateDiscoveryCache(get<SqlDiscoveryStore>(), get()) }
        addSingletonFactory<AnimeSourceLinkRepository> { get<SqlDiscoveryStore>() }
        addSingletonFactory<AnimeCatalogRemote> {
            val client = get<NetworkHelper>().apiClient
            tachiyomi.domain.discovery.FailoverAnimeCatalogRemote(
                AnilistCatalogRemote(client, get()),
                KitsuCatalogRemote(client, get()),
            )
        }
        addSingletonFactory<AnimeCatalogRepository> { CachedAnimeCatalogRepository(get(), get(), get()) }
        addSingletonFactory {
            DiscoverySourceService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
        }
        addSingletonFactory { DiscoveryPlaybackService(get(), get(), get(), get()) }
        addSingletonFactory { tachiyomi.domain.discovery.CatalogSeriesEvidence(get()) }
        addSingletonFactory { SmartSourceResolver(get(), get(), get(), get(), get(), get(), get()) }
        addSingletonFactory {
            fun provider(resume: Boolean) = LocalHomeSectionProvider(
                get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), resume,
            )
            LocalHomeSections(provider(true), provider(false))
        }
    }
}
