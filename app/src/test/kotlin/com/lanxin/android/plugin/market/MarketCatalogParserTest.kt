package com.lanxin.android.plugin.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketCatalogParserTest {

    @Test
    fun `parse sample catalog`() {
        val json = """
            {
              "schema_version": 1,
              "updated_at": "2026-07-16T00:00:00Z",
              "plugins": [
                {
                  "id": "example.hello",
                  "name": "Hello",
                  "version": "1.0.0",
                  "download_url": "https://example.invalid/a.apk",
                  "description": "d",
                  "author": "a",
                  "min_app_version": "0.7.0",
                  "checksum": "abc",
                  "size": 12
                }
              ]
            }
        """.trimIndent()
        val catalog = MarketCatalogParser.parse(json)
        assertEquals(1, catalog.schemaVersion)
        assertEquals(1, catalog.plugins.size)
        val p = catalog.plugins.single()
        assertEquals("example.hello", p.id)
        assertEquals("https://example.invalid/a.apk", p.downloadUrl)
        assertEquals(12L, p.size)
        assertEquals("abc", p.checksum)
    }

    @Test
    fun `parse ignores unknown keys`() {
        val json = """{"schema_version":2,"extra":true,"plugins":[]}"""
        val catalog = MarketCatalogParser.parse(json)
        assertEquals(2, catalog.schemaVersion)
        assertTrue(catalog.plugins.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty text throws`() {
        MarketCatalogParser.parse("   ")
    }

    @Test
    fun `encode roundtrip`() {
        val original = SampleMarketCatalog.catalog
        val text = MarketCatalogParser.encode(original)
        val again = MarketCatalogParser.parse(text)
        assertEquals(original.plugins.size, again.plugins.size)
        assertEquals(original.plugins[0].id, again.plugins[0].id)
    }
}
