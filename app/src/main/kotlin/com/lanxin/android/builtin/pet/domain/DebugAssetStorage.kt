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
 * Android 10+ 公共根常不可写：用户可在设置页通过 SAF 授权公共 `LanXin/`
 *（[LanXinSafTree]），引擎仍写 externalFiles，成功后可选镜像到 SAF 树。
 *
 * @see LanXinSafTree
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
        val safDisplayLabel: String = ""
    ) {
        /** 公共 File 可写，或 SAF 树可写。 */
        val publicWritable: Boolean get() = !usedFallback || safWritable
    }

    /**
     * 解析下载根。
     * - 优先：`Environment.getExternalStorageDirectory()/LanXin`（File 直写）
     * - 回退：`context.getExternalFilesDir(null)/LanXin`
     * - 再回退：`context.filesDir/LanXin`（极少）
     * - 叠加：DataStore 中的 SAF 树 Uri（[LanXinSafTree.PREFS_KEY] 由调用方传入）
     */
    fun resolve(context: Context, safTreeUri: String? = null): Root {
        val probe = LanXinSafTree.probe(context, safTreeUri)
        val publicBase = Environment.getExternalStorageDirectory()
        if (publicBase != null) {
            val publicLanXin = File(publicBase, DebugOpenSourcePaths.ROOT_DIR)
            if (ensureWritableDir(publicLanXin)) {
                return Root(
                    baseDir = publicBase,
                    lanXinDir = publicLanXin,
                    usedFallback = false,
                    displayPath = publicLanXin.absolutePath,
                    safTreeUri = probe.treeUri,
                    safGranted = probe.granted,
                    safWritable = probe.writable,
                    safDisplayLabel = probe.displayLabel
                )
            }
        }

        val externalFiles = context.getExternalFilesDir(null)
        if (externalFiles != null) {
            val fallbackLanXin = File(externalFiles, DebugOpenSourcePaths.ROOT_DIR)
            if (ensureWritableDir(fallbackLanXin)) {
                val display = if (probe.writable) {
                    "App 私有 + SAF(${probe.displayLabel})"
                } else {
                    fallbackLanXin.absolutePath
                }
                return Root(
                    baseDir = externalFiles,
                    lanXinDir = fallbackLanXin,
                    usedFallback = true,
                    displayPath = display,
                    safTreeUri = probe.treeUri,
                    safGranted = probe.granted,
                    safWritable = probe.writable,
                    safDisplayLabel = probe.displayLabel
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
            displayPath = last.absolutePath,
            safTreeUri = probe.treeUri,
            safGranted = probe.granted,
            safWritable = probe.writable,
            safDisplayLabel = probe.displayLabel
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
        if (!root.usedFallback || !root.safWritable || root.safTreeUri.isBlank()) return null
        return LanXinSafTree.mirrorFile(
            context = context,
            treeUriString = root.safTreeUri,
            file = localFile,
            relativeUnderLanXin = relativeUnderLanXin
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
