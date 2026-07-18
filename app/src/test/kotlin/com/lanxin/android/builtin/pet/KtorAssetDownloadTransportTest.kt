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

package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.data.KtorAssetDownloadTransport
import io.ktor.client.plugins.HttpTimeoutConfig
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载专用超时 + 可恢复：不复用 API 默认短超时。
 * connect ≥ 60s；socket 长空闲；request=INFINITE（大文件靠进度 + 取消）。
 *
 * 回归：Ktor 3.5 禁止 requestTimeoutMillis=0，必须用 INFINITE_TIMEOUT_MS，
 * 否则每次下载在装 HttpTimeout 时立刻 IllegalArgumentException，
 * 且被 shortError 误标成「Timeout」。
 */
class KtorAssetDownloadTransportTest {

    @Test
    fun downloadTimeouts_areGenerousForLargeAssets() {
        assertTrue(
            "connectTimeout 应 ≥ 30s，弱网建连",
            KtorAssetDownloadTransport.CONNECT_TIMEOUT_MS >= 30_000L
        )
        assertTrue(
            "connectTimeout 建议 ≥ 60s（现 90s）",
            KtorAssetDownloadTransport.CONNECT_TIMEOUT_MS >= 60_000L
        )
        assertTrue(
            "socketTimeout 应 ≥ 2min，大文件读空闲",
            KtorAssetDownloadTransport.SOCKET_TIMEOUT_MS >= 120_000L
        )
        assertEquals(
            "requestTimeout 必须用 INFINITE，禁止 0（Ktor 3.5 require>0）",
            HttpTimeoutConfig.INFINITE_TIMEOUT_MS,
            KtorAssetDownloadTransport.REQUEST_TIMEOUT_MS
        )
        assertTrue(
            "requestTimeout 必须 > 0，否则 HttpTimeoutConfig 直接抛",
            KtorAssetDownloadTransport.REQUEST_TIMEOUT_MS > 0L
        )
        assertEquals("OkHttp", KtorAssetDownloadTransport.ENGINE_NAME)
    }

    @Test
    fun requestTimeout_zeroIsInvalid_infiniteIsValid() {
        // 复现 #93 误伤：0 在 Ktor 3.5 非法
        var zeroFailed = false
        try {
            HttpTimeoutConfig(requestTimeoutMillis = 0L)
        } catch (_: IllegalArgumentException) {
            zeroFailed = true
        }
        assertTrue("Ktor 3.5 应拒绝 requestTimeout=0", zeroFailed)

        // INFINITE 可配置
        val cfg = HttpTimeoutConfig(
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        )
        assertEquals(
            HttpTimeoutConfig.INFINITE_TIMEOUT_MS,
            cfg.requestTimeoutMillis
        )
    }

    @Test
    fun resume_partFile_sidecarName() {
        val dest = File("/tmp/LanXin/models/local-llm/light/llm.mnn.weight")
        val part = KtorAssetDownloadTransport.partFile(dest)
        assertEquals("llm.mnn.weight.part", part.name)
        assertEquals(dest.parentFile, part.parentFile)
    }

    @Test
    fun urlHint_keepsHostAndFile() {
        val hint = KtorAssetDownloadTransport.urlHint(
            "https://hf-mirror.com/csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/resolve/main/tokens.txt?download=true"
        )
        assertTrue(hint.contains("hf-mirror.com"))
        assertTrue(hint.contains("tokens.txt"))
        assertFalse(hint.contains("download=true"))
    }

    @Test
    fun classifyError_distinguishesConnectSocketDnsAndBadConfig() {
        assertEquals(
            "Connect timeout",
            KtorAssetDownloadTransport.classifyError(
                SocketTimeoutException("connect timed out")
            )
        )
        assertEquals(
            "Socket timeout",
            KtorAssetDownloadTransport.classifyError(
                SocketTimeoutException("Read timed out")
            )
        )
        assertEquals(
            "DNS failed",
            KtorAssetDownloadTransport.classifyError(
                UnknownHostException("hf-mirror.com")
            )
        )
        assertEquals(
            "Connect refused",
            KtorAssetDownloadTransport.classifyError(
                ConnectException("Connection refused")
            )
        )
        // 核心回归：Ktor 3.5 配置错误 ≠ Timeout
        assertEquals(
            "Bad timeout config",
            KtorAssetDownloadTransport.classifyError(
                IllegalArgumentException("Only positive timeout values are allowed, for request timeout millis 0")
            )
        )
        assertEquals(
            "Connect timeout",
            KtorAssetDownloadTransport.classifyError(
                IllegalStateException(
                    "Connect timeout has expired [url=https://x/config.json, connect_timeout=unknown ms]"
                )
            )
        )
    }

    @Test
    fun enrichError_appendsUrlHint() {
        val dest = File("/tmp/LanXin/asr/tokens.txt")
        val err = KtorAssetDownloadTransport.enrichError(
            url = "https://cdn.jsdelivr.net/gh/Live2D/CubismWebSamples@develop/Samples/Resources/Mao/Mao.model3.json",
            destFile = dest,
            cause = IllegalStateException("下载失败 HTTP 403")
        )
        val msg = err.message.orEmpty()
        assertTrue(msg.contains("HTTP 403") || msg.contains("下载失败"))
        assertTrue(msg.contains("cdn.jsdelivr.net") || msg.contains("Mao.model3.json"))
        assertTrue(msg.contains("tokens.txt"))
    }

    @Test
    fun retryPolicy_timeoutsAreRetryable_http403AndBadConfigNot() {
        assertTrue(KtorAssetDownloadTransport.isRetryable(SocketTimeoutException("read")))
        assertTrue(
            KtorAssetDownloadTransport.isRetryable(
                IllegalStateException("Connect timeout has expired")
            )
        )
        assertTrue(
            KtorAssetDownloadTransport.isRetryable(
                IllegalStateException("下载失败 HTTP 503")
            )
        )
        assertTrue(
            KtorAssetDownloadTransport.isRetryable(
                IllegalStateException("下载失败 HTTP 429")
            )
        )
        assertTrue(KtorAssetDownloadTransport.isRetryable(UnknownHostException("x")))
        assertFalse(
            KtorAssetDownloadTransport.isRetryable(
                IllegalStateException("下载失败 HTTP 403")
            )
        )
        assertFalse(
            KtorAssetDownloadTransport.isRetryable(
                IllegalStateException("下载失败 HTTP 404")
            )
        )
        // 配置错误不可重试
        assertFalse(
            KtorAssetDownloadTransport.isRetryable(
                IllegalArgumentException("Only positive timeout values are allowed, for request timeout millis 0")
            )
        )
        assertTrue(KtorAssetDownloadTransport.MAX_ATTEMPTS >= 3)
    }
}
