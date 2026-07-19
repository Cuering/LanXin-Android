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

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Debug 资源落盘根目录解析。
 *
 * **自动建统一可访问目录**：解析时确保 `LanXin/` 与标准子目录
 *（live2d / asr / tts / models / backgrounds / music）存在，文件管理器可直接打开。
 *
 * 优先公共共享存储下的 `LanXin/`（或 `Documents/LanXin/`），
 * 写失败时回退到 `getExternalFilesDir()/LanXin/`（仍比内部 filesDir 更易访问）。
 *
 * 不申请 MANAGE_EXTERNAL_STORAGE；仅最小必要尝试 + 优雅回退。
 * Android 10+ 公共根常不可写：用户可在设置页通过 SAF 授权公共 `LanXin/`
 *（[LanXinSafTree]）。引擎主路径仍写可 File 访问的目录；授权成功后
 * **必须**把下载结果镜像到公共树，失败要可见，避免 UI 显示「已授权」却只落私有目录。
 *
 * @see LanXinSafTree
 * @see docs/debug-assets.md
 */
object DebugAssetStorage {

    data class Root(
        /** 传给 [DebugAssetDownloader] 的 base（其下再挂 [DebugOpenSourcePaths.ROOT_DIR]）。 */
        val baseDir: File,
        /** 实际 `…/LanXin` 目录。 */
        val lanXinDir: File,
        /** true = 未用上公共存储根 File 直写，走了 App 外部 files 回退。 */
        val usedFallback: Boolean,
        /** 展示给用户的绝对路径。 */
        val displayPath: String,
        /**
         * 用户 SAF 授权的公共树 Uri（可空）。
         * 有授权时 [safGranted] = true；File 仍可能 usedFallback。
         */
        val safTreeUri: String = "",
        val safGranted: Boolean = false,
        val safWritable: Boolean = false,
        val safDisplayLabel: String = "",
        /**
         * 最近一次镜像结果说明（成功/失败/跳过）；供 UI snackbar 展示，避免静默。
         */
        val lastMirrorNote: String = "",
        /** 已确保的标准子目录个数（相对 [lanXinDir]）。 */
        val structureDirCount: Int = 0
    ) {
        /** 公共 File 可写，或 SAF 树可写。 */
        val publicWritable: Boolean get() = !usedFallback || safWritable

        /** 下载后是否需要把 File 结果镜像到公共 SAF 树。 */
        val shouldMirrorToSaf: Boolean
            get() = usedFallback && safWritable && safTreeUri.isNotBlank()
    }

    /**
     * 解析下载根，并 **自动创建** `LanXin/` 标准子目录。
     * - 优先：`Environment.getExternalStorageDirectory()/LanXin`（File 直写）
     * - 其次：`…/Documents/LanXin`（标准文档树，文件管理器更易发现）
     * - 回退：`context.getExternalFilesDir(null)/LanXin`
     * - 再回退：`context.filesDir/LanXin`（极少）
     * - 叠加：DataStore 中的 SAF 树 Uri（[LanXinSafTree.PREFS_KEY] 由调用方传入）
     * - 若 SAF 可写：同步在公共树下创建同样子目录骨架
     *
     * 注意：SAF 授权**不会**把 [baseDir] 改成 content Uri（引擎要 File 路径），
     * 但 [shouldMirrorToSaf] 为 true 时下载完成后必须镜像到公共树。
     */
    fun resolve(context: Context, safTreeUri: String? = null): Root {
        val probe = LanXinSafTree.probe(context, safTreeUri)

        // 公共 File 候选：根 LanXin → Documents/LanXin
        for (candidate in publicLanXinCandidates()) {
            if (ensureWritableDir(candidate.lanXinDir)) {
                val structureCount = ensureLanXinStructure(candidate.lanXinDir)
                // SAF 可写时也在公共树建骨架（用户文件管理器看到空目录即可知布局）
                if (probe.writable) {
                    LanXinSafTree.ensureStructure(context, probe.treeUri)
                }
                return Root(
                    baseDir = candidate.baseDir,
                    lanXinDir = candidate.lanXinDir,
                    usedFallback = false,
                    displayPath = if (probe.writable) {
                        "${candidate.lanXinDir.absolutePath}（公共可写）"
                    } else {
                        candidate.lanXinDir.absolutePath
                    },
                    safTreeUri = probe.treeUri,
                    safGranted = probe.granted,
                    safWritable = probe.writable,
                    safDisplayLabel = probe.displayLabel,
                    structureDirCount = structureCount
                )
            }
        }

        val externalFiles = context.getExternalFilesDir(null)
        if (externalFiles != null) {
            val fallbackLanXin = File(externalFiles, DebugOpenSourcePaths.ROOT_DIR)
            if (ensureWritableDir(fallbackLanXin)) {
                val structureCount = ensureLanXinStructure(fallbackLanXin)
                if (probe.writable) {
                    LanXinSafTree.ensureStructure(context, probe.treeUri)
                }
                // 授权可写时明确告知：引擎写 App 私有，完成后会镜像到公共树
                val display = when {
                    probe.writable ->
                        "引擎：${fallbackLanXin.absolutePath} → 镜像公共 ${probe.displayLabel}"
                    probe.granted ->
                        "引擎：${fallbackLanXin.absolutePath}（SAF 已授权但不可写：${probe.displayLabel}）"
                    else ->
                        "${fallbackLanXin.absolutePath}（公共不可写，已自动建 App 内 LanXin/ 骨架；可授权公共目录）"
                }
                return Root(
                    baseDir = externalFiles,
                    lanXinDir = fallbackLanXin,
                    usedFallback = true,
                    displayPath = display,
                    safTreeUri = probe.treeUri,
                    safGranted = probe.granted,
                    safWritable = probe.writable,
                    safDisplayLabel = probe.displayLabel,
                    structureDirCount = structureCount
                )
            }
        }

        val internalBase = context.filesDir
        val last = File(internalBase, DebugOpenSourcePaths.ROOT_DIR)
        last.mkdirs()
        val structureCount = ensureLanXinStructure(last)
        if (probe.writable) {
            LanXinSafTree.ensureStructure(context, probe.treeUri)
        }
        val display = when {
            probe.writable ->
                "引擎：${last.absolutePath} → 镜像公共 ${probe.displayLabel}"
            else -> last.absolutePath
        }
        return Root(
            baseDir = internalBase,
            lanXinDir = last,
            usedFallback = true,
            displayPath = display,
            safTreeUri = probe.treeUri,
            safGranted = probe.granted,
            safWritable = probe.writable,
            safDisplayLabel = probe.displayLabel,
            structureDirCount = structureCount
        )
    }

    /** 单测 / 无 Context：直接把 [baseDir] 当作下载 base，并建标准子目录。 */
    fun fromBaseDir(baseDir: File): Root {
        val lanXin = File(baseDir, DebugOpenSourcePaths.ROOT_DIR)
        lanXin.mkdirs()
        val structureCount = ensureLanXinStructure(lanXin)
        return Root(
            baseDir = baseDir,
            lanXinDir = lanXin,
            usedFallback = false,
            displayPath = lanXin.absolutePath,
            structureDirCount = structureCount
        )
    }

    /**
     * 确保 `LanXin/` 下标准子目录存在（幂等）。
     * @return 成功存在的子目录个数
     */
    fun ensureLanXinStructure(lanXinDir: File): Int {
        if (!lanXinDir.exists() && !lanXinDir.mkdirs()) return 0
        if (!lanXinDir.isDirectory) return 0
        var count = 0
        for (rel in DebugOpenSourcePaths.STANDARD_SUBDIRS) {
            val dir = File(lanXinDir, rel)
            try {
                if (dir.isDirectory || dir.mkdirs()) {
                    count++
                }
            } catch (_: Throwable) {
                // 单目录失败不阻断其它
            }
        }
        return count
    }

    /**
     * 公共 File 候选（按优先级）：
     * 1. `{external}/LanXin` → baseDir=external，relativeReadyPath=`LanXin/...`
     * 2. `{external}/Documents/LanXin` → baseDir=Documents，relative 同为 `LanXin/...`
     *
     * [PublicCandidate.baseDir] 始终是 `LanXin` 的父目录，保证
     * `File(baseDir, "LanXin/asr/…")` 与 [lanXinDir] 一致。
     */
    data class PublicCandidate(val baseDir: File, val lanXinDir: File)

    fun publicLanXinCandidates(
        externalRoot: File? = Environment.getExternalStorageDirectory()
    ): List<PublicCandidate> {
        if (externalRoot == null) return emptyList()
        val rootName = DebugOpenSourcePaths.ROOT_DIR
        val direct = PublicCandidate(
            baseDir = externalRoot,
            lanXinDir = File(externalRoot, rootName)
        )
        // 字面量 "Documents"：JVM 单测 android.jar 上 DIRECTORY_DOCUMENTS 可能为 null
        val docsName = Environment.DIRECTORY_DOCUMENTS?.takeIf { it.isNotBlank() } ?: "Documents"
        val documents = File(externalRoot, docsName)
        val underDocs = PublicCandidate(
            baseDir = documents,
            lanXinDir = File(documents, rootName)
        )
        return listOf(direct, underDocs)
    }

    /**
     * 是否应在下载完成后镜像到 SAF（与 [mirrorToSafIfNeeded] / [mirrorReadyPathToSaf] 门控一致）。
     * 纯逻辑，供单测锁定契约。
     */
    fun shouldMirror(root: Root): Boolean = root.shouldMirrorToSaf

    /**
     * 下载完成后：若 File 走了回退且 SAF 可写，把 [localFile] 镜像到树。
     * @return 镜像成功的 document Uri，或 null
     */
    fun mirrorToSafIfNeeded(
        context: Context,
        root: Root,
        localFile: File,
        relativeUnderLanXin: String
    ): String? {
        if (!shouldMirror(root)) return null
        if (!localFile.isFile || localFile.length() <= 0L) return null
        return LanXinSafTree.mirrorFile(
            context = context,
            treeUriString = root.safTreeUri,
            file = localFile,
            relativeUnderLanXin = relativeUnderLanXin
        )
    }

    /**
     * 下载完成后把就绪路径（文件或目录）镜像到公共 SAF 树。
     *
     * - 文件：单文件镜像
     * - 目录：递归镜像全部非空文件
     * - 失败不抛异常，返回 [MirrorResult] 供 UI 展示（**禁止静默**）
     */
    data class MirrorResult(
        val attempted: Boolean,
        val success: Boolean,
        val mirroredCount: Int,
        val message: String
    ) {
        companion object {
            val SKIPPED = MirrorResult(
                attempted = false,
                success = true,
                mirroredCount = 0,
                message = ""
            )
        }
    }

    fun mirrorReadyPathToSaf(
        context: Context,
        root: Root,
        readyPath: String
    ): MirrorResult {
        if (!shouldMirror(root)) return MirrorResult.SKIPPED
        val target = File(readyPath)
        if (!target.exists()) {
            return MirrorResult(
                attempted = true,
                success = false,
                mirroredCount = 0,
                message = "镜像失败：就绪路径不存在 $readyPath"
            )
        }
        return try {
            if (target.isFile) {
                val rel = LanXinSafTree.relativeUnderLanXin(
                    target.absolutePath,
                    root.lanXinDir
                )
                if (rel == null) {
                    return MirrorResult(
                        attempted = true,
                        success = false,
                        mirroredCount = 0,
                        message = "镜像失败：路径不在 LanXin 下 ${target.absolutePath}"
                    )
                }
                val uri = LanXinSafTree.mirrorFile(
                    context = context,
                    treeUriString = root.safTreeUri,
                    file = target,
                    relativeUnderLanXin = rel
                )
                if (uri != null) {
                    MirrorResult(
                        attempted = true,
                        success = true,
                        mirroredCount = 1,
                        message = "已同步到公共目录 ${root.safDisplayLabel.ifBlank { "LanXin" }}：$rel"
                    )
                } else {
                    MirrorResult(
                        attempted = true,
                        success = false,
                        mirroredCount = 0,
                        message = "镜像到公共目录失败（$rel）。引擎仍可用 App 私有路径；请重新授权公共 LanXin"
                    )
                }
            } else if (target.isDirectory) {
                val rel = LanXinSafTree.relativeUnderLanXin(
                    target.absolutePath,
                    root.lanXinDir
                ) ?: target.name
                val count = LanXinSafTree.mirrorDirectory(
                    context = context,
                    treeUriString = root.safTreeUri,
                    dir = target,
                    relativeUnderLanXin = rel
                )
                if (count > 0) {
                    MirrorResult(
                        attempted = true,
                        success = true,
                        mirroredCount = count,
                        message = "已同步 $count 个文件到公共目录 ${root.safDisplayLabel.ifBlank { "LanXin" }}（$rel）"
                    )
                } else {
                    MirrorResult(
                        attempted = true,
                        success = false,
                        mirroredCount = 0,
                        message = "镜像目录到公共树失败（$rel）。引擎仍可用 App 私有路径；请重新授权公共 LanXin"
                    )
                }
            } else {
                MirrorResult(
                    attempted = true,
                    success = false,
                    mirroredCount = 0,
                    message = "镜像失败：未知路径类型 $readyPath"
                )
            }
        } catch (t: Throwable) {
            MirrorResult(
                attempted = true,
                success = false,
                mirroredCount = 0,
                message = "镜像异常：${t.message ?: t.javaClass.simpleName}"
            )
        }
    }

    private fun ensureWritableDir(dir: File): Boolean {
        return try {
            if (!dir.exists() && !dir.mkdirs()) return false
            if (!dir.isDirectory) return false
            val probe = File(dir, ".lanxin_write_probe")
            probe.writeText("ok")
            probe.delete()
            true
        } catch (_: Throwable) {
            false
        }
    }
}
