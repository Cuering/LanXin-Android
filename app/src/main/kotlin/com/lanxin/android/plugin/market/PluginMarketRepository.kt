package com.lanxin.android.plugin.market

/**
 * 插件市场数据源（可插拔）。
 *
 * 实现可来自远程 JSON、内置 sample、本地文件等。
 */
interface PluginMarketRepository {

    /** 拉取完整目录。失败时抛异常或由实现包装为 Result。 */
    suspend fun fetchCatalog(): Result<MarketCatalog>

    /**
     * 按关键字过滤（id / name / description / author，忽略大小写）。
     * 空查询返回全部。
     */
    suspend fun search(query: String): Result<List<MarketPluginEntry>>
}

/** 内置 sample 目录，供单测与离线演示。 */
object SampleMarketCatalog {

    val catalog: MarketCatalog = MarketCatalog(
        schemaVersion = 1,
        updatedAt = "2026-07-16T00:00:00Z",
        plugins = listOf(
            MarketPluginEntry(
                id = "example.hello",
                name = "Hello Plugin",
                version = "1.0.0",
                description = "示例外部插件（sample 索引，无真实下载包）",
                author = "LanXin",
                downloadUrl = "https://example.invalid/plugins/hello-1.0.0.apk",
                minAppVersion = "0.7.0",
                checksum = "",
                size = 0L
            ),
            MarketPluginEntry(
                id = "example.tools",
                name = "Tools Pack",
                version = "0.2.1",
                description = "示例工具包插件（sample）",
                author = "LanXin",
                downloadUrl = "https://example.invalid/plugins/tools-0.2.1.apk",
                minAppVersion = "0.7.0",
                checksum = "",
                size = 1024L
            ),
            MarketPluginEntry(
                id = "example.notes",
                name = "Notes Helper",
                version = "2.0.0",
                description = "笔记辅助示例插件",
                author = "community",
                downloadUrl = "https://example.invalid/plugins/notes-2.0.0.apk",
                minAppVersion = "0.7.5",
                checksum = "",
                size = 0L
            )
        )
    )
}

/** 始终返回 [SampleMarketCatalog] 的数据源。 */
class SamplePluginMarketRepository : PluginMarketRepository {

    override suspend fun fetchCatalog(): Result<MarketCatalog> =
        Result.success(SampleMarketCatalog.catalog)

    override suspend fun search(query: String): Result<List<MarketPluginEntry>> {
        val all = SampleMarketCatalog.catalog.plugins
        if (query.isBlank()) return Result.success(all)
        val q = query.trim().lowercase()
        return Result.success(
            all.filter { entry ->
                entry.id.lowercase().contains(q) ||
                    entry.name.lowercase().contains(q) ||
                    entry.description.lowercase().contains(q) ||
                    entry.author.lowercase().contains(q)
            }
        )
    }
}

/**
 * 远程 JSON 索引数据源。
 *
 * [fetcher] 可注入以便单测；默认由 Hilt 提供 Ktor 实现。
 */
class RemotePluginMarketRepository(
    private val catalogUrlProvider: suspend () -> String,
    private val fetcher: MarketHttpFetcher
) : PluginMarketRepository {

    override suspend fun fetchCatalog(): Result<MarketCatalog> {
        return try {
            val url = catalogUrlProvider().trim()
            if (url.isBlank()) {
                return Result.failure(IllegalStateException("市场索引 URL 为空"))
            }
            val body = fetcher.getText(url).getOrElse { return Result.failure(it) }
            val catalog = MarketCatalogParser.parse(body)
            Result.success(catalog)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun search(query: String): Result<List<MarketPluginEntry>> {
        val catalog = fetchCatalog().getOrElse { return Result.failure(it) }
        if (query.isBlank()) return Result.success(catalog.plugins)
        val q = query.trim().lowercase()
        return Result.success(
            catalog.plugins.filter { entry ->
                entry.id.lowercase().contains(q) ||
                    entry.name.lowercase().contains(q) ||
                    entry.description.lowercase().contains(q) ||
                    entry.author.lowercase().contains(q)
            }
        )
    }
}

/**
 * 远程优先，失败时可选回退 sample。
 */
class CompositePluginMarketRepository(
    private val remote: PluginMarketRepository,
    private val sample: PluginMarketRepository = SamplePluginMarketRepository(),
    private val fallbackToSample: Boolean = true
) : PluginMarketRepository {

    @Volatile
    var lastSource: String = "unknown"
        private set

    override suspend fun fetchCatalog(): Result<MarketCatalog> {
        val remoteResult = remote.fetchCatalog()
        if (remoteResult.isSuccess) {
            lastSource = "remote"
            return remoteResult
        }
        if (fallbackToSample) {
            lastSource = "sample"
            return sample.fetchCatalog()
        }
        return remoteResult
    }

    override suspend fun search(query: String): Result<List<MarketPluginEntry>> {
        val catalog = fetchCatalog().getOrElse { return Result.failure(it) }
        if (query.isBlank()) return Result.success(catalog.plugins)
        val q = query.trim().lowercase()
        return Result.success(
            catalog.plugins.filter { entry ->
                entry.id.lowercase().contains(q) ||
                    entry.name.lowercase().contains(q) ||
                    entry.description.lowercase().contains(q) ||
                    entry.author.lowercase().contains(q)
            }
        )
    }
}
