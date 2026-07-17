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
import com.lanxin.android.data.network.NetworkClient
import io.ktor.client.HttpClient
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
 * 基于 [NetworkClient] / Ktor 的 Debug 资源下载传输层。
 */
@Singleton
class KtorAssetDownloadTransport @Inject constructor(
    private val networkClient: NetworkClient
) : AssetDownloadTransport {

    private val httpClient: HttpClient get() = networkClient()

    override suspend fun downloadToFile(
        url: String,
        destFile: File,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        destFile.parentFile?.mkdirs()
        if (destFile.exists()) destFile.delete()
        val tmp = File(destFile.parentFile, destFile.name + ".part")
        if (tmp.exists()) tmp.delete()

        httpClient.prepareGet(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "*/*")
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
        private const val DEFAULT_BUFFER = 16 * 1024
        private const val USER_AGENT = "LanXin-Android-DebugAssetDownloader"
    }
}
