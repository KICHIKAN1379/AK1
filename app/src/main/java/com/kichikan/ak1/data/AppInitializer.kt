package com.kichikan.ak1.data

import android.content.Context
import com.kichikan.ak1.domain.service.AssetArt
import com.kichikan.ak1.domain.service.RankArt

/**
 * Coordinates first-run setup and bundled catalog synchronization after persisted state is loaded,
 * but before any Activity or Compose destination is created.
 */
internal class AppInitializer(
    catalogPort: CatalogBootstrapperPort,
    private val startupPort: AppInitializationPort
) {
    private val appContext: Context = catalogPort.appContext
    private val catalog = CatalogBootstrapper(catalogPort)

    fun initialize() {
        val catalogPrefs = appContext.getSharedPreferences("ak1_settings", Context.MODE_PRIVATE)
        if (!catalogPrefs.getBoolean("starter_catalog_created", false)) {
            catalog.seedStarterCatalog(regenerate = false)
            catalogPrefs.edit()
                .putBoolean("starter_catalog_created", true)
                .putInt("starter_art_version", AssetArt.ART_VERSION)
                .apply()
        } else if (catalogPrefs.getInt("starter_art_version", 0) < AssetArt.ART_VERSION) {
            catalog.seedStarterCatalog(regenerate = true)
            catalog.regenerateLevelAvatars()
            catalogPrefs.edit()
                .putInt("starter_art_version", AssetArt.ART_VERSION)
                .apply()
        }

        // Preserve the established startup order and each task's existing idempotency marker.
        runCatching { catalog.syncProfilePack() }
        runCatching { startupPort.seedDefaultHiddenMissions() }
        runCatching { RankArt.ensure(appContext) }
        runCatching { catalog.syncRankPack() }
        runCatching { catalog.syncLevelBadgeOwnership() }
        runCatching { catalog.syncGroupPack() }
        runCatching { catalog.upgradeStarterLooks() }
        runCatching { catalog.migrateNoFreeLooks() }

        startupPort.persist()

        // Both catch-up operations are idempotent and used to run at repository construction.
        runCatching { startupPort.processBirthdayRewards() }
        runCatching { startupPort.evaluateAssistantAlerts() }
    }
}
