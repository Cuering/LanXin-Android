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
import kotlinx.coroutines.flow.channelFlow
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
 * - 使用 [channelFlow] + [send]：transport 进度回调可在任意协程上下文触发
 *   （Ktor `withContext`/Undispatched），避免 cold `flow` 的 emit 上下文不变量崩溃
 * - 终端事件在 try/catch **之外** send（禁止 catch 内 send/emit）
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

    /**
     * 下载并安装 [kind]；进度以 [DebugAssetDownloadEvent] 流式上报。
     *
     * 使用 [channelFlow]：`send` 可从任意协程上下文安全调用（含 transport 进度回调
     * 所在的 Undispatched / withContext 协程），不再受 cold `flow` emit 上下文约束。
     * 终端事件（Completed / Failed / Cancelled）一律在 try/catch **之外** send，
     * 避免 Flow exception transparency 违规。
     */
    fun download(
        baseDir: File,
        kind: DebugAssetKind,
        preferredMirror: DebugAssetMirror
    ): Flow<DebugAssetDownloadEvent> = channelFlow {
        send(DebugAssetDownloadEvent.Started)
        val job = currentCoroutineContext()[Job]
        activeJob = job
        // 记录终端结果，catch 内不 send（Flow exception transparency）
        var terminal: DebugAssetDownloadEvent? = null
        try {
            mutex.withLock {
                val used = when (kind) {
                    DebugAssetKind.LIVE2D -> installLive2d(baseDir, preferredMirror) { event ->
                        send(event)
                    }
                    DebugAssetKind.ASR, DebugAssetKind.TTS ->
                        installAsrOrTts(baseDir, kind, preferredMirror) { event ->
                            send(event)
                        }
                    DebugAssetKind.LOCAL_LLM ->
                        installLocalLlm(baseDir, preferredMirror) { event ->
                            send(event)
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
            // 不 rethrow：UI collect 正常收 Cancelled；不在 catch 内 send
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
        // 正常路径 send 终端事件（在 catch 块外）
        terminal?.let { send(it) }
    }.flowOn(Dispatchers.IO)

    private data class UsedSource(
        val mirror: DebugAssetMirror,
        val label: String
    )

    /**
     * 多源全部失败：携带每个源各自的错误摘要，避免 UI 只显示最后一个 HF timeout。
     */
    private class MultiSourceFailure(
        val errorsBySource: List<Pair<String, String>>,
        cause: Throwable?
    ) : Exception(formatSourceErrors(errorsBySource), cause) {
        val attempted: List<String> get() = errorsBySource.map { it.first }
    }

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
        val errorsBySource = mutableListOf<Pair<String, String>>()
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
                errorsBySource += source.label to shortError(t)
                // 保留 staging/.part：用户重试同源可续传；各源目录按 label 隔离
            }
        }
        throw MultiSourceFailure(errorsBySource, lastError)
    }

    private suspend fun installAsrOrTts(
        baseDir: File,
        kind: DebugAssetKind,
        preferred: DebugAssetMirror,
        emit: suspend (DebugAssetDownloadEvent) -> Unit
    ): UsedSource {
        val errorsBySource = mutableListOf<Pair<String, String>>()
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
                errorsBySource += source.label to shortError(t)
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
                errorsBySource += candidate.label to shortError(t)
            }
        }

        throw MultiSourceFailure(errorsBySource, lastError)
    }

    /**
     * 多文件安装：可恢复。
     * - 已完整落盘的文件跳过；
     * - 失败保留 staging 与 `*.part`（transport Range 续传）；
     * - 成功后迁入目标目录并清理 staging。
     */
    private suspend fun installMultiFileSource(
        baseDir: File,
        source: DebugAssetCatalog.MultiFileSource,
        emit: suspend (DebugAssetDownloadEvent) -> Unit
    ) {
        val staging = File(
            baseDir,
            "${DebugOpenSourcePaths.ROOT_DIR}/.tmp/mf-${source.label}-staging"
        )
        staging.mkdirs()

        val files = source.relativeFiles
        var done = 0
        // 统计已就绪文件，进度起点不为 0
        for (rel in files) {
            val existing = File(staging, rel)
            if (existing.isFile && existing.length() > 0L) done++
        }
        val alreadyDone = done
        done = 0
        for (rel in files) {
            currentCoroutineContext().ensureActive()
            val dest = File(staging, rel)
            dest.parentFile?.mkdirs()
            if (dest.isFile && dest.length() > 0L) {
                done++
                val overall = (done * 100) / files.size
                emit(
                    DebugAssetDownloadEvent.Progress(
                        downloadedBytes = done.toLong(),
                        totalBytes = files.size.toLong(),
                        percent = overall.coerceIn(0, 90),
                        mirror = source.mirror,
                        phase = if (alreadyDone > 0) "resuming" else "downloading",
                        sourceLabel = source.label
                    )
                )
                continue
            }
            val url = "${source.baseUrl.trimEnd('/')}/$rel"
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
        // 迁入前去掉残留 .part
        staging.walkTopDown().filter { it.isFile && it.name.endsWith(".part") }
            .forEach { runCatching { it.delete() } }
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
            // 保留 .part / 已下字节，由 transport Range 续传
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
        val errorsBySource = mutableListOf<Pair<String, String>>()
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
                errorsBySource += candidate.label to shortError(t)
                destFile.delete()
            }
        }
        throw MultiSourceFailure(errorsBySource, lastError)
    }

    companion object {
        fun shortError(t: Throwable): String {
            // MultiSourceFailure 已聚合，直接用其 message
            if (t is MultiSourceFailure) {
                val msg = t.message.orEmpty()
                return if (msg.length <= 220) msg else msg.take(217) + "…"
            }
            val root = generateSequence(t) { it.cause }.last()
            val raw = root.message?.takeIf { it.isNotBlank() } ?: root.javaClass.simpleName
            // 去掉冗长 URL 路径，保留关键超时文案
            val compact = compactTimeoutMessage(raw)
            return if (compact.length <= 160) compact else compact.take(157) + "…"
        }

        /**
         * 压缩 Ktor/CIO timeout 文案：
         * `Connect timeout has expired [url=https://…/config.json, connect_timeout=unknown ms]`
         * → `Connect timeout (config.json)`
         */
        fun compactTimeoutMessage(raw: String): String {
            val lower = raw.lowercase()
            if (!lower.contains("timeout")) return raw
            val file = Regex("""/([^/\s?]+)\s*[,)]""")
                .findAll(raw)
                .map { it.groupValues[1] }
                .lastOrNull()
            val kind = when {
                lower.contains("connect timeout") -> "Connect timeout"
                lower.contains("socket timeout") -> "Socket timeout"
                lower.contains("request timeout") -> "Request timeout"
                else -> "Timeout"
            }
            return if (file != null) "$kind ($file)" else kind
        }

        /**
         * 格式化「源:错误」列表，供 MultiSourceFailure / UI 展示。
         * 例：`modelscope:Connect timeout; hf-mirror:HTTP 403; huggingface:Connect timeout`
         */
        fun formatSourceErrors(errorsBySource: List<Pair<String, String>>): String {
            if (errorsBySource.isEmpty()) return "所有镜像均失败"
            return errorsBySource
                .distinctBy { it.first }
                .take(6)
                .joinToString("; ") { (label, err) ->
                    val short = err.take(48).let { if (err.length > 48) "$it…" else it }
                    "$label:$short"
                }
        }

        fun failMessage(
            kind: DebugAssetKind,
            short: String,
            attempted: List<String>
        ): String {
            // short 若已含「源:错误」聚合则不再重复拼「已试」
            val alreadyDetailed = short.contains(':') &&
                attempted.any { short.contains(it) }
            val base = when {
                alreadyDetailed -> short
                attempted.isEmpty() -> short
                else -> {
                    val src = attempted.distinct().take(6).joinToString(",")
                    "$short（已试:$src）"
                }
            }
            return when (kind) {
                DebugAssetKind.LIVE2D -> {
                    val msg = "$base。${DebugAssetCatalog.LIVE2D_DOWNLOAD_FAIL_HINT}"
                    if (msg.length <= 240) msg else msg.take(237) + "…"
                }
                DebugAssetKind.LOCAL_LLM -> {
                    val msg = "$base。${DebugAssetCatalog.LOCAL_LLM_DOWNLOAD_FAIL_HINT}"
                    if (msg.length <= 280) msg else msg.take(277) + "…"
                }
                else -> if (base.length <= 200) base else base.take(197) + "…"
            }
        }
    }
}
