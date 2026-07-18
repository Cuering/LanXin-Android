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
 * - Live2D：jsDelivr → fastly → github-raw（单文件）；内置 Mao 优先，下载可选
 * - ASR / TTS：HF / hf-mirror 目录文件优先；GitHub release 归档回退
 * - LOCAL_LLM：ModelScope → hf-mirror → HuggingFace（MNN 运行时文件）
 * - 终端事件在 try/catch **之外** emit（Flow exception transparency）
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
            DebugAssetKind.LOCAL_LLM ->
                DebugOpenSourcePaths.isLocalLlmDirReady(File(path))
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
            DebugAssetKind.LOCAL_LLM -> {
                val d = DebugOpenSourcePaths.localLlmModelDir(baseDir)
                if (DebugOpenSourcePaths.isLocalLlmDirReady(d)) d.absolutePath else ""
            }
        }
    }

    fun download(
        baseDir: File,
        kind: DebugAssetKind,
        preferredMirror: DebugAssetMirror
    ): Flow<DebugAssetDownloadEvent> = flow {
        emit(DebugAssetDownloadEvent.Started)
        val job = currentCoroutineContext()[Job]
        activeJob = job
        var terminal: DebugAssetDownloadEvent? = null
        try {
            mutex.withLock {
                val used = when (kind) {
                    DebugAssetKind.LIVE2D -> installLive2d(baseDir, preferredMirror) { event ->
                        emit(event)
                    }
                    DebugAssetKind.ASR, DebugAssetKind.TTS ->
                        installAsrOrTts(baseDir, kind, preferredMirror) { event ->
                            emit(event)
                        }
                    DebugAssetKind.LOCAL_LLM ->
                        installLocalLlm(baseDir, preferredMirror) { event ->
                            emit(event)
                        }
                }
                val path = readyPath(baseDir, kind)
                terminal = if (path.isBlank() || !isReady(baseDir, kind)) {
                    DebugAssetDownloadEvent.Failed(
                        kind = kind,
                        message = failMessage(
                            kind,
                            "下载完成但校验失败（缺少关键文件）",
                            emptyList()
                        ),
                        attemptedSources = emptyList()
                    )
                } else {
                    DebugAssetDownloadEvent.Completed(
                        kind = kind,
                        readyPath = path,
                        mirror = used.mirror,
                        sourceLabel = used.label
                    )
                }
            }
        } catch (_: CancellationException) {
            terminal = DebugAssetDownloadEvent.Cancelled
        } catch (t: Throwable) {
            val attempted = (t as? MultiSourceFailure)?.attempted.orEmpty()
            terminal = DebugAssetDownloadEvent.Failed(
                kind = kind,
                message = failMessage(kind, shortError(t), attempted),
                attemptedSources = attempted
            )
        } finally {
            if (activeJob === job) activeJob = null
        }
        terminal?.let { emit(it) }
    }.flowOn(Dispatchers.IO)

    private data class UsedSource(
        val mirror: DebugAssetMirror,
        val label: String
    )

    private class MultiSourceFailure(
        cause: Throwable,
        val attempted: List<String>
    ) : Exception(cause.message, cause)

    private suspend fun installLive2d(
        baseDir: File,
        preferred: DebugAssetMirror,
        emit: suspend (DebugAssetDownloadEvent) -> Unit
    ): UsedSource {
        val root = File(baseDir, DebugAssetCatalog.live2d.extractDirRel)
        val staging = File(baseDir, "${DebugOpenSourcePaths.ROOT_DIR}/.tmp/live2d-mao-staging")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()

        val files = DebugAssetCatalog.live2dMaoRelativeFiles
        var used = UsedSource(preferred, preferred.name)
        var done = 0
        for (rel in files) {
            currentCoroutineContext().ensureActive()
            val dest = File(staging, rel)
            dest.parentFile?.mkdirs()
            used = downloadFromCandidates(
                candidates = DebugAssetCatalog.live2dFileCandidates(rel, preferred),
                destFile = dest
            ) { downloaded, total, candidate ->
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
                        mirror = candidate.mirror,
                        phase = "live2d-files",
                        sourceLabel = candidate.label
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
                mirror = used.mirror,
                phase = "extracting",
                sourceLabel = used.label
            )
        )
        if (root.exists()) root.deleteRecursively()
        root.parentFile?.mkdirs()
        if (!staging.renameTo(root)) {
            staging.copyRecursively(root, overwrite = true)
            staging.deleteRecursively()
        }
        val notice = File(root, "NOTICE.txt")
        if (!notice.isFile) {
            notice.writeText(
                "Live2D Sample Mao — see ${DebugAssetLicense.LIVE2D_SAMPLE_TERMS_URL}\n" +
                    "In-app download is optional; APK ships builtin assets/pet/live2d/Mao.\n" +
                    "Source: ${used.label}\n"
            )
        }
        return used
    }

    private suspend fun installLocalLlm(
        baseDir: File,
        preferred: DebugAssetMirror,
        emit: suspend (DebugAssetDownloadEvent) -> Unit
    ): UsedSource {
        val attempted = mutableListOf<String>()
        var lastError: Throwable? = null
        val multi = DebugAssetCatalog.localLlmMultiFileSources(preferred)
        for (source in multi) {
            currentCoroutineContext().ensureActive()
            try {
                installMultiFileSource(baseDir, source, emit)
                val dir = File(baseDir, source.modelDirRel)
                if (!DebugOpenSourcePaths.isLocalLlmDirReady(dir)) {
                    throw IllegalStateException("本地脑校验失败：缺少 llm.mnn")
                }
                return UsedSource(source.mirror, source.label)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastError = t
                attempted += source.label
            }
        }
        throw MultiSourceFailure(
            lastError ?: IllegalStateException("本地脑下载失败"),
            attempted
        )
    }

    private suspend fun installAsrOrTts(
        baseDir: File,
        kind: DebugAssetKind,
        preferred: DebugAssetMirror,
        emit: suspend (DebugAssetDownloadEvent) -> Unit
    ): UsedSource {
        val attempted = mutableListOf<String>()
        var lastError: Throwable? = null

        val multi = when (kind) {
            DebugAssetKind.ASR -> DebugAssetCatalog.asrMultiFileSources(preferred)
            DebugAssetKind.TTS -> DebugAssetCatalog.ttsMultiFileSources(preferred)
            else -> emptyList()
        }
        for (source in multi) {
            currentCoroutineContext().ensureActive()
            try {
                installMultiFileSource(baseDir, source, emit)
                return UsedSource(source.mirror, source.label)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastError = t
                attempted += source.label
            }
        }

        // GitHub release 归档回退
        val archives = DebugAssetCatalog.archiveCandidates(kind, preferred)
        for (candidate in archives) {
            currentCoroutineContext().ensureActive()
            try {
                installArchiveUrl(baseDir, kind, candidate, emit)
                return UsedSource(candidate.mirror, candidate.label)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastError = t
                attempted += candidate.label
            }
        }

        throw MultiSourceFailure(
            lastError ?: IllegalStateException("下载失败"),
            attempted
        )
    }

    private suspend fun installMultiFileSource(
        baseDir: File,
        source: DebugAssetCatalog.MultiFileSource,
        emit: suspend (DebugAssetDownloadEvent) -> Unit
    ) {
        val staging = File(
            baseDir,
            "${DebugOpenSourcePaths.ROOT_DIR}/.tmp/mf-${source.label}-staging"
        )
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()

        val files = source.relativeFiles
        var done = 0
        for (rel in files) {
            currentCoroutineContext().ensureActive()
            val url = "${source.baseUrl.trimEnd('/')}/$rel"
            val dest = File(staging, rel)
            dest.parentFile?.mkdirs()
            transport.downloadToFile(url, dest) { downloaded, total ->
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
                        percent = overall.coerceIn(0, 90),
                        mirror = source.mirror,
                        phase = "downloading",
                        sourceLabel = source.label
                    )
                )
            }
            if (!dest.isFile || dest.length() <= 0L) {
                throw IllegalStateException("空文件 $rel @ ${source.label}")
            }
            done++
        }

        emit(
            DebugAssetDownloadEvent.Progress(
                downloadedBytes = files.size.toLong(),
                totalBytes = files.size.toLong(),
                percent = 95,
                mirror = source.mirror,
                phase = "extracting",
                sourceLabel = source.label
            )
        )

        val target = File(baseDir, source.modelDirRel)
        if (target.exists()) target.deleteRecursively()
        target.parentFile?.mkdirs()
        if (!staging.renameTo(target)) {
            staging.copyRecursively(target, overwrite = true)
            staging.deleteRecursively()
        }
        val ok = when {
            source.modelDirRel.contains("local-llm") ->
                DebugOpenSourcePaths.isLocalLlmDirReady(target)
            else -> DebugOpenSourcePaths.isModelDirReady(target)
        }
        if (!ok) {
            throw IllegalStateException("多文件安装后目录未就绪：${target.absolutePath}")
        }
    }

    private suspend fun installArchiveUrl(
        baseDir: File,
        kind: DebugAssetKind,
        candidate: DebugAssetUrlCandidate,
        emit: suspend (DebugAssetDownloadEvent) -> Unit
    ) {
        val spec = DebugAssetCatalog.spec(kind)
        val tmp = File(baseDir, "${DebugOpenSourcePaths.ROOT_DIR}/.tmp")
        tmp.mkdirs()
        val extractRoot = File(baseDir, spec.extractDirRel)
        extractRoot.mkdirs()

        val archiveName = candidate.url.substringAfterLast('/').ifBlank { "asset.bin" }
            .substringBefore('?')
        val archive = File(tmp, archiveName)
        try {
            if (archive.exists()) archive.delete()
            transport.downloadToFile(candidate.url, archive) { downloaded, total ->
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
                        mirror = candidate.mirror,
                        phase = "downloading",
                        sourceLabel = candidate.label
                    )
                )
            }
            if (!archive.isFile || archive.length() <= 0L) {
                throw IllegalStateException("空归档")
            }
            emit(
                DebugAssetDownloadEvent.Progress(
                    downloadedBytes = archive.length(),
                    totalBytes = archive.length(),
                    percent = 88,
                    mirror = candidate.mirror,
                    phase = "extracting",
                    sourceLabel = candidate.label
                )
            )
            extractRoot.listFiles()?.forEach { child ->
                if (child.isDirectory) child.deleteRecursively() else child.delete()
            }
            ArchiveExtractor.extract(archive, extractRoot)
        } finally {
            archive.delete()
        }
    }

    private suspend fun downloadFromCandidates(
        candidates: List<DebugAssetUrlCandidate>,
        destFile: File,
        onProgress: suspend (
            downloaded: Long,
            total: Long,
            candidate: DebugAssetUrlCandidate
        ) -> Unit
    ): UsedSource {
        var lastError: Throwable? = null
        val attempted = mutableListOf<String>()
        for (candidate in candidates) {
            currentCoroutineContext().ensureActive()
            try {
                if (destFile.exists()) destFile.delete()
                transport.downloadToFile(candidate.url, destFile) { downloaded, total ->
                    onProgress(downloaded, total, candidate)
                }
                if (!destFile.isFile || destFile.length() <= 0L) {
                    throw IllegalStateException("空文件")
                }
                return UsedSource(candidate.mirror, candidate.label)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastError = t
                attempted += candidate.label
                destFile.delete()
            }
        }
        throw MultiSourceFailure(
            lastError ?: IllegalStateException("所有镜像均失败"),
            attempted
        )
    }

    companion object {
        fun shortError(t: Throwable): String {
            val root = generateSequence(t) { it.cause }.last()
            val raw = root.message?.takeIf { it.isNotBlank() } ?: root.javaClass.simpleName
            return if (raw.length <= 160) raw else raw.take(157) + "…"
        }

        fun failMessage(
            kind: DebugAssetKind,
            short: String,
            attempted: List<String>
        ): String {
            val base = if (attempted.isEmpty()) short else {
                val src = attempted.distinct().take(6).joinToString(",")
                "$short（已试:$src）"
            }
            return when (kind) {
                DebugAssetKind.LIVE2D -> {
                    val msg = "$base。${DebugAssetCatalog.LIVE2D_DOWNLOAD_FAIL_HINT}"
                    if (msg.length <= 220) msg else msg.take(217) + "…"
                }
                else -> if (base.length <= 180) base else base.take(177) + "…"
            }
        }
    }
}
