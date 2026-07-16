package com.lanxin.android.plugin.market

import kotlinx.serialization.json.Json

/**
 * 市场索引 JSON 解析（纯 JVM 可测）。
 */
object MarketCatalogParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun parse(text: String): MarketCatalog {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("市场索引为空")
        }
        return json.decodeFromString(MarketCatalog.serializer(), trimmed)
    }

    fun encode(catalog: MarketCatalog): String =
        json.encodeToString(MarketCatalog.serializer(), catalog)
}
