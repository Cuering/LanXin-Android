/*
 * Copyright 2025 LanXin Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lanxin.android.builtin.pet.data

import com.lanxin.android.builtin.pet.domain.AssetDownloadTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Debug 资源下载专用传输层。
 *
 * **不**复用 [com.lanxin.android.data.network.NetworkClient] 的默认短超时：
 * 本地脑 ~880MB，弱网下 connect 需 30–60s，socket 空闲需更长，request 不设整包上限
 *（靠进度回调 + 协程取消）。
 *
 * 可恢复：
 * - 落盘 `*.part`；失败**保留** part，下次/重试发 `Range: bytes=N-`
 * - 服务端 206 追加；200 整包重写；416 视为已完成
 * - 超时/断连自动重试（指数退避），保留已下字节
 */
@Singleton
class KtorAssetDownloadTransport @Inject constructor() : AssetDownloadTransport {

    private val httpClient: HttpClient by lazy {
        HttpClient(CIO) {
            expectSuccess = false
            // followRedirects 默认 true，兼容 modelscope / hf 302
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = SOCKET_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }
    }

    override suspend fun downloadToFile(
        url: String,
        destFile: File,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        destFile.parentFile?.mkdirs()
        // 完整 dest 已就绪则直接返回（多文件 staging 跳过已下完的）
        if (destFile.isFile && destFile.length() > 0L && !partFile(destFile).exists()) {
            onProgress(destFile.length(), destFile.length())
            return@withContext
        }
        if (destFile.exists()) destFile.delete()

        val tmp = partFile(destFile)
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            try {
                downloadOnce(url, destFile, tmp, onProgress)
                return@withContext
            } catch (ce: CancellationException) {
                // 用户取消：保留 part，便于再次点下载续传
                throw ce
            } catch (t: Throwable) {
                lastError = t
                if (!isRetryable(t) || attempt >= MAX_ATTEMPTS) break
                val backoff = RETRY_BASE_DELAY_MS * (1L shl (attempt - 1).coerceAtMost(3))
                delay(backoff.coerceAtMost(RETRY_MAX_DELAY_MS))
            }
        }
        // 失败保留 .part，不删；仅清可能半写的 dest
        destFile.delete()
        throw lastError ?: IllegalStateException("下载失败")
    }

    private suspend fun downloadOnce(
        url: String,
        destFile: File,
        tmp: File,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit
    ) {
        val existing = if (tmp.isFile && tmp.length() > 0L) tmp.length() else 0L

        httpClient.prepareGet(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "*/*")
            if (existing > 0L) {
                header(HttpHeaders.Range, "bytes=$existing-")
            }
            timeout {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = SOCKET_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }.execute { response ->
            val code = response.status.value
            when {
                code == HttpStatusCode.PartialContent.value -> {
                    // 206：追加
                    val contentLen = response.contentLength()
                    val total = if (contentLen != null && contentLen >= 0L) {
                        existing + contentLen
                    } else {
                        -1L
                    }
                    appendChannel(response.bodyAsChannel(), tmp, existing, total, onProgress)
                }
                response.status.isSuccess() -> {
                    // 200：服务端忽略 Range 或首次全量；整包重写
                    if (tmp.exists()) tmp.delete()
                    val total = response.contentLength() ?: -1L
                    writeChannel(response.bodyAsChannel(), tmp, total, onProgress)
                }
                code == HttpStatusCode.RequestedRangeNotSatisfiable.value -> {
                    // 416：本地 part 可能已完整
                    if (tmp.isFile && tmp.length() > 0L) {
                        onProgress(tmp.length(), tmp.length())
                    } else {
                        throw IllegalStateException("下载失败 HTTP 416")
                    }
                }
                else -> throw IllegalStateException("下载失败 HTTP $code")
            }
        }

        if (!tmp.isFile || tmp.length() <= 0L) {
            throw IllegalStateException("下载文件为空")
        }
        if (destFile.exists()) destFile.delete()
        if (!tmp.renameTo(destFile)) {
            tmp.copyTo(destFile, overwrite = true)
            tmp.delete()
        }
        if (!destFile.isFile || destFile.length() <= 0L) {
            throw IllegalStateException("下载文件为空")
        }
    }

    private suspend fun writeChannel(
        channel: ByteReadChannel,
        tmp: File,
        total: Long,
        onProgress: suspend (Long, Long) -> Unit
    ) {
        var downloaded = 0L
        val buffer = ByteArray(DEFAULT_BUFFER)
        FileOutputStream(tmp, false).use { out ->
            while (!channel.isClosedForRead) {
                currentCoroutineContext().ensureActive()
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) break
                out.write(buffer, 0, read)
                downloaded += read
                onProgress(downloaded, total)
            }
        }
    }

    private suspend fun appendChannel(
        channel: ByteReadChannel,
        tmp: File,
        existing: Long,
        total: Long,
        onProgress: suspend (Long, Long) -> Unit
    ) {
        var downloaded = existing
        val buffer = ByteArray(DEFAULT_BUFFER)
        FileOutputStream(tmp, true).use { out ->
            while (!channel.isClosedForRead) {
                currentCoroutineContext().ensureActive()
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) break
                out.write(buffer, 0, read)
                downloaded += read
                onProgress(downloaded, total)
            }
        }
    }

    override suspend fun openStream(url: String): InputStream = withContext(Dispatchers.IO) {
        val tmp = File.createTempFile("debug-asset-", ".bin")
        try {
            downloadToFile(url, tmp) { _, _ -> }
            object : FileInputStream(tmp) {
                override fun close() {
                    super.close()
                    tmp.delete()
                }
            }
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
    }

    companion object {
        private const val DEFAULT_BUFFER = 64 * 1024
        private const val USER_AGENT = "LanXin-Android-DebugAssetDownloader"

        /** 弱网 / 跨境建连：默认 API 超时过短会导致 modelscope/hf 全挂。 */
        const val CONNECT_TIMEOUT_MS: Long = 60_000L

        /**
         * 读空闲超时（两次收包间隔）。大文件靠持续进度重置计时；
         * 过短会在弱网卡顿时误杀，过长则挂死难发现。
         */
        const val SOCKET_TIMEOUT_MS: Long = 5 * 60_000L

        /**
         * 整请求不设上限（880MB 可达数十分钟）。
         * Ktor 3.x：`0` = 禁用 request timeout；用 socket 空闲 + 用户取消兜底。
         */
        const val REQUEST_TIMEOUT_MS: Long = 0L

        /** 单文件瞬时失败最大尝试次数（含首次）。 */
        const val MAX_ATTEMPTS: Int = 3

        const val RETRY_BASE_DELAY_MS: Long = 1_500L
        const val RETRY_MAX_DELAY_MS: Long = 12_000L

        fun partFile(destFile: File): File =
            File(destFile.parentFile, destFile.name + ".part")

        /**
         * 可重试：超时、连接重置、短暂 IO；HTTP 4xx（除 408/429）不重试。
         */
        fun isRetryable(t: Throwable): Boolean {
            val chain = generateSequence(t) { it.cause }.toList()
            for (e in chain) {
                when (e) {
                    is SocketTimeoutException -> return true
                    is SocketException -> return true
                    is java.io.IOException -> {
                        val m = e.message.orEmpty().lowercase()
                        if (m.contains("timeout") ||
                            m.contains("connection reset") ||
                            m.contains("broken pipe") ||
                            m.contains("connection refused") ||
                            m.contains("network is unreachable")
                        ) {
                            return true
                        }
                    }
                }
            }
            val msg = (t.message ?: "").lowercase()
            if (msg.contains("timeout")) return true
            // HTTP 408 / 429 / 5xx
            val http = Regex("""HTTP\s+(\d{3})""").find(t.message.orEmpty())
            val code = http?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (code != null) {
                return code == 408 || code == 429 || code in 500..599
            }
            return false
        }
    }
}
