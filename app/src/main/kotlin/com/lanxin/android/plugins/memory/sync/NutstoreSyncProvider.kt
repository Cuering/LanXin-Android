package com.lanxin.android.plugins.memory.sync

import android.util.Log
import okhttp3.Credentials
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 同步提供者接口。
 */
interface SyncProvider {
    suspend fun push(data: Map<String, ByteArray>): Result<Unit>
    suspend fun pull(): Result<Map<String, ByteArray>>
    suspend fun testConnection(): Result<String>
}

/**
 * 坚果云 WebDAV 同步提供者。
 * 默认地址：https://dav.jianguoyun.com/dav/lanxin/
 */
class NutstoreSyncProvider(
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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun authHeader(): String = Credentials.basic(username, appPassword)

    private fun ensureTrailingSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"

    private fun ensureDirectory() {
        val base = ensureTrailingSlash(webDavUrl)
        val request = Request.Builder()
            .url(base)
            .method("MKCOL", null)
            .header("Authorization", authHeader())
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code != 201 && response.code != 405 && response.code != 409) {
                    Log.w(TAG, "MKCOL returned ${response.code}")
                }
            }
        } catch (_: Exception) {
            // directory may already exist
        }
    }

    override suspend fun push(data: Map<String, ByteArray>): Result<Unit> {
        return try {
            ensureDirectory()
            val base = ensureTrailingSlash(webDavUrl)
            for ((filename, bytes) in data) {
                val url = base + filename.trimStart('/')
                val mediaType = if (filename.endsWith(".db")) MEDIA_TYPE_OCTET else MEDIA_TYPE_TEXT
                val body = bytes.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(url)
                    .put(body)
                    .header("Authorization", authHeader())
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return Result.failure(IllegalStateException("PUT $filename failed: ${response.code}"))
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pull(): Result<Map<String, ByteArray>> {
        return try {
            val base = ensureTrailingSlash(webDavUrl)
            val names = listOf("memory_export.json", "evolution_index.md")
            val out = linkedMapOf<String, ByteArray>()
            for (name in names) {
                val request = Request.Builder()
                    .url(base + name)
                    .get()
                    .header("Authorization", authHeader())
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null) out[name] = bytes
                    }
                }
            }
            Result.success(out)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<String> {
        return try {
            val base = ensureTrailingSlash(webDavUrl)
            val request = Request.Builder()
                .url(base)
                .method("PROPFIND", ByteArray(0).toRequestBody(MEDIA_TYPE_TEXT))
                .header("Authorization", authHeader())
                .header("Depth", "0")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 207) {
                    Result.success("ok ${response.code}")
                } else {
                    Result.failure(IllegalStateException("PROPFIND failed: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
