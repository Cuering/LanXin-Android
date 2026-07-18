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
 * 优先公共共享存储下的 `LanXin/`（文件管理器易见），
 * 写失败时回退到 `getExternalFilesDir()/LanXin/`（仍比内部 filesDir 更易访问）。
 *
 * 不申请 MANAGE_EXTERNAL_STORAGE；仅最小必要尝试 + 优雅回退。
 */
object DebugAssetStorage {

    data class Root(
        /** 传给 [DebugAssetDownloader] 的 base（其下再挂 [DebugOpenSourcePaths.ROOT_DIR]）。 */
        val baseDir: File,
        /** 实际 `…/LanXin` 目录。 */
        val lanXinDir: File,
        /** true = 未用上公共存储根，走了 App 外部 files 回退。 */
        val usedFallback: Boolean,
        /** 展示给用户的绝对路径。 */
        val displayPath: String
    )

    /**
     * 解析下载根。
     * - 优先：`Environment.getExternalStorageDirectory()/LanXin`
     * - 回退：`context.getExternalFilesDir(null)/LanXin`
     * - 再回退：`context.filesDir/LanXin`（极少）
     */
    fun resolve(context: Context): Root {
        val publicBase = Environment.getExternalStorageDirectory()
        if (publicBase != null) {
            val publicLanXin = File(publicBase, DebugOpenSourcePaths.ROOT_DIR)
            if (ensureWritableDir(publicLanXin)) {
                return Root(
                    baseDir = publicBase,
                    lanXinDir = publicLanXin,
                    usedFallback = false,
                    displayPath = publicLanXin.absolutePath
                )
            }
        }

        val externalFiles = context.getExternalFilesDir(null)
        if (externalFiles != null) {
            val fallbackLanXin = File(externalFiles, DebugOpenSourcePaths.ROOT_DIR)
            if (ensureWritableDir(fallbackLanXin)) {
                return Root(
                    baseDir = externalFiles,
                    lanXinDir = fallbackLanXin,
                    usedFallback = true,
                    displayPath = fallbackLanXin.absolutePath
                )
            }
        }

        val internalBase = context.filesDir
        val last = File(internalBase, DebugOpenSourcePaths.ROOT_DIR)
        last.mkdirs()
        return Root(
            baseDir = internalBase,
            lanXinDir = last,
            usedFallback = true,
            displayPath = last.absolutePath
        )
    }

    /** 单测 / 无 Context：直接把 [baseDir] 当作下载 base。 */
    fun fromBaseDir(baseDir: File): Root {
        val lanXin = File(baseDir, DebugOpenSourcePaths.ROOT_DIR)
        lanXin.mkdirs()
        return Root(
            baseDir = baseDir,
            lanXinDir = lanXin,
            usedFallback = false,
            displayPath = lanXin.absolutePath
        )
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
