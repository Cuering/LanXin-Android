package com.lanxin.android.plugins.memory.sync

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 同步提供者接口。
 *
 * push/pull 分别用于上传和下载记忆数据（memory.db + judgment_packs + evolution_index）。
 */
interface SyncProvider {
    suspend fun push(data: Map<String, ByteArray>): Result<Unit>
    suspend fun pull(): Result<Map<String, ByteArray>>
}

/**
 * 坚果云 WebDAV 同步提供者。
 *
 * 使用 OkHttp 通过 PROPFIND/PUT/GET 与 WebDAV 服务器交互。
 * 默认地址：https://dav.jianguoyun.com/dav/lanxin/
 */
@Singleton
class NutstoreSyncProvider @Inject constructor(
    private val webDavUrl: String,
    private val username: String,
    private val appPassword: String
) : SyncProvider {

    companion object {
        const val DEFAULT_URL = "https://dav.jianguoyun.com/dav/lanxin/"
        private const val TAG = "NutstoreSync"
        private val MEDIA_TYPE_OCTET: MediaType = "application/octet-stream".toMediaType()
        private val MEDIA_TYPE_TEXT: MediaType = "text/plain; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /** 确保远程目录存在 */
    private suspend fun ensureDirectory() {
        val request = Request.Builder()
            .url(webDavUrl)
            .method("MKCOL", null)
            .header("Authorization", basicAuth())
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code != 201 && response.code != 409) {
                    Log.w(TAG, "MKCOL returned ${response.code}")
                }
            }
        } catch (_: Exception) {
            // 目录可能已存在
        }
    }

    override suspend fun push(data: Map<String, ByteArray>): Result<Unit> {
        return try {
            ensureDirectory()
            for ((filename, bytes) in data) {
                val url = "$webDavUrl$filename"
                val mediaType = if (filename.endsWith(".db")) MEDIA_TYPE_OCTET else MEDIA_TYPE_TEXT
                val body = bytes.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(url)
                    .put(body)
                    .header("Authorization", basicAuth())
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Push failed for $filename: ${response.code}")
                        return Result.failure(IOException("Push failed: ${response.code}"))
                    }
                }
            }
            Log.i(TAG, "Pushed ${data.size} files to Nutstore")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Push error", e)
            Result.failure(e)
        }
    }

    override suspend fun pull(): Result<Map<String, ByteArray>> {
        return try {
            val result = mutableMapOf<String, ByteArray>()
            val listRequest = Request.Builder()
                .url(webDavUrl)
                .method("PROPFIND", null)
                .header("Authorization", basicAuth())
                .header("Depth", "1")
                .build()
            client.newCall(listRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("PROPFIND failed: ${response.code}"))
                }
                val source = response.body?.source()
                if (source != null) {
                    parsePropfindResponse(source, result)
                }
            }
            // 下载每个文件
            for ((filename, _) in result.toList()) {
                val fileUrl = "$webDavUrl$filename"
                val fileRequest = Request.Builder()
                    .url(fileUrl)
                    .get()
                    .header("Authorization", basicAuth())
                    .build()
                client.newCall(fileRequest).execute().use { fileResp ->
                    if (fileResp.isSuccessful) {
                        result[filename] = fileResp.body?.bytes() ?: byteArrayOf()
                        Log.d(TAG, "Pulled $filename (${result[filename]?.size ?: 0} bytes)")
                    }
                }
            }
            Log.i(TAG, "Pulled ${result.size} files from Nutstore")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Pull error", e)
            Result.failure(e)
        }
    }

    /** 测试连接 */
    suspend fun testConnection(): Result<Unit> {
        return try {
            val request = Request.Builder()
                .url(webDavUrl)
                .method("PROPFIND", null)
                .header("Authorization", basicAuth())
                .header("Depth", "0")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 207) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun basicAuth(): String {
        val credentials = "$username:$appPassword"
        return "Basic " + android.util.Base64.encodeToString(
            credentials.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        ).trim()
    }

    /** 简单解析 WebDAV PROPFIND 响应中的文件列表 */
    private fun parsePropfindResponse(source: BufferedSource, result: MutableMap<String, ByteArray>) {
        val xml = source.readString(Charsets.UTF_8)
        // 提取 href 属性中的文件名
        val hrefPattern = Regex("""href="([^"]+)"""")
        hrefPattern.findAll(xml).forEach { match ->
            val href = match.groupValues[1]
            val filename = href.split('/').lastOrNull()?.takeIf { it.isNotBlank() }
            if (!filename.isNullOrEmpty() && filename != "." && filename != "..") {
                result[filename] = byteArrayOf() // 占位，实际下载在 pull() 中完成
            }
        }
    }
}
