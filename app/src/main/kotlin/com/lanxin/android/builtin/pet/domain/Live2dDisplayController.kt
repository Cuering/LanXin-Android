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

/**
 * M2b Live2D 显示模式决策（纯逻辑，可单测）。
 *
 * - 路径未就绪 → [Live2dDisplayMode.PLACEHOLDER]
 * - model3 可读且结构合理 → [Live2dDisplayMode.LIVE2D_SHELL]（WebView 渲染壳）
 * - 配置了路径但无效 / 解析失败 → [Live2dDisplayMode.FALLBACK]
 *
 * 官方 Sample Mao 已进 APK assets；运行时拷到 filesDir 后 WebView 用 file://。
 * 完整 Cubism Core 渲染属后续增强，本阶段保证「有路径就进壳，失败降级」。
 */
object Live2dDisplayController {

    enum class Live2dDisplayMode {
        /** 无路径 / 未配置：emoji 占位。 */
        PLACEHOLDER,

        /** model3 就绪：WebView Live2D 渲染壳。 */
        LIVE2D_SHELL,

        /** 路径无效或 model3 不可用：降级占位 + 错误提示。 */
        FALLBACK
    }

    data class Decision(
        val mode: Live2dDisplayMode,
        val model3Path: String,
        val model3FileUrl: String,
        val modelDirFileUrl: String,
        val reason: String,
        val shortLabel: String
    )

    /**
     * @param resolvedPath [PetResourceResolver] 解析后的绝对路径（可空）
     * @param model3Readable 单测可注入；默认读文件系统
     * @param model3LooksValid 单测可注入；默认轻量校验 JSON 含 Version/FileReferences
     */
    fun decide(
        resolvedPath: String,
        model3Readable: (File) -> Boolean = { it.isFile && it.canRead() && it.length() > 0L },
        model3LooksValid: (File) -> Boolean = { looksLikeModel3(it) }
    ): Decision {
        val trimmed = resolvedPath.trim()
        if (trimmed.isBlank()) {
            return Decision(
                mode = Live2dDisplayMode.PLACEHOLDER,
                model3Path = "",
                model3FileUrl = "",
                modelDirFileUrl = "",
                reason = "live2d_path_empty",
                shortLabel = "占位"
            )
        }
        if (trimmed.startsWith("stub://")) {
            return Decision(
                mode = Live2dDisplayMode.PLACEHOLDER,
                model3Path = trimmed,
                model3FileUrl = "",
                modelDirFileUrl = "",
                reason = "live2d_stub",
                shortLabel = "占位（stub）"
            )
        }
        // 仓内 Sample 逻辑路径：WebView 可用 android_asset；ensure 后走绝对路径
        if (BuiltInLive2dAssets.pathLooksBuiltin(trimmed) && !File(trimmed).isFile) {
            return Decision(
                mode = Live2dDisplayMode.LIVE2D_SHELL,
                model3Path = trimmed,
                model3FileUrl = "file:///android_asset/${BuiltInLive2dAssets.MODEL3_ASSET}",
                modelDirFileUrl = "file:///android_asset/${BuiltInLive2dAssets.ASSET_ROOT}/",
                reason = "live2d_builtin_pending_install",
                shortLabel = "Live2D 壳（内置）"
            )
        }

        val file = File(trimmed)
        if (!model3Readable(file)) {
            return Decision(
                mode = Live2dDisplayMode.FALLBACK,
                model3Path = trimmed,
                model3FileUrl = "",
                modelDirFileUrl = "",
                reason = "live2d_file_missing",
                shortLabel = "降级（路径无效）"
            )
        }
        if (!model3LooksValid(file)) {
            return Decision(
                mode = Live2dDisplayMode.FALLBACK,
                model3Path = trimmed,
                model3FileUrl = toFileUrl(file),
                modelDirFileUrl = toFileUrl(file.parentFile),
                reason = "live2d_model3_invalid",
                shortLabel = "降级（model3 无效）"
            )
        }

        return Decision(
            mode = Live2dDisplayMode.LIVE2D_SHELL,
            model3Path = file.absolutePath,
            model3FileUrl = toFileUrl(file),
            modelDirFileUrl = toFileUrl(file.parentFile),
            reason = "live2d_shell_ready",
            shortLabel = "Live2D 壳"
        )
    }

    /** 将绝对路径转为 WebView 可读的 file:// URL。 */
    fun toFileUrl(file: File?): String {
        if (file == null) return ""
        val abs = file.absolutePath
        // WebView 需要 file:/// 三斜杠
        return if (abs.startsWith("/")) "file://$abs" else "file:///$abs"
    }

    /**
     * 轻量 model3 校验：文件名 + 可读文本含 Version 或 FileReferences。
     * 不解析完整 Cubism schema（避免引入大依赖）。
     */
    fun looksLikeModel3(file: File): Boolean {
        val name = file.name.lowercase()
        if (!name.endsWith(".model3.json") && !name.endsWith("model3.json")) {
            // 允许任意 *.json 若内容像 model3
            if (!name.endsWith(".json")) return false
        }
        return runCatching {
            val head = file.bufferedReader(Charsets.UTF_8).use { reader ->
                val buf = CharArray(2048)
                val n = reader.read(buf)
                if (n <= 0) "" else String(buf, 0, n)
            }
            head.contains("FileReferences", ignoreCase = true) ||
                head.contains("\"Version\"", ignoreCase = true) ||
                head.contains("Moc", ignoreCase = true)
        }.getOrDefault(false)
    }

    fun readinessDetailForMode(mode: Live2dDisplayMode): String = when (mode) {
        Live2dDisplayMode.PLACEHOLDER -> "未配置 model3 → 占位显示"
        Live2dDisplayMode.LIVE2D_SHELL -> "Live2D model3 就绪（渲染壳）"
        Live2dDisplayMode.FALLBACK -> "路径/模型不可用 → 降级占位"
    }
}
