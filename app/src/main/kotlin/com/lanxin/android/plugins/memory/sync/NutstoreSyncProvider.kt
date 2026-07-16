package com.lanxin.android.plugins.memory.sync

import android.util.Base64
import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 同步提供者接口。
 */
interface SyncProvider {
    suspend fun push(data: Map<String, ByteArray>): Result<Unit>
    suspend fun pull(): Result<Map<String, ByteArray>>
    suspend fun testConnection(): Result<String>
}

/**
 * 坚果云 WebDAV 同步提供者（HttpURLConnection，无额外依赖）。
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
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
    }

    private fun ensureTrailingSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"

    private fun basicAuthHeader(): String {
        val token = Base64.encodeToString(
            "$username:$appPassword".toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        return "Basic $token"
    }

    private fun open(url: String, method: String): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", basicAuthHeader())
            instanceFollowRedirects = true
            doInput = true
        }
        return conn
    }

    private fun ensureDirectory() {
        val base = ensureTrailingSlash(webDavUrl)
        val conn = open(base, "MKCOL")
        try {
            conn.connect()
            val code = conn.responseCode
            if (code != 201 && code != 405 && code != 409 && code != 301 && code != 200) {
                Log.w(TAG, "MKCOL returned $code")
            }
        } catch (e: Exception) {
            Log.w(TAG, "MKCOL failed: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    private fun readBody(conn: HttpURLConnection): ByteArray {
        val stream = try {
            conn.inputStream
        } catch (_: Exception) {
            conn.errorStream
        } ?: return ByteArray(0)
        BufferedInputStream(stream).use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    override suspend fun push(data: Map<String, ByteArray>): Result<Unit> {
        return try {
            ensureDirectory()
            val base = ensureTrailingSlash(webDavUrl)
            for ((filename, bytes) in data) {
                val url = base + filename.trimStart('/')
                val conn = open(url, "PUT")
                try {
                    conn.doOutput = true
                    val contentType = if (filename.endsWith(".db")) {
                        "application/octet-stream"
                    } else {
                        "text/plain; charset=utf-8"
                    }
                    conn.setRequestProperty("Content-Type", contentType)
                    conn.setRequestProperty("Content-Length", bytes.size.toString())
                    conn.outputStream.use { it.write(bytes) }
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        val err = String(readBody(conn), StandardCharsets.UTF_8)
                        return Result.failure(
                            IllegalStateException("PUT $filename failed: $code $err")
                        )
                    }
                } finally {
                    conn.disconnect()
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
                val conn = open(base + name, "GET")
                try {
                    val code = conn.responseCode
                    if (code in 200..299) {
                        out[name] = readBody(conn)
                    }
                } finally {
                    conn.disconnect()
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
            val conn = open(base, "PROPFIND")
            try {
                conn.doOutput = true
                conn.setRequestProperty("Depth", "0")
                conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
                val body = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>""".toByteArray(StandardCharsets.UTF_8)
                conn.setRequestProperty("Content-Length", body.size.toString())
                conn.outputStream.use { it.write(body) }
                val code = conn.responseCode
                if (code in 200..299 || code == 207) {
                    Result.success("ok $code")
                } else {
                    val err = String(readBody(conn), StandardCharsets.UTF_8)
                    Result.failure(IllegalStateException("PROPFIND failed: $code $err"))
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
