package com.lanxin.android.plugin.market

import com.lanxin.android.data.network.NetworkClient
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 市场 HTTP 抽象（文本索引 + 文件下载），便于单测注入 Fake。
 */
interface MarketHttpFetcher {

    /** GET 文本（JSON 索引）。 */
    suspend fun getText(url: String): Result<String>

    /**
     * 下载到 [destFile]，可选进度回调（0f..1f；未知总长时可能反复 0）。
     */
    suspend fun downloadToFile(
        url: String,
        destFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<Long>
}

/**
 * 基于 [NetworkClient] 的 Ktor 实现。
 */
@Singleton
class KtorMarketHttpFetcher @Inject constructor(
    private val networkClient: NetworkClient
) : MarketHttpFetcher {

    private val client get() = networkClient()

    override suspend fun getText(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = client.get(url)
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                Result.failure(
                    IllegalStateException("HTTP ${response.status.value}: ${text.take(200)}")
                )
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadToFile(
        url: String,
        destFile: File,
        onProgress: (Float) -> Unit
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            destFile.parentFile?.mkdirs()
            client.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) {
                    val err = runCatching { response.bodyAsText() }.getOrDefault("")
                    return@execute Result.failure(
                        IllegalStateException(
                            "HTTP ${response.status.value}: ${err.take(200)}"
                        )
                    )
                }
                val contentLength = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
                val channel: ByteReadChannel = response.bodyAsChannel()
                var written = 0L
                FileOutputStream(destFile).use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER)
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read < 0) break
                        if (read == 0) continue
                        out.write(buffer, 0, read)
                        written += read
                        if (contentLength > 0L) {
                            onProgress((written.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f))
                        } else {
                            onProgress(0f)
                        }
                    }
                    out.flush()
                }
                onProgress(1f)
                Result.success(written)
            }
        } catch (e: Exception) {
            runCatching { destFile.delete() }
            Result.failure(e)
        }
    }

    companion object {
        private const val DEFAULT_BUFFER = 8 * 1024
    }
}
