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

package com.lanxin.android.builtin.pet.domain

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App 内一键下载 Debug 开源资源到 [baseDir]/[DebugOpenSourcePaths.ROOT_DIR]/
 * （即用户可访问的 `LanXin/`，由 [DebugAssetStorage] 解析 base）。
 *
 * - Live2D：多文件 raw（CubismWebSamples Mao）
 * - ASR / TTS：sherpa-onnx release 归档解压
 * - 镜像：用户首选 + 失败回退官方
 * - 大文件**不进 git**；**禁止** AstrBot 服务器当下载盘
 *
 * Flow 异常透明：终端事件（Completed/Failed/Cancelled）在 catch **外** emit，
 * 避免 `Flow exception transparency is violated`。
 *
 * 写 DataStore 路径由 ViewModel 在 [DebugAssetDownloadEvent.Completed] 后处理。
 */
@Singleton
class DebugAssetDownloader @Inject constructor(
    private val transport: AssetDownloadTransport
) {

    private val mutex = Mutex()

    @Volatile
    private var activeJob: Job? = null

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }

    fun isReady(baseDir: File, kind: DebugAssetKind): Boolean {
        val path = readyPath(baseDir, kind)
        if (path.isBlank()) return false
        return when (kind) {
            DebugAssetKind.LIVE2D -> File(path).isFile && File(path).length() > 0L
            DebugAssetKind.ASR, DebugAssetKind.TTS ->
                DebugOpenSourcePaths.isModelDirReady(File(path))
        }
    }

    fun readyPath(baseDir: File, kind: DebugAssetKind): String {
        return when (kind) {
            DebugAssetKind.LIVE2D -> {
                val f = DebugOpenSourcePaths.live2dModelFile(baseDir)
                if (f.isFile) f.absolutePath else ""
            }
            DebugAssetKind.ASR -> {
                val d = DebugOpenSourcePaths.asrModelDir(baseDir)
                if (DebugOpenSourcePaths.isModelDirReady(d)) d.absolutePath else ""
            }
            DebugAssetKind.TTS -> {
                val d = DebugOpenSourcePaths.ttsModelDir(baseDir)
                if (DebugOpenSourcePaths.isModelDirReady(d)) d.absolutePath else ""
            }
        }
    }

    /**
     * 下载并安装 [kind]；进度以 [DebugAssetDownloadEvent] 流式上报。
     *
     * @param baseDir 下载 base（其下创建 `LanXin/…`），见 [DebugAssetStorage.resolve]
     */
    fun download(
        baseDir: File,
        kind: DebugAssetKind,
        preferredMirror: DebugAssetMirror
    ): Flow<DebugAssetDownloadEvent> = flow {
        emit(DebugAssetDownloadEvent.Started)
        val job = currentCoroutineContext()[Job]
        activeJob = job
        // 终端事件在 catch 外 emit，遵守 Flow exception transparency
        var terminal: DebugAssetDownloadEvent? = null
        try {
            mutex.withLock {
                val usedMirror = when (kind) {
                    DebugAssetKind.LIVE2D -> installLive2d(baseDir, preferredMirror) { event ->
                        emit(event)
                    }
                    DebugAssetKind.ASR, DebugAssetKind.TTS ->
                        installArchive(baseDir, kind, preferredMirror) { event ->
                            emit(event)
                        }
                }
                val path = readyPath(baseDir, kind)
                terminal = if (path.isBlank() || !isReady(baseDir, kind)) {
                    DebugAssetDownloadEvent.Failed(
                        kind = kind,
                        message = "下载完成但校验失败（缺少关键文件）"
                    )
                } else {
                    DebugAssetDownloadEvent.Completed(
                        kind = kind,
                        readyPath = path,
                        mirror = usedMirror
                    )
                }
            }
        } catch (_: CancellationException) {
            terminal = DebugAssetDownloadEvent.Cancelled
            // 不向下游抛取消，便于 UI collect 正常结束
        } catch (t: Throwable) {
            terminal = DebugAssetDownloadEvent.Failed(
                kind = kind,
                message = shortError(t)
            )
        } finally {
            if (activeJob === job) activeJob = null
        }
        terminal?.let { emit(it) }
    }.flowOn(Dispatchers.IO)

    private suspend fun installLive2d(
        baseDir: File,
        preferred: DebugAssetMirror,
        emit: suspend (DebugAssetDownloadEvent) -> Unit
    ): DebugAssetMirror {
        val root = File(baseDir, DebugAssetCatalog.live2d.extractDirRel)
        val staging = File(baseDir, "${DebugOpenSourcePaths.ROOT_DIR}/.tmp/live2d-mao-staging")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()

        val files = DebugAssetCatalog.live2dMaoRelativeFiles
        var usedMirror = preferred
        var done = 0
        for (rel in files) {
            currentCoroutineContext().ensureActive()
            val official = DebugAssetCatalog.live2dFileUrl(rel)
            val dest = File(staging, rel)
            dest.parentFile?.mkdirs()
            usedMirror = downloadWithMirrorFallback(
                officialUrl = official,
                destFile = dest,
                preferred = preferred
            ) { downloaded, total, mirror ->
                val filePct = if (total > 0) {
                    ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                val overall = ((done * 100) + filePct) / files.size
                emit(
                    DebugAssetDownloadEvent.Progress(
                        downloadedBytes = done.toLong() + 1,
                        totalBytes = files.size.toLong(),
                        percent = overall.coerceIn(0, 99),
                        mirror = mirror,
                        phase = "live2d-files"
                    )
                )
            }
            done++
        }

        emit(
            DebugAssetDownloadEvent.Progress(
                downloadedBytes = files.size.toLong(),
                totalBytes = files.size.toLong(),
                percent = 95,
                mirror = usedMirror,
                phase = "extracting"
            )
        )
        if (root.exists()) root.deleteRecursively()
        root.parentFile?.mkdirs()
        if (!staging.renameTo(root)) {
            staging.copyRecursively(root, overwrite = true)
            staging.deleteRecursively()
        }
        // NOTICE 可选
        val notice = File(root, "NOTICE.txt")
        if (!notice.isFile) {
            notice.writeText(
                "Live2D Sample Mao — see ${DebugAssetLicense.LIVE2D_SAMPLE_TERMS_URL}\n"
            )
        }
        return usedMirror
    }

    private suspend fun installArchive(
        baseDir: File,
        kind: DebugAssetKind,
        preferred: DebugAssetMirror,
        emit: suspend (DebugAssetDownloadEvent) -> Unit
    ): DebugAssetMirror {
        val spec = DebugAssetCatalog.spec(kind)
        val tmp = File(baseDir, "${DebugOpenSourcePaths.ROOT_DIR}/.tmp")
        tmp.mkdirs()
        val extractRoot = File(baseDir, spec.extractDirRel)
        extractRoot.mkdirs()

        var lastError: Throwable? = null
        var usedMirror = preferred
        for (officialUrl in spec.officialUrls) {
            val archiveName = officialUrl.substringAfterLast('/').ifBlank { "asset.bin" }
            val archive = File(tmp, archiveName)
            try {
                usedMirror = downloadWithMirrorFallback(
                    officialUrl = officialUrl,
                    destFile = archive,
                    preferred = preferred
                ) { downloaded, total, mirror ->
                    val pct = if (total > 0) {
                        ((downloaded * 85) / total).toInt().coerceIn(0, 85)
                    } else {
                        40
                    }
                    emit(
                        DebugAssetDownloadEvent.Progress(
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            percent = pct,
                            mirror = mirror,
                            phase = "downloading"
                        )
                    )
                }

                emit(
                    DebugAssetDownloadEvent.Progress(
                        downloadedBytes = archive.length(),
                        totalBytes = archive.length(),
                        percent = 88,
                        mirror = usedMirror,
                        phase = "extracting"
                    )
                )
                // 清空旧内容再解压
                extractRoot.listFiles()?.forEach { child ->
                    if (child.isDirectory) child.deleteRecursively() else child.delete()
                }
                ArchiveExtractor.extract(archive, extractRoot)
                archive.delete()
                return usedMirror
            } catch (ce: CancellationException) {
                archive.delete()
                throw ce
            } catch (t: Throwable) {
                lastError = t
                archive.delete()
            }
        }
        throw lastError ?: IllegalStateException("下载失败")
    }

    /**
     * 按 [DebugAssetCatalog.mirrorAttemptOrder] 尝试下载；成功返回实际使用的镜像。
     */
    private suspend fun downloadWithMirrorFallback(
        officialUrl: String,
        destFile: File,
        preferred: DebugAssetMirror,
        onProgress: suspend (downloaded: Long, total: Long, mirror: DebugAssetMirror) -> Unit
    ): DebugAssetMirror {
        var lastError: Throwable? = null
        val attempts = DebugAssetCatalog.mirrorAttemptOrder(preferred)
        for ((mirror, prefixIndex) in attempts) {
            currentCoroutineContext().ensureActive()
            val url = DebugAssetCatalog.resolveUrl(officialUrl, mirror, prefixIndex)
            try {
                if (destFile.exists()) destFile.delete()
                transport.downloadToFile(url, destFile) { downloaded, total ->
                    onProgress(downloaded, total, mirror)
                }
                if (!destFile.isFile || destFile.length() <= 0L) {
                    throw IllegalStateException("空文件")
                }
                return mirror
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastError = t
                destFile.delete()
            }
        }
        throw lastError ?: IllegalStateException("所有镜像均失败")
    }

    companion object {
        fun shortError(t: Throwable): String {
            val raw = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
            return if (raw.length <= 120) raw else raw.take(117) + "…"
        }
    }
}
