package com.lanxin.android.plugin.market

/**
 * 市场配置读写（可替换，便于单测）。
 *
 * 默认实现 [MarketPreferences]（DataStore）。
 */
interface MarketSettings {

    suspend fun getCatalogUrl(): String

    suspend fun setCatalogUrl(url: String?)

    suspend fun getConfig(): MarketConfig =
        MarketConfig(catalogUrl = getCatalogUrl(), fallbackToSample = true)
}
