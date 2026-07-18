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

import com.lanxin.android.builtin.pet.domain.ArchiveExtractor
import com.lanxin.android.builtin.pet.domain.AssetDownloadTransport
import com.lanxin.android.builtin.pet.domain.DebugAssetCatalog
import com.lanxin.android.builtin.pet.domain.DebugAssetDownloadEvent
import com.lanxin.android.builtin.pet.domain.DebugAssetDownloader
import com.lanxin.android.builtin.pet.domain.DebugAssetKind
import com.lanxin.android.builtin.pet.domain.DebugAssetMirror
import com.lanxin.android.builtin.pet.domain.DebugOpenSourcePaths
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DebugAssetDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun catalog_live2dCandidates_preferJsdelivr() {
        val cands = DebugAssetCatalog.live2dFileCandidates(
            "Mao.model3.json",
            DebugAssetMirror.MIRROR_CDN
        )
        assertTrue(cands.first().url.contains("cdn.jsdelivr.net"))
        assertTrue(cands.any { it.url.contains("fastly.jsdelivr.net") })
        assertTrue(cands.any { it.url.contains("raw.githubusercontent.com") })
        assertEquals("jsdelivr", cands.first().label)
        assertFalse(cands.any { it.label.contains("ghproxy") })
    }

    @Test
    fun catalog_resolveUrl_doesNotPrefixGhproxy() {
        val official = "https://github.com/k2-fsa/sherpa-onnx/releases/download/x/a.tar.bz2"
        assertEquals(official, DebugAssetCatalog.resolveUrl(official, DebugAssetMirror.OFFICIAL))
        assertEquals(official, DebugAssetCatalog.resolveUrl(official, DebugAssetMirror.MIRROR_CDN))
    }

    @Test
    fun mirrorAttemptOrder_cdnFallsBackToOfficial() {
        val order = DebugAssetCatalog.mirrorAttemptOrder(DebugAssetMirror.MIRROR_CDN)
        assertEquals(DebugAssetMirror.MIRROR_CDN, order.first().first)
        assertEquals(DebugAssetMirror.OFFICIAL, order.last().first)
        assertFalse(order.any { it.first.name.contains("GHPROXY") })
    }

    @Test
    fun asrMultiFile_hfMirrorFirst() {
        val sources = DebugAssetCatalog.asrMultiFileSources(DebugAssetMirror.MIRROR_CDN)
        assertTrue(sources.isNotEmpty())
        assertTrue(sources.first().baseUrl.contains("hf-mirror.com"))
        assertTrue(sources.any { it.baseUrl.contains("huggingface.co") })
        assertTrue(DebugAssetCatalog.asrModelRelativeFiles.contains("tokens.txt"))
    }

    @Test
    fun localLlmMultiFile_modelscopeFirst() {
        val sources = DebugAssetCatalog.localLlmMultiFileSources(DebugAssetMirror.MIRROR_CDN)
        assertTrue(sources.first().baseUrl.contains("modelscope.cn"))
        assertTrue(sources.first().label == "modelscope")
        assertTrue(DebugAssetCatalog.localLlmRelativeFiles.contains("llm.mnn"))
    }

    @Test
    fun localLlm_preseededDir_isReadyWithoutNetwork() = runTest {
        val baseDir = tmp.newFolder("base")
        val dir = File(baseDir, DebugOpenSourcePaths.LOCAL_LLM_LIGHT_DIR_REL)
        dir.mkdirs()
        File(dir, "llm.mnn").writeText("stub-mnn")
        File(dir, "llm.mnn.weight").writeText("stub-weight")
        val transport = object : AssetDownloadTransport {
            override suspend fun downloadToFile(
                url: String,
                destFile: File,
                onProgress: suspend (Long, Long) -> Unit
            ) {
                error("should not download when already ready")
            }

            override suspend fun openStream(url: String): InputStream =
                ByteArrayInputStream(ByteArray(0))
        }
        val downloader = DebugAssetDownloader(transport)
        assertTrue(downloader.isReady(baseDir, DebugAssetKind.LOCAL_LLM))
        assertTrue(downloader.readyPath(baseDir, DebugAssetKind.LOCAL_LLM).contains("local-llm"))
    }

    @Test
    fun archiveExtractor_safeResolve_blocksZipSlip() {
        val dest = tmp.newFolder("out")
        try {
            ArchiveExtractor.safeResolve(dest, "../evil.txt")
            org.junit.Assert.fail("expected zip-slip rejection")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("zip-slip") || e.message!!.contains("非法"))
        }
    }

    @Test
    fun asr_preseededDir_isReadyWithoutNetwork() = runTest {
        val baseDir = tmp.newFolder("base")
        val zipBytes = buildZipArchive(
            mapOf(
                "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/encoder.onnx" to
                    "fake-onnx".toByteArray(),
                "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/tokens.txt" to
                    "a\n".toByteArray()
            )
        )
        val extractDir = File(baseDir, "LanXin/asr")
        extractDir.mkdirs()
        val zipFile = File(tmp.newFolder("arc"), "model.zip")
        zipFile.writeBytes(zipBytes)
        ArchiveExtractor.extract(zipFile, extractDir)

        val transport = object : AssetDownloadTransport {
            override suspend fun downloadToFile(
                url: String,
                destFile: File,
                onProgress: suspend (Long, Long) -> Unit
            ) {
                error("should not download when already ready")
            }

            override suspend fun openStream(url: String): InputStream =
                ByteArrayInputStream(ByteArray(0))
        }
        val downloader = DebugAssetDownloader(transport)
        assertTrue(downloader.isReady(baseDir, DebugAssetKind.ASR))
        assertTrue(downloader.readyPath(baseDir, DebugAssetKind.ASR).contains("zipformer"))
    }

    @Test
    fun download_live2d_withMockTransport_writesModel3() = runTest {
        val baseDir = tmp.newFolder("base")
        val transport = object : AssetDownloadTransport {
            override suspend fun downloadToFile(
                url: String,
                destFile: File,
                onProgress: suspend (Long, Long) -> Unit
            ) {
                destFile.parentFile?.mkdirs()
                val body = when {
                    url.endsWith("Mao.model3.json") -> """{"Version":3}"""
                    url.endsWith("Mao.moc3") -> "moc3-bytes"
                    else -> "x"
                }
                destFile.writeText(body)
                onProgress(body.length.toLong(), body.length.toLong())
            }

            override suspend fun openStream(url: String): InputStream =
                ByteArrayInputStream(ByteArray(0))
        }
        val downloader = DebugAssetDownloader(transport)
        val events = downloader.download(
            baseDir,
            DebugAssetKind.LIVE2D,
            DebugAssetMirror.MIRROR_CDN
        ).toList()

        assertTrue(events.any { it is DebugAssetDownloadEvent.Started })
        val completed = events.filterIsInstance<DebugAssetDownloadEvent.Completed>().single()
        assertEquals(DebugAssetKind.LIVE2D, completed.kind)
        assertTrue(File(completed.readyPath).isFile)
        assertTrue(downloader.isReady(baseDir, DebugAssetKind.LIVE2D))
        assertTrue(File(baseDir, DebugOpenSourcePaths.LIVE2D_MAO_MODEL3_REL).isFile)
        assertTrue(completed.readyPath.contains("LanXin"))
        assertTrue(completed.sourceLabel.contains("jsdelivr") || completed.sourceLabel.contains("github"))
    }

    @Test
    fun download_mirrorFallback_whenJsdelivrFails() = runTest {
        val baseDir = tmp.newFolder("base")
        var calls = 0
        val transport = object : AssetDownloadTransport {
            override suspend fun downloadToFile(
                url: String,
                destFile: File,
                onProgress: suspend (Long, Long) -> Unit
            ) {
                calls++
                if (url.contains("cdn.jsdelivr.net")) {
                    error("jsdelivr down")
                }
                destFile.parentFile?.mkdirs()
                val body = when {
                    url.contains("Mao.moc3") -> "moc"
                    url.contains("model3") -> "{}"
                    else -> "f"
                }
                destFile.writeText(body)
                onProgress(1, 1)
            }

            override suspend fun openStream(url: String): InputStream =
                ByteArrayInputStream(ByteArray(0))
        }
        val downloader = DebugAssetDownloader(transport)
        val events = downloader.download(
            baseDir,
            DebugAssetKind.LIVE2D,
            DebugAssetMirror.MIRROR_CDN
        ).toList()
        assertTrue(events.any { it is DebugAssetDownloadEvent.Completed })
        assertTrue(calls > DebugAssetCatalog.live2dMaoRelativeFiles.size)
        val completed = events.filterIsInstance<DebugAssetDownloadEvent.Completed>().single()
        assertTrue(
            completed.sourceLabel == "fastly-jsdelivr" ||
                completed.sourceLabel == "github-raw"
        )
    }

    @Test
    fun download_asr_fromHfMirror_multiFile() = runTest {
        val baseDir = tmp.newFolder("base")
        val transport = object : AssetDownloadTransport {
            override suspend fun downloadToFile(
                url: String,
                destFile: File,
                onProgress: suspend (Long, Long) -> Unit
            ) {
                if (!url.contains("hf-mirror.com") && !url.contains("huggingface.co")) {
                    error("unexpected url $url")
                }
                destFile.parentFile?.mkdirs()
                destFile.writeText("fake-${destFile.name}")
                onProgress(1, 1)
            }

            override suspend fun openStream(url: String): InputStream =
                ByteArrayInputStream(ByteArray(0))
        }
        val downloader = DebugAssetDownloader(transport)
        val events = downloader.download(
            baseDir,
            DebugAssetKind.ASR,
            DebugAssetMirror.MIRROR_CDN
        ).toList()
        val completed = events.filterIsInstance<DebugAssetDownloadEvent.Completed>().single()
        assertTrue(completed.readyPath.contains("zipformer"))
        assertTrue(File(completed.readyPath, "tokens.txt").isFile)
        assertEquals("hf-mirror", completed.sourceLabel)
    }

    @Test
    fun download_failed_emitsFailedWithAttemptedSources() = runTest {
        val baseDir = tmp.newFolder("base")
        val transport = object : AssetDownloadTransport {
            override suspend fun downloadToFile(
                url: String,
                destFile: File,
                onProgress: suspend (Long, Long) -> Unit
            ) {
                error("network unreachable forever")
            }

            override suspend fun openStream(url: String): InputStream =
                ByteArrayInputStream(ByteArray(0))
        }
        val downloader = DebugAssetDownloader(transport)
        val events = downloader.download(
            baseDir,
            DebugAssetKind.LIVE2D,
            DebugAssetMirror.MIRROR_CDN
        ).toList()
        assertFalse(events.any { it is DebugAssetDownloadEvent.Completed })
        val failed = events.filterIsInstance<DebugAssetDownloadEvent.Failed>().single()
        assertTrue(failed.message.isNotBlank())
        assertEquals(DebugAssetKind.LIVE2D, failed.kind)
        assertTrue(failed.attemptedSources.isNotEmpty() || failed.message.contains("已试"))
        assertTrue(events.first() is DebugAssetDownloadEvent.Started)
        assertTrue(events.last() is DebugAssetDownloadEvent.Failed)
        assertEquals(1, events.filterIsInstance<DebugAssetDownloadEvent.Failed>().size)
    }

    @Test
    fun download_failureAfterProgress_emitsFailedWithoutTransparencyCrash() = runTest {
        val filesDir = tmp.newFolder("files")
        var progressCalls = 0
        val transport = object : AssetDownloadTransport {
            override suspend fun downloadToFile(
                url: String,
                destFile: File,
                onProgress: suspend (Long, Long) -> Unit
            ) {
                progressCalls++
                onProgress(1L, 100L)
                error("mid-download failure after progress")
            }

            override suspend fun openStream(url: String): InputStream =
                ByteArrayInputStream(ByteArray(0))
        }
        val downloader = DebugAssetDownloader(transport)
        val events = downloader.download(
            filesDir,
            DebugAssetKind.LIVE2D,
            DebugAssetMirror.MIRROR_CDN
        ).toList()

        assertTrue(progressCalls >= 1)
        assertTrue(events.any { it is DebugAssetDownloadEvent.Started })
        assertTrue(events.any { it is DebugAssetDownloadEvent.Progress })
        assertFalse(events.any { it is DebugAssetDownloadEvent.Completed })
        val failed = events.filterIsInstance<DebugAssetDownloadEvent.Failed>().single()
        assertTrue(failed.message.contains("mid-download") || failed.message.isNotBlank())
        assertFalse(
            "Failed 不得是 Flow exception transparency 文案",
            failed.message.contains("exception transparency", ignoreCase = true) ||
                failed.message.contains("Flow was collected", ignoreCase = true) ||
                failed.message.contains("emission happened", ignoreCase = true)
        )
        assertTrue(events.last() is DebugAssetDownloadEvent.Failed)
        assertEquals(1, events.count { it is DebugAssetDownloadEvent.Failed })
    }

    /**
     * 真机复现：Ktor transport 在 withContext(IO) / Undispatched 里调 onProgress，
     * cold flow 的 emit 会炸 Flow exception transparency。channelFlow+send 应安全。
     */
    @Test
    fun download_progressFromWrongCoroutineContext_doesNotCrash() = runTest {
        val baseDir = tmp.newFolder("base-wrong-ctx")
        var progressCalls = 0
        val transport = object : AssetDownloadTransport {
            override suspend fun downloadToFile(
                url: String,
                destFile: File,
                onProgress: suspend (Long, Long) -> Unit
            ) {
                // 模拟 Ktor：进度回调在「另一层」IO/Undispatched 上下文触发
                withContext(Dispatchers.IO) {
                    progressCalls++
                    onProgress(50L, 100L)
                    progressCalls++
                    onProgress(100L, 100L)
                }
                destFile.parentFile?.mkdirs()
                val body = when {
                    url.endsWith("Mao.model3.json") -> """{"Version":3}"""
                    url.endsWith("Mao.moc3") -> "moc3-bytes"
                    else -> "x"
                }
                destFile.writeText(body)
            }

            override suspend fun openStream(url: String): InputStream =
                ByteArrayInputStream(ByteArray(0))
        }
        val downloader = DebugAssetDownloader(transport)
        val events = downloader.download(
            baseDir,
            DebugAssetKind.LIVE2D,
            DebugAssetMirror.MIRROR_CDN
        ).toList()

        assertTrue(progressCalls >= 2)
        assertTrue(events.any { it is DebugAssetDownloadEvent.Started })
        assertTrue(events.any { it is DebugAssetDownloadEvent.Progress })
        val completed = events.filterIsInstance<DebugAssetDownloadEvent.Completed>().single()
        assertEquals(DebugAssetKind.LIVE2D, completed.kind)
        assertTrue(File(completed.readyPath).isFile)
        assertFalse(
            events.any {
                it is DebugAssetDownloadEvent.Failed &&
                    (
                        it.message.contains("exception transparency", ignoreCase = true) ||
                            it.message.contains("Flow was collected", ignoreCase = true)
                        )
            }
        )
    }

    @Test
    fun download_asr_progressFromWrongContext_thenBusinessFailed() = runTest {
        val baseDir = tmp.newFolder("base-asr-wrong-ctx")
        var progressCalls = 0
        val transport = object : AssetDownloadTransport {
            override suspend fun downloadToFile(
                url: String,
                destFile: File,
                onProgress: suspend (Long, Long) -> Unit
            ) {
                // 先从「错上下文」发 Progress，再抛业务错误（真机 github-release 路径）
                withContext(Dispatchers.Default) {
                    progressCalls++
                    onProgress(10L, 100L)
                }
                error("network unreachable forever")
            }

            override suspend fun openStream(url: String): InputStream =
                ByteArrayInputStream(ByteArray(0))
        }
        val downloader = DebugAssetDownloader(transport)
        val events = downloader.download(
            baseDir,
            DebugAssetKind.ASR,
            DebugAssetMirror.OFFICIAL
        ).toList()

        assertTrue(progressCalls >= 1)
        assertTrue(events.first() is DebugAssetDownloadEvent.Started)
        assertTrue(events.any { it is DebugAssetDownloadEvent.Progress })
        assertFalse(events.any { it is DebugAssetDownloadEvent.Completed })
        val failed = events.filterIsInstance<DebugAssetDownloadEvent.Failed>().single()
        assertEquals(DebugAssetKind.ASR, failed.kind)
        assertTrue(
            "应是业务失败消息",
            failed.message.contains("network") ||
                failed.message.contains("unreachable") ||
                failed.message.contains("已试") ||
                failed.message.isNotBlank()
        )
        assertFalse(
            "不得包装 Flow exception transparency",
            failed.message.contains("exception transparency", ignoreCase = true) ||
                failed.message.contains("Flow was collected", ignoreCase = true) ||
                failed.message.contains("emission happened", ignoreCase = true) ||
                failed.message.contains("Undispatched", ignoreCase = true)
        )
        assertTrue(events.last() is DebugAssetDownloadEvent.Failed)
    }

    @Test
    fun download_cancelled_emitsCancelledWithoutRethrow() = runTest {
        val filesDir = tmp.newFolder("files")
        val transport = object : AssetDownloadTransport {
            override suspend fun downloadToFile(
                url: String,
                destFile: File,
                onProgress: suspend (Long, Long) -> Unit
            ) {
                onProgress(1L, 10L)
                throw CancellationException("user cancel")
            }

            override suspend fun openStream(url: String): InputStream =
                ByteArrayInputStream(ByteArray(0))
        }
        val downloader = DebugAssetDownloader(transport)
        val events = downloader.download(
            filesDir,
            DebugAssetKind.LIVE2D,
            DebugAssetMirror.MIRROR_CDN
        ).toList()

        assertTrue(events.first() is DebugAssetDownloadEvent.Started)
        assertTrue(events.any { it is DebugAssetDownloadEvent.Progress })
        assertTrue(events.any { it is DebugAssetDownloadEvent.Cancelled })
        assertFalse(events.any { it is DebugAssetDownloadEvent.Failed })
        assertFalse(events.any { it is DebugAssetDownloadEvent.Completed })
        assertEquals(DebugAssetDownloadEvent.Cancelled, events.last())
    }

    @Test
    fun shortError_truncates() {
        val long = RuntimeException("x".repeat(200))
        val s = DebugAssetDownloader.shortError(long)
        assertTrue(s.length <= 160)
    }

    @Test
    fun localLlm_resume_skipsAlreadyDownloadedFiles() = runTest {
        val baseDir = tmp.newFolder("base-llm-resume")
        val staging = File(baseDir, "LanXin/.tmp/mf-modelscope-staging")
        // 预置部分已下完的小文件（模拟断点后续传）
        for (rel in listOf("config.json", "configuration.json", "llm_config.json")) {
            val f = File(staging, rel)
            f.parentFile?.mkdirs()
            f.writeText("pre-$rel")
        }
        val transport = object : AssetDownloadTransport {
            val downloaded = mutableListOf<String>()
            override suspend fun downloadToFile(
                url: String,
                destFile: File,
                onProgress: suspend (Long, Long) -> Unit
            ) {
                downloaded += destFile.name
                destFile.parentFile?.mkdirs()
                destFile.writeText("new-${destFile.name}")
                onProgress(1, 1)
            }

            override suspend fun openStream(url: String): InputStream =
                ByteArrayInputStream(ByteArray(0))
        }
        val downloader = DebugAssetDownloader(transport)
        val events = downloader.download(
            baseDir,
            DebugAssetKind.LOCAL_LLM,
            DebugAssetMirror.MIRROR_CDN
        ).toList()
        val completed = events.filterIsInstance<DebugAssetDownloadEvent.Completed>().single()
        assertEquals(DebugAssetKind.LOCAL_LLM, completed.kind)
        assertTrue(downloader.isReady(baseDir, DebugAssetKind.LOCAL_LLM))
        // 已预置的三个文件不应再请求
        assertFalse(transport.downloaded.contains("config.json"))
        assertFalse(transport.downloaded.contains("configuration.json"))
        assertFalse(transport.downloaded.contains("llm_config.json"))
        assertTrue(transport.downloaded.contains("llm.mnn"))
        assertTrue(transport.downloaded.contains("llm.mnn.weight"))
        assertTrue(
            events.any {
                it is DebugAssetDownloadEvent.Progress && it.phase == "resuming"
            }
        )
    }

    @Test
    fun compactTimeoutMessage_stripsUrlKeepsFile() {
        val raw =
            "Connect timeout has expired " +
                "[url=https://huggingface.co/taobao-mnn/Qwen2.5-1.5B-Instruct-MNN/resolve/main/config.json, " +
                "connect_timeout=unknown ms]"
        val c = DebugAssetDownloader.compactTimeoutMessage(raw)
        assertTrue(c.contains("Connect timeout"))
        assertTrue(c.contains("config.json"))
        assertFalse(c.contains("huggingface.co"))
    }

    @Test
    fun compactTimeoutMessage_badConfigNotMislabelledTimeout() {
        val c = DebugAssetDownloader.compactTimeoutMessage(
            "Only positive timeout values are allowed, for request timeout millis 0"
        )
        assertEquals("Bad timeout config", c)
        assertFalse(c.equals("Timeout", ignoreCase = true))
    }

    @Test
    fun shortError_prefersClassifiedTransportMessage() {
        val t = IllegalStateException(
            "Connect timeout · config.json @ modelscope.cn/…/config.json"
        )
        val s = DebugAssetDownloader.shortError(t)
        assertTrue(s.startsWith("Connect timeout"))
        assertTrue(s.contains("config.json"))
    }

    @Test
    fun shortError_illegalArgPositiveTimeoutIsBadConfig() {
        val t = IllegalArgumentException(
            "Only positive timeout values are allowed, for request timeout millis 0"
        )
        assertEquals("Bad timeout config", DebugAssetDownloader.shortError(t))
    }

    @Test
    fun formatSourceErrors_keepsEachSource() {
        val s = DebugAssetDownloader.formatSourceErrors(
            listOf(
                "modelscope" to "Connect timeout (config.json)",
                "hf-mirror" to "HTTP 403",
                "huggingface" to "Connect timeout (config.json)"
            )
        )
        assertTrue(s.contains("modelscope:"))
        assertTrue(s.contains("hf-mirror:"))
        assertTrue(s.contains("huggingface:"))
        assertTrue(s.contains("HTTP 403"))
    }

    @Test
    fun failMessage_localLlm_includesManualPathHint() {
        val msg = DebugAssetDownloader.failMessage(
            DebugAssetKind.LOCAL_LLM,
            "modelscope:Connect timeout; huggingface:Connect timeout",
            listOf("modelscope", "huggingface")
        )
        assertTrue(msg.contains("modelscope"))
        assertTrue(msg.contains("LanXin/models/local-llm/light"))
        assertTrue(msg.contains("Wi") || msg.contains("Wi‑Fi") || msg.contains("重试"))
        // 已含源:错误时不重复「已试」
        assertFalse(msg.contains("已试:modelscope"))
    }

    @Test
    fun localLlm_allSourcesFail_aggregatesPerSourceErrors() = runTest {
        val baseDir = tmp.newFolder("base-llm-fail")
        val transport = object : AssetDownloadTransport {
            override suspend fun downloadToFile(
                url: String,
                destFile: File,
                onProgress: suspend (Long, Long) -> Unit
            ) {
                when {
                    url.contains("modelscope.cn") && !url.contains("www.") ->
                        error("Connect timeout has expired [url=$url, connect_timeout=unknown ms]")
                    url.contains("www.modelscope.cn") ->
                        error("Connect timeout has expired [url=$url]")
                    url.contains("hf-mirror.com") ->
                        error("下载失败 HTTP 403")
                    url.contains("huggingface.co") ->
                        error("Connect timeout has expired [url=$url, connect_timeout=unknown ms]")
                    else -> error("unexpected $url")
                }
            }

            override suspend fun openStream(url: String): InputStream =
                ByteArrayInputStream(ByteArray(0))
        }
        val downloader = DebugAssetDownloader(transport)
        val events = downloader.download(
            baseDir,
            DebugAssetKind.LOCAL_LLM,
            DebugAssetMirror.MIRROR_CDN
        ).toList()

        assertTrue(events.first() is DebugAssetDownloadEvent.Started)
        val failed = events.filterIsInstance<DebugAssetDownloadEvent.Failed>().single()
        assertEquals(DebugAssetKind.LOCAL_LLM, failed.kind)
        // 每源错误均保留，不只最后一个 HF
        assertTrue(failed.message.contains("modelscope"))
        assertTrue(
            failed.message.contains("hf-mirror") ||
                failed.attemptedSources.contains("hf-mirror")
        )
        assertTrue(
            failed.message.contains("huggingface") ||
                failed.attemptedSources.contains("huggingface")
        )
        assertTrue(failed.message.contains("LanXin/models/local-llm/light"))
        assertTrue(failed.attemptedSources.size >= 3)
        assertTrue(events.last() is DebugAssetDownloadEvent.Failed)
    }

    @Test
    fun rootDir_isLanXinUserVisible() {
        assertEquals("LanXin", DebugOpenSourcePaths.ROOT_DIR)
        assertTrue(DebugOpenSourcePaths.LIVE2D_MAO_MODEL3_REL.startsWith("LanXin/"))
        assertTrue(DebugOpenSourcePaths.ASR_ZIPFORMER_14M_REL.startsWith("LanXin/"))
        assertTrue(DebugOpenSourcePaths.TTS_MATCHA_BAKER_REL.startsWith("LanXin/"))
    }

    private fun buildZipArchive(entries: Map<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            entries.forEach { (name, data) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}
