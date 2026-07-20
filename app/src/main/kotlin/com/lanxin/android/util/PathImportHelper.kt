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

package com.lanxin.android.util

import java.io.File

/**
 * 用户自选资源导入：路径摘要与私有目录约定（纯逻辑，可单测）。
 *
 * 落盘根：`filesDir/user-picked/<kind>/...`
 * 底层引擎只吃文件系统路径时，由 [LocalPathImporter] 从 SAF Uri 拷入此处。
 */
object PathImportHelper {

    const val ROOT_DIR = "user-picked"

    enum class Kind(val dirName: String) {
        LIVE2D("live2d"),
        ASR("asr"),
        TTS_DIR("tts"),
        TTS_REF("tts-ref"),
        LOCAL_LLM("local-llm"),

        /** 知识库文件夹导入暂存（可选；当前以 Document 树直接读为主）。 */
        KNOWLEDGE("knowledge")
    }

    /** 某类资源的私有根目录（每次导入可再分子目录）。 */
    fun kindRoot(filesDir: File, kind: Kind): File = File(filesDir, "$ROOT_DIR/${kind.dirName}")

    /**
     * 单次导入目标目录：带时间戳，避免覆盖。
     */
    fun newImportDir(filesDir: File, kind: Kind, stampMs: Long = System.currentTimeMillis()): File {
        return File(kindRoot(filesDir, kind), "import_$stampMs")
    }

    /**
     * UI 摘要：空 →「未选择」；否则末段文件名 + 可选截断绝对路径。
     */
    fun shortSummary(path: String, maxTail: Int = 42): String {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return "未选择"
        if (trimmed.startsWith("stub://")) return trimmed
        val name = trimmed.substringAfterLast('/').ifBlank { trimmed }
        if (trimmed.length <= maxTail) return trimmed
        val tail = trimmed.takeLast(maxTail)
        return "$name · …$tail"
    }

    /** 展示用文件名（无路径时返回空串）。 */
    fun displayFileName(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith("stub://")) return trimmed
        return trimmed.substringAfterLast('/').ifBlank { trimmed }
    }

    /**
     * 在目录树中找第一个 `*.model3.json`（深度优先，按名字排序保证稳定）。
     */
    fun findModel3Json(root: File): File? {
        if (!root.exists()) return null
        if (root.isFile) {
            return if (root.name.endsWith(".model3.json", ignoreCase = true)) root else null
        }
        val kids = root.listFiles()?.sortedBy { it.name.lowercase() } ?: return null
        for (f in kids) {
            if (f.isFile && f.name.endsWith(".model3.json", ignoreCase = true)) return f
        }
        for (d in kids) {
            if (d.isDirectory) {
                findModel3Json(d)?.let { return it }
            }
        }
        return null
    }

    /**
     * 在目录树中定位可用的本地 LLM 模型包根目录（含 config + 权重）。
     * 深度优先，名字排序保证稳定。
     */
    fun findLocalLlmPackageDir(root: File): File? {
        if (!root.exists()) return null
        if (root.isFile) {
            return root.parentFile?.takeIf { localLlmPackageIssue(it.absolutePath) == null }
        }
        if (localLlmPackageIssue(root.absolutePath) == null) return root
        val kids = root.listFiles()?.sortedBy { it.name.lowercase() } ?: return null
        for (d in kids) {
            if (d.isDirectory) {
                findLocalLlmPackageDir(d)?.let { return it }
            }
        }
        return null
    }

    /**
     * 解析引擎应加载的路径：优先 config.json，其次包目录，再次原路径。
     */
    fun resolveLocalLlmLoadPath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isBlank() || trimmed.startsWith("stub://")) return trimmed
        val f = File(trimmed)
        if (!f.exists()) return trimmed
        val pkg = when {
            f.isDirectory -> findLocalLlmPackageDir(f) ?: f
            f.isFile -> f.parentFile?.let { findLocalLlmPackageDir(it) } ?: f.parentFile ?: f
            else -> f
        }
        val config = listOf("config.json", "llm_config.json", "llm.mnn.json")
            .map { File(pkg, it) }
            .firstOrNull { it.isFile }
        return config?.absolutePath ?: pkg.absolutePath
    }

    /**
     * 校验本地 LLM 模型包是否基本完整。
     *
     * @return null 表示可尝试 native load；否则为可读错误原因
     */
    fun localLlmPackageIssue(path: String): String? {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return "path_empty"
        if (trimmed.startsWith("stub://")) return null
        val f = File(trimmed)
        if (!f.exists()) return "path_missing:$trimmed"

        val dir: File
        val focusFile: File?
        when {
            f.isDirectory -> {
                dir = f
                focusFile = null
            }
            f.isFile -> {
                dir = f.parentFile ?: return "path_invalid_parent"
                focusFile = f
            }
            else -> return "path_invalid"
        }

        val configFiles = listOf("config.json", "llm_config.json", "llm.mnn.json")
        val hasConfig = configFiles.any { File(dir, it).isFile } ||
            (focusFile != null && focusFile.name.endsWith(".json", ignoreCase = true))
        val hasWeight = dir.listFiles()?.any {
            it.isFile && it.name.endsWith(".mnn", ignoreCase = true)
        } == true ||
            (focusFile != null && focusFile.name.endsWith(".mnn", ignoreCase = true))

        if (focusFile != null &&
            focusFile.name.endsWith(".mnn", ignoreCase = true) &&
            !hasConfig
        ) {
            return "missing_config: 仅导入了 ${focusFile.name}。" +
                "MNN 本地脑需要完整模型文件夹（config.json + *.mnn + tokenizer），请用「选择文件夹」。"
        }
        if (!hasConfig) {
            return "missing_config: 目录中无 config.json / llm_config.json"
        }
        if (!hasWeight) {
            return "missing_weight: 目录中无 *.mnn 权重"
        }
        return null
    }

    fun sanitizeFileName(raw: String): String {
        val base = raw.trim().ifBlank { "file" }
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_")
            .take(180)
        return base.ifBlank { "file" }
    }
}
