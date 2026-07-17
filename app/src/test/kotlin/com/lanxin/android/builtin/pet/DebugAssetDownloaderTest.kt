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
    fun catalog_resolveUrl_mirrorPrefixesOfficial() {
        val official = "https://github.com/k2-fsa/sherpa-onnx/releases/download/x/a.tar.bz2"
        assertEquals(official, DebugAssetCatalog.resolveUrl(official, DebugAssetMirror.OFFICIAL))
        val mirrored = DebugAssetCatalog.resolveUrl(official, DebugAssetMirror.MIRROR_GHPROXY, 0)
        assertTrue(mirrored.startsWith("https://"))
        assertTrue(mirrored.contains("github.com") || mirrored.endsWith(official))
        assertTrue(mirrored.length > official.length)
    }

    @Test
    fun mirrorAttemptOrder_ghproxyFallsBackToOfficial() {
        val order = DebugAssetCatalog.mirrorAttemptOrder(DebugAssetMirror.MIRROR_GHPROXY)
        assertTrue(order.any { it.first == DebugAssetMirror.MIRROR_GHPROXY })
        assertEquals(DebugAssetMirror.OFFICIAL, order.last().first)
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
            DebugAssetMirror.OFFICIAL
        ).toList()

        assertTrue(events.any { it is DebugAssetDownloadEvent.Started })
        val completed = events.filterIsInstance<DebugAssetDownloadEvent.Completed>().single()
        assertEquals(DebugAssetKind.LIVE2D, completed.kind)
        assertTrue(File(completed.readyPath).isFile)
        assertTrue(downloader.isReady(baseDir, DebugAssetKind.LIVE2D))
        assertTrue(File(baseDir, DebugOpenSourcePaths.LIVE2D_MAO_MODEL3_REL).isFile)
        assertTrue(completed.readyPath.contains("LanXin"))
    }

    @Test
    fun download_mirrorFallback_whenFirstFails() = runTest {
        val baseDir = tmp.newFolder("base")
        var calls = 0
        val transport = object : AssetDownloadTransport {
            override suspend fun downloadToFile(
                url: String,
                destFile: File,
                onProgress: suspend (Long, Long) -> Unit
            ) {
                calls++
                if (url.contains("ghproxy") || url.contains("ddlc")) {
                    error("mirror down")
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
            DebugAssetMirror.MIRROR_GHPROXY
        ).toList()
        assertTrue(events.any { it is DebugAssetDownloadEvent.Completed })
        assertTrue(calls > DebugAssetCatalog.live2dMaoRelativeFiles.size)
    }

    @Test
    fun download_failed_emitsFailedShortMessage() = runTest {
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
            DebugAssetMirror.OFFICIAL
        ).toList()
        assertFalse(events.any { it is DebugAssetDownloadEvent.Completed })
        val failed = events.filterIsInstance<DebugAssetDownloadEvent.Failed>().single()
        assertTrue(failed.message.isNotBlank())
        assertTrue(failed.message.length <= 120)
        // Flow 应正常结束，不抛 exception transparency / 二次失败
        assertEquals(DebugAssetKind.LIVE2D, failed.kind)
        assertEquals(1, events.filterIsInstance<DebugAssetDownloadEvent.Failed>().size)
        assertTrue(events.first() is DebugAssetDownloadEvent.Started)
        assertTrue(events.last() is DebugAssetDownloadEvent.Failed)
    }

    @Test
    fun download_failureAfterProgress_emitsFailedWithoutTransparencyCrash() = runTest {
        // 复现用户崩溃：先 Progress emit，随后 transport 抛错；catch 不得再 emit
        val baseDir = tmp.newFolder("base")
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
            baseDir,
            DebugAssetKind.LIVE2D,
            DebugAssetMirror.OFFICIAL
        ).toList()

        assertTrue(progressCalls >= 1)
        assertTrue(events.any { it is DebugAssetDownloadEvent.Started })
        assertTrue(events.any { it is DebugAssetDownloadEvent.Progress })
        assertFalse(events.any { it is DebugAssetDownloadEvent.Completed })
        val failed = events.filterIsInstance<DebugAssetDownloadEvent.Failed>().single()
        assertTrue(failed.message.contains("mid-download") || failed.message.isNotBlank())
        assertTrue(events.last() is DebugAssetDownloadEvent.Failed)
        // 无 IllegalStateException("Flow exception transparency is violated") 泄漏
        assertEquals(1, events.count { it is DebugAssetDownloadEvent.Failed })
    }

    @Test
    fun download_cancelled_emitsCancelledWithoutRethrow() = runTest {
        val baseDir = tmp.newFolder("base")
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
            baseDir,
            DebugAssetKind.LIVE2D,
            DebugAssetMirror.OFFICIAL
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
        assertTrue(s.length <= 120)
    }

    @Test
    fun rootDir_isLanXinUserVisible() {
        assertEquals("LanXin", DebugOpenSourcePaths.ROOT_DIR)
        assertTrue(DebugOpenSourcePaths.LIVE2D_MAO_MODEL3_REL.startsWith("LanXin/"))
        assertTrue(DebugOpenSourcePaths.ASR_ZIPFORMER_14M_REL.startsWith("LanXin/"))
        assertTrue(DebugOpenSourcePaths.TTS_MATCHA_BAKER_REL.startsWith("LanXin/"))
    }

    @Test
    fun live2dFileUrls_areSingleFileNotDirectory() {
        val url = DebugAssetCatalog.live2dFileUrl("Mao.model3.json")
        assertTrue(url.endsWith("/Mao.model3.json"))
        assertFalse(url.endsWith("/Mao"))
        assertFalse(url.contains("cdn.jsdelivr.net/gh/Live2D/CubismWebSamples@develop/Samples/Resources/Mao\""))
        // 固定 commit 或 raw 单文件，避免 jsDelivr 目录 404
        assertTrue(
            url.contains("raw.githubusercontent.com") ||
                url.contains("cdn.jsdelivr.net/gh/") ||
                url.contains("fastly.jsdelivr.net/gh/")
        )
        assertTrue(url.contains("Mao.model3.json"))
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
