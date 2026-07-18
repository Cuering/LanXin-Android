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
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
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
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient

/**
 * Debug 资源下载专用传输层。
 *
 * **不**复用 [com.lanxin.android.data.network.NetworkClient] 的默认短超时：
 * 本地脑 ~880MB，弱网下 connect 需 30–90s，socket 空闲需更长，request 不设整包上限
 *（靠进度回调 + 协程取消）。
 *
 * 引擎：OkHttp（Android 上 DNS/IPv4 更稳；CIO 在部分机型 IPv6 卡住会全源 Timeout）。
 *
 * 可恢复：
 * - 落盘 `*.part`；失败**保留** part，下次/重试发 `Range: bytes=N-`
 * - 服务端 206 追加；200 整包重写；416 视为已完成
 * - 超时/断连自动重试（指数退避），保留已下字节
 */
@Singleton
class KtorAssetDownloadTransport @Inject constructor() : AssetDownloadTransport {

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            // followRedirects 默认 true，兼容 modelscope / hf 302
            engine {
                preconfigured = OkHttpClient.Builder()
                    .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .writeTimeout(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .callTimeout(0, TimeUnit.MILLISECONDS) // 整包不限；靠读空闲 + 取消
                    .retryOnConnectionFailure(true)
                    .dns(Ipv4PreferDns)
                    .build()
            }
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
                lastError = enrichError(url, destFile, t)
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

        /**
         * 弱网 / 跨境建连：默认 API 超时过短会导致 modelscope/hf 全挂。
         * 90s 覆盖部分 DNS 慢 + TLS 握手。
         */
        const val CONNECT_TIMEOUT_MS: Long = 90_000L

        /**
         * 读空闲超时（两次收包间隔）。大文件靠持续进度重置计时；
         * 过短会在弱网卡顿时误杀，过长则挂死难发现。
         */
        const val SOCKET_TIMEOUT_MS: Long = 5 * 60_000L

        /**
         * 整请求不设上限（880MB 可达数十分钟）。
         *
         * **Ktor 3.5 陷阱**：`requestTimeoutMillis = 0` 会在配置时直接
         * `require(value > 0)` 抛 [IllegalArgumentException]
         *（“Only positive timeout values are allowed”），导致**所有**模型
         * 下载在第一次 HTTP 请求前就失败，且被 shortError 误标成 Timeout。
         * 必须用 [HttpTimeoutConfig.INFINITE_TIMEOUT_MS]（= Long.MAX_VALUE）。
         * 大文件靠 socket 空闲超时 + 用户取消兜底。
         */
        const val REQUEST_TIMEOUT_MS: Long = HttpTimeoutConfig.INFINITE_TIMEOUT_MS

        /** 引擎标识：单测 / 文档。 */
        const val ENGINE_NAME: String = "OkHttp"

        /** 单文件瞬时失败最大尝试次数（含首次）。 */
        const val MAX_ATTEMPTS: Int = 3

        const val RETRY_BASE_DELAY_MS: Long = 1_500L
        const val RETRY_MAX_DELAY_MS: Long = 12_000L

        fun partFile(destFile: File): File =
            File(destFile.parentFile, destFile.name + ".part")

        /**
         * 短 URL 提示：`host/…/file`，避免完整 query 刷屏。
         */
        fun urlHint(url: String): String {
            return try {
                val noQuery = url.substringBefore('?')
                val host = noQuery.substringAfter("://").substringBefore('/')
                val file = noQuery.substringAfterLast('/').ifBlank { noQuery }
                if (host.isBlank()) file else "$host/…/$file"
            } catch (_: Throwable) {
                url.take(64)
            }
        }

        /**
         * 给异常补 host/file，便于 MultiSource 聚合时区分真实网络失败。
         */
        fun enrichError(url: String, destFile: File, cause: Throwable): Throwable {
            if (cause is CancellationException) return cause
            val classified = classifyError(cause)
            val hint = urlHint(url)
            val name = destFile.name
            val msg = buildString {
                append(classified)
                if (name.isNotBlank()) append(" · ").append(name)
                append(" @ ").append(hint)
            }
            return IllegalStateException(msg, cause)
        }

        /**
         * 细分类：connect / socket / request / DNS / 配置错误 / HTTP。
         * **禁止**把 `IllegalArgumentException(…timeout…)` 压成 Timeout
         *（Ktor 3.5 requestTimeout=0 即此坑）。
         */
        fun classifyError(t: Throwable): String {
            val chain = generateSequence(t) { it.cause }.toList()
            for (e in chain) {
                when (e) {
                    is HttpRequestTimeoutException -> return "Request timeout"
                    is SocketTimeoutException -> {
                        val m = e.message.orEmpty().lowercase()
                        return if (m.contains("connect")) "Connect timeout" else "Socket timeout"
                    }
                    is UnknownHostException -> return "DNS failed"
                    is ConnectException -> return "Connect refused"
                    is java.net.NoRouteToHostException -> return "No route"
                    is java.net.PortUnreachableException -> return "Port unreachable"
                    is SocketException -> {
                        val m = e.message.orEmpty().lowercase()
                        return when {
                            m.contains("timeout") && m.contains("connect") -> "Connect timeout"
                            m.contains("timeout") -> "Socket timeout"
                            m.contains("reset") -> "Connection reset"
                            m.contains("unreachable") -> "Network unreachable"
                            m.contains("refused") -> "Connect refused"
                            else -> "Socket error"
                        }
                    }
                    is IllegalArgumentException -> {
                        val m = e.message.orEmpty()
                        // Ktor 3.5：requestTimeout=0 → “Only positive timeout values are allowed”
                        if (m.contains("positive timeout", ignoreCase = true) ||
                            m.contains("Only positive", ignoreCase = true)
                        ) {
                            return "Bad timeout config"
                        }
                        if (m.contains("timeout", ignoreCase = true)) {
                            return "Config error"
                        }
                    }
                }
            }
            val raw = (t.message ?: t.javaClass.simpleName)
            val lower = raw.lowercase()
            return when {
                lower.contains("connect timeout") -> "Connect timeout"
                lower.contains("socket timeout") || lower.contains("read timed out") ->
                    "Socket timeout"
                lower.contains("request timeout") -> "Request timeout"
                lower.contains("timeout has expired") && lower.contains("connect") ->
                    "Connect timeout"
                lower.contains("timeout has expired") -> "Timeout"
                lower.contains("unknownhost") || lower.contains("unable to resolve") ->
                    "DNS failed"
                lower.contains("failed to connect") -> "Connect failed"
                lower.startsWith("下载失败 http") || Regex("""HTTP\s+\d{3}""").containsMatchIn(raw) ->
                    raw.take(40)
                else -> raw.take(80)
            }
        }

        /**
         * 可重试：超时、连接重置、短暂 IO；HTTP 4xx（除 408/429）不重试；
         * 配置错误（Bad timeout config）不重试。
         */
        fun isRetryable(t: Throwable): Boolean {
            val chain = generateSequence(t) { it.cause }.toList()
            for (e in chain) {
                when (e) {
                    is IllegalArgumentException -> {
                        val m = e.message.orEmpty().lowercase()
                        if (m.contains("positive timeout") || m.contains("only positive")) {
                            return false
                        }
                    }
                    is HttpRequestTimeoutException -> return true
                    is SocketTimeoutException -> return true
                    is UnknownHostException -> return true
                    is ConnectException -> return true
                    is SocketException -> return true
                    is java.io.IOException -> {
                        val m = e.message.orEmpty().lowercase()
                        if (m.contains("timeout") ||
                            m.contains("connection reset") ||
                            m.contains("broken pipe") ||
                            m.contains("connection refused") ||
                            m.contains("network is unreachable") ||
                            m.contains("failed to connect")
                        ) {
                            return true
                        }
                    }
                }
            }
            val msg = (t.message ?: "").lowercase()
            if (msg.contains("bad timeout config") || msg.contains("config error")) {
                return false
            }
            if (msg.contains("timeout") ||
                msg.contains("dns failed") ||
                msg.contains("connect refused") ||
                msg.contains("connect failed") ||
                msg.contains("network unreachable") ||
                msg.contains("connection reset")
            ) {
                return true
            }
            // HTTP 408 / 429 / 5xx
            val http = Regex("""HTTP\s+(\d{3})""").find(t.message.orEmpty())
            val code = http?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (code != null) {
                return code == 408 || code == 429 || code in 500..599
            }
            return false
        }
    }

    /**
     * DNS 优先 IPv4：部分运营商/机型 AAAA 先返回后卡住，直到 connect 超时。
     */
    private object Ipv4PreferDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.size <= 1) return addresses
            return addresses.sortedBy { addr -> if (addr is Inet4Address) 0 else 1 }
        }
    }
}
