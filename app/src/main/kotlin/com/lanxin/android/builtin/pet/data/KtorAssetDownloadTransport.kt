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
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Debug 资源下载专用传输层。
 *
 * **不**复用 [com.lanxin.android.data.network.NetworkClient] 的默认短超时：
 * 本地脑 ~880MB，弱网下 connect 需 30–60s，socket 空闲需更长，request 不设整包上限
 *（靠进度回调 + 协程取消）。
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
        if (destFile.exists()) destFile.delete()
        val tmp = File(destFile.parentFile, destFile.name + ".part")
        if (tmp.exists()) tmp.delete()

        try {
            httpClient.prepareGet(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "*/*")
                // per-request 再写一遍，避免被其它插件覆盖
                timeout {
                    connectTimeoutMillis = CONNECT_TIMEOUT_MS
                    socketTimeoutMillis = SOCKET_TIMEOUT_MS
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    throw IllegalStateException(
                        "下载失败 HTTP ${response.status.value}"
                    )
                }
                val total = response.contentLength() ?: -1L
                val channel: ByteReadChannel = response.bodyAsChannel()
                var downloaded = 0L
                val buffer = ByteArray(DEFAULT_BUFFER)
                FileOutputStream(tmp).use { out ->
                    while (!channel.isClosedForRead) {
                        ensureActive()
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }

            if (!tmp.renameTo(destFile)) {
                tmp.copyTo(destFile, overwrite = true)
                tmp.delete()
            }
            if (!destFile.isFile || destFile.length() <= 0L) {
                throw IllegalStateException("下载文件为空")
            }
        } catch (t: Throwable) {
            tmp.delete()
            destFile.delete()
            throw t
        }
    }

    override suspend fun openStream(url: String): InputStream = withContext(Dispatchers.IO) {
        val tmp = File.createTempFile("debug-asset-", ".bin")
        try {
            downloadToFile(url, tmp) { _, _ -> }
            // 调用方负责关闭；关闭后删临时文件
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
         * （3.5 无 `HttpTimeout.INFINITE_TIMEOUT_MS` 常量）
         */
        const val REQUEST_TIMEOUT_MS: Long = 0L
    }
}
