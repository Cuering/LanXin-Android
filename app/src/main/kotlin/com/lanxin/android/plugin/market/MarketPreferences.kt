package com.lanxin.android.plugin.market

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 插件市场配置（DataStore）。
 *
 * 键前缀与 sync_ 命名空间隔离。
 */
@Singleton
class MarketPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private val catalogUrlKey = stringPreferencesKey(MarketDefaults.PREF_CATALOG_URL)

    suspend fun getCatalogUrl(): String {
        val stored = dataStore.data.map { it[catalogUrlKey] }.first()
        return if (stored.isNullOrBlank()) {
            MarketDefaults.DEFAULT_CATALOG_URL
        } else {
            stored.trim()
        }
    }

    suspend fun setCatalogUrl(url: String?) {
        dataStore.edit { prefs ->
            if (url.isNullOrBlank()) {
                prefs.remove(catalogUrlKey)
            } else {
                prefs[catalogUrlKey] = url.trim()
            }
        }
    }

    suspend fun getConfig(): MarketConfig = MarketConfig(
        catalogUrl = getCatalogUrl(),
        fallbackToSample = true
    )
}
