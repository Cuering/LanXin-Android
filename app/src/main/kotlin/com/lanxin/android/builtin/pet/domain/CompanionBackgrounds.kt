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
import java.io.File

/**
 * 陪伴页换背景：预设渐变 ID + 用户图 `LanXin/backgrounds/`（与 music/live2d 同根）。
 *
 * 渲染在 Compose 层（WebView 透明叠上），不进 HTML/Cubism。
 * 自定义图经 SAF 导入后落盘；大文件不进 git。
 */
object CompanionBackgrounds {

    const val DIR_REL = "${DebugOpenSourcePaths.ROOT_DIR}/backgrounds"
    const val CUSTOM_ID = "custom"
    const val DEFAULT_ID = "sakura"

    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    /**
     * 内置预设（纯色/渐变，零资源）。
     * [colorsArgb] 为 Compose 用 ARGB 长整型（0xAARRGGBB）。
     */
    data class Preset(
        val id: String,
        val label: String,
        val colorsArgb: List<Long>
    )

    val PRESETS: List<Preset> = listOf(
        Preset(
            id = "sakura",
            label = "樱花粉",
            colorsArgb = listOf(0xFFFFE4ECL, 0xFFFFC1D6L, 0xFFFF9BB8L)
        ),
        Preset(
            id = "sky",
            label = "晴空",
            colorsArgb = listOf(0xFFE3F2FDL, 0xFFBBDEFBL, 0xFF90CAF9L)
        ),
        Preset(
            id = "mint",
            label = "薄荷绿",
            colorsArgb = listOf(0xFFE8F5E9L, 0xFFC8E6C9L, 0xFFA5D6A7L)
        ),
        Preset(
            id = "lavender",
            label = "薰衣紫",
            colorsArgb = listOf(0xFFF3E5F5L, 0xFFE1BEE7L, 0xFFCE93D8L)
        ),
        Preset(
            id = "night",
            label = "夜色",
            colorsArgb = listOf(0xFF1A1A2EL, 0xFF16213EL, 0xFF0F3460L)
        ),
        Preset(
            id = "sunset",
            label = "晚霞",
            colorsArgb = listOf(0xFFFFE0B2L, 0xFFFFAB91L, 0xFFFF8A65L)
        )
    )

    fun presetById(id: String): Preset? =
        PRESETS.firstOrNull { it.id == id }

    fun isKnownPreset(id: String): Boolean =
        id.isNotBlank() && id != CUSTOM_ID && presetById(id) != null

    fun backgroundsDir(baseDir: File): File = File(baseDir, DIR_REL)

    fun backgroundsDirFromStorage(context: Context, safTreeUri: String? = null): File {
        val root = DebugAssetStorage.resolve(context, safTreeUri)
        val dir = File(root.lanXinDir, "backgrounds")
        dir.mkdirs()
        return dir
    }

    fun isImageFile(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return file.extension.lowercase() in IMAGE_EXTENSIONS
    }

    /** 浅层扫描用户导入图。 */
    fun listImages(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { isImageFile(it) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    /**
     * 解析当前生效背景。
     * - 预设 ID → [Resolved.Preset]
     * - custom + 有效路径（绝对 / 相对 LanXin）→ [Resolved.Image]
     * - 否则回退默认樱花粉
     *
     * @param lanXinDir 用于解析相对路径；null 时仅认绝对路径
     */
    fun resolve(
        presetId: String,
        customPath: String,
        lanXinDir: File? = null
    ): Resolved {
        val id = presetId.trim().ifBlank { DEFAULT_ID }
        if (id == CUSTOM_ID || customPath.isNotBlank() && !isKnownPreset(id)) {
            val stored = customPath.trim()
            val f = when {
                lanXinDir != null ->
                    LanXinSafTree.resolveUnderLanXin(stored, lanXinDir)
                        ?: File(stored).takeIf { isImageFile(it) }
                else -> File(stored).takeIf { isImageFile(it) }
            }
            if (f != null && isImageFile(f)) {
                return Resolved.Image(path = f.absolutePath, displayName = f.name)
            }
        }
        val preset = presetById(id) ?: presetById(DEFAULT_ID)!!
        return Resolved.Preset(preset)
    }

    /**
     * 将绝对路径收敛为相对 `LanXin/backgrounds/…` 存储键；失败则退回绝对路径。
     */
    fun storePathKey(absolutePath: String, lanXinDir: File): String {
        return LanXinSafTree.relativeUnderLanXin(absolutePath, lanXinDir)
            ?: absolutePath.trim()
    }

    sealed class Resolved {
        data class Preset(val preset: CompanionBackgrounds.Preset) : Resolved()
        data class Image(val path: String, val displayName: String) : Resolved()
    }
}
