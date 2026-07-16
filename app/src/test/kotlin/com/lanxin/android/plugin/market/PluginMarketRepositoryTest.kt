package com.lanxin.android.plugin.market

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginMarketRepositoryTest {

    @Test
    fun `sample repository returns three plugins`() = runBlocking {
        val repo = SamplePluginMarketRepository()
        val catalog = repo.fetchCatalog().getOrThrow()
        assertEquals(3, catalog.plugins.size)
        val search = repo.search("hello").getOrThrow()
        assertEquals(1, search.size)
        assertEquals("example.hello", search[0].id)
    }

    @Test
    fun `remote repository parses fetcher body`() = runBlocking {
        val body = MarketCatalogParser.encode(SampleMarketCatalog.catalog)
        val fetcher = object : MarketHttpFetcher {
            override suspend fun getText(url: String) = Result.success(body)
            override suspend fun downloadToFile(
                url: String,
                destFile: File,
                onProgress: (Float) -> Unit
            ) = Result.failure(UnsupportedOperationException())
        }
        val repo = RemotePluginMarketRepository(
            catalogUrlProvider = { "https://example.invalid/index.json" },
            fetcher = fetcher
        )
        val catalog = repo.fetchCatalog().getOrThrow()
        assertEquals(3, catalog.plugins.size)
    }

    @Test
    fun `composite falls back to sample`() = runBlocking {
        val failing = object : PluginMarketRepository {
            override suspend fun fetchCatalog() =
                Result.failure(IllegalStateException("down"))
            override suspend fun search(query: String) = fetchCatalog().map { emptyList() }
        }
        val composite = CompositePluginMarketRepository(
            remote = failing,
            sample = SamplePluginMarketRepository(),
            fallbackToSample = true
        )
        val catalog = composite.fetchCatalog().getOrThrow()
        assertEquals(3, catalog.plugins.size)
        assertEquals("sample", composite.lastSource)
    }

    @Test
    fun `composite remote success`() = runBlocking {
        val remote = SamplePluginMarketRepository()
        val composite = CompositePluginMarketRepository(
            remote = remote,
            fallbackToSample = true
        )
        assertTrue(composite.fetchCatalog().isSuccess)
        assertEquals("remote", composite.lastSource)
    }
}
