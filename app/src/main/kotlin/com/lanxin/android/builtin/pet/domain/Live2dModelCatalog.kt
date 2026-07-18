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
import com.lanxin.android.util.PathImportHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Live2D 模型目录扫描与导入落盘（用户可发现的 `LanXin/live2d/`）。
 *
 * 约定：
 * - 根：`LanXin/live2d/<ModelName>/`，内含 `*.model3.json`
 * - 内置 Mao：列表中始终显示；[ensureBuiltinExported] 可同步到 `LanXin/live2d/Mao/`
 * - 扫描以目录内 model3 为准，兼容 `Mao` / `mao` 大小写
 *
 * **禁止**复制妹居商业资源。
 */
object Live2dModelCatalog {

    const val LIVE2D_SUBDIR = "live2d"
    const val BUILTIN_ID = "builtin:Mao"
    const val BUILTIN_DISPLAY_NAME = "内置 Mao"

    enum class Source {
        /** APK assets / filesDir 内置 Sample。 */
        BUILTIN,

        /** `LanXin/live2d/<name>/` 用户可见目录。 */
        LANXIN,

        /** 历史 user-picked 或其它自定义绝对路径。 */
        CUSTOM
    }

    data class ModelEntry(
        /** 稳定 id：builtin:Mao 或 model3 绝对路径。 */
        val id: String,
        val displayName: String,
        /** model3 绝对路径；内置未落盘时可能是 logical asset 路径。 */
        val model3Path: String,
        val source: Source,
        val ready: Boolean,
        /** 短路径摘要（设置页）。 */
        val shortPath: String,
        /** 是否为当前选中（由调用方填）。 */
        val selected: Boolean = false
    )

    /** `…/LanXin/live2d` 目录。 */
    fun live2dRoot(lanXinDir: File): File = File(lanXinDir, LIVE2D_SUBDIR)

    fun live2dRootDisplay(lanXinDir: File): String = live2dRoot(lanXinDir).absolutePath

    /**
     * 扫描可切换模型列表。
     *
     * @param configuredPath DataStore `live2d_model_path`（可空）
     * @param resolvedPath 当前解析后的生效路径
     * @param filesDir Context.filesDir
     * @param lanXinDir 用户可见 LanXin 根
     */
    fun listModels(
        configuredPath: String,
        resolvedPath: String,
        filesDir: File,
        lanXinDir: File
    ): List<ModelEntry> {
        val configured = configuredPath.trim()
        val resolved = resolvedPath.trim()
        val activePath = configured.ifBlank { resolved }

        val entries = linkedMapOf<String, ModelEntry>()

        // 1) 内置 Mao 始终出现
        val builtinInstalled = BuiltInLive2dAssets.installedModelFile(filesDir)
        val builtinPath = when {
            builtinInstalled.isFile && builtinInstalled.length() > 0L ->
                builtinInstalled.absolutePath
            else -> BuiltInLive2dAssets.LOGICAL_PATH
        }
        val builtinReady = BuiltInLive2dAssets.pathLooksBuiltin(builtinPath) ||
            (builtinInstalled.isFile && builtinInstalled.length() > 0L)
        entries[BUILTIN_ID] = ModelEntry(
            id = BUILTIN_ID,
            displayName = BUILTIN_DISPLAY_NAME,
            model3Path = builtinPath,
            source = Source.BUILTIN,
            ready = builtinReady,
            shortPath = PathImportHelper.shortSummary(builtinPath),
            selected = isSelected(activePath, builtinPath, isBuiltin = true)
        )

        // 2) LanXin/live2d/* 扫描（用户可见主目录）
        scanModelDirs(
            root = live2dRoot(lanXinDir),
            source = Source.LANXIN,
            nameForDir = { dir, model3 ->
                val name = dir.name.ifBlank { model3.nameWithoutExtension }
                if (name.equals("Mao", ignoreCase = true)) "Mao（LanXin）" else name
            },
            activePath = activePath,
            entries = entries
        )

        // 3) 兼容旧路径：filesDir/debug-assets/live2d/*
        val legacyRoot = File(filesDir, "${DebugOpenSourcePaths.LEGACY_ROOT_DIR}/$LIVE2D_SUBDIR")
        scanModelDirs(
            root = legacyRoot,
            source = Source.CUSTOM,
            nameForDir = { dir, model3 ->
                val name = dir.name.ifBlank { model3.nameWithoutExtension }
                if (name.equals("Mao", ignoreCase = true)) {
                    "Mao（旧 debug-assets）"
                } else {
                    "$name（旧）"
                }
            },
            activePath = activePath,
            entries = entries
        )

        // 4) 当前配置路径若不在列表中，补一条 CUSTOM
        if (configured.isNotBlank() &&
            !entries.values.any { pathsEqual(it.model3Path, configured) } &&
            !BuiltInLive2dAssets.pathLooksBuiltin(configured)
        ) {
            val f = File(configured)
            entries[configured] = ModelEntry(
                id = configured,
                displayName = PathImportHelper.displayFileName(configured)
                    .ifBlank { "自定义模型" },
                model3Path = configured,
                source = Source.CUSTOM,
                ready = f.isFile && f.length() > 0L,
                shortPath = PathImportHelper.shortSummary(configured),
                selected = true
            )
        }

        // 若没有任何 selected，默认选中内置
        val list = entries.values.toList()
        if (list.none { it.selected } && list.isNotEmpty()) {
            return list.mapIndexed { i, e ->
                if (i == 0) e.copy(selected = true) else e
            }
        }
        return list
    }

    /**
     * 将内置 Mao 同步到 `LanXin/live2d/Mao/`，便于文件管理器与其它模型并列。
     * @return 导出后的 model3 绝对路径；失败 null
     */
    fun ensureBuiltinExported(context: Context, lanXinDir: File): String? {
        val installed = BuiltInLive2dAssets.ensureInstalled(context) ?: return null
        val srcModel = File(installed)
        if (!srcModel.isFile) return null
        val srcRoot = srcModel.parentFile ?: return null
        val destRoot = File(live2dRoot(lanXinDir), "Mao")
        return runCatching {
            if (needsCopy(srcRoot, destRoot, BuiltInLive2dAssets.MODEL3_NAME)) {
                if (destRoot.exists()) destRoot.deleteRecursively()
                copyDir(srcRoot, destRoot)
            }
            val destModel = File(destRoot, BuiltInLive2dAssets.MODEL3_NAME)
            if (destModel.isFile && destModel.length() > 0L) {
                destModel.absolutePath
            } else {
                null
            }
        }.getOrNull()
    }

    /**
     * 将已拷贝到临时目录的模型树落到 `LanXin/live2d/<name>/`。
     *
     * @param sourceDir 含 model3 的目录（或其上级）
     * @param preferredName 优先目录名（缺省取 model3 父目录名）
     * @return 落盘后的 model3 绝对路径
     */
    fun importModelTree(
        lanXinDir: File,
        sourceDir: File,
        preferredName: String? = null
    ): File {
        val model3 = PathImportHelper.findModel3Json(sourceDir)
            ?: error("未找到 *.model3.json")
        // 模型根：优先 model3 的父目录（完整资源通常同级）
        val modelRoot = model3.parentFile ?: sourceDir
        val baseName = preferredName?.let { PathImportHelper.sanitizeFileName(it) }
            ?.takeIf { it.isNotBlank() }
            ?: modelRoot.name.takeIf { it.isNotBlank() && it != "import" }
            ?: model3.name.removeSuffix(".model3.json").removeSuffix(".model3")
                .ifBlank { "model" }
            .let { PathImportHelper.sanitizeFileName(it) }

        val uniqueName = uniqueDirName(live2dRoot(lanXinDir), baseName)
        val destRoot = File(live2dRoot(lanXinDir), uniqueName)
        if (destRoot.exists()) destRoot.deleteRecursively()
        copyDir(modelRoot, destRoot)
        val destModel = PathImportHelper.findModel3Json(destRoot)
            ?: error("导入后未找到 model3")
        return destModel
    }

    /**
     * 单文件 model3：仅拷贝该文件到 `LanXin/live2d/<name>/`（同包资源可能缺失，由用户自备）。
     */
    fun importModel3File(
        lanXinDir: File,
        sourceFile: File,
        preferredName: String? = null
    ): File {
        if (!sourceFile.isFile) error("不是有效文件")
        val baseName = preferredName?.let { PathImportHelper.sanitizeFileName(it) }
            ?.takeIf { it.isNotBlank() }
            ?: sourceFile.name
                .removeSuffix(".model3.json")
                .removeSuffix(".model3")
                .ifBlank { "model" }
                .let { PathImportHelper.sanitizeFileName(it) }
        val uniqueName = uniqueDirName(live2dRoot(lanXinDir), baseName)
        val destRoot = File(live2dRoot(lanXinDir), uniqueName)
        destRoot.mkdirs()
        val dest = File(destRoot, sourceFile.name)
        sourceFile.copyTo(dest, overwrite = true)
        if (!dest.isFile || dest.length() <= 0L) error("导入后文件无效")
        return dest
    }

    /** 切换目标路径：内置 id → 空配置（走默认解析）或已安装绝对路径。 */
    fun resolveSwitchPath(
        entry: ModelEntry,
        filesDir: File
    ): String? {
        return when (entry.source) {
            Source.BUILTIN -> {
                // 清空自定义 → 走内置解析；若已安装则也可写绝对路径
                val installed = BuiltInLive2dAssets.installedModelFile(filesDir)
                if (installed.isFile && installed.length() > 0L) {
                    installed.absolutePath
                } else {
                    // 空路径表示使用默认内置
                    null
                }
            }
            Source.LANXIN, Source.CUSTOM -> entry.model3Path
        }
    }

    fun currentDisplayName(
        models: List<ModelEntry>,
        configuredPath: String,
        resolvedPath: String
    ): String {
        models.firstOrNull { it.selected }?.let { return it.displayName }
        val path = configuredPath.ifBlank { resolvedPath }
        if (path.isBlank()) return BUILTIN_DISPLAY_NAME
        if (BuiltInLive2dAssets.pathLooksBuiltin(path)) return BUILTIN_DISPLAY_NAME
        return PathImportHelper.displayFileName(path).ifBlank { "自定义" }
    }

    private fun scanModelDirs(
        root: File,
        source: Source,
        nameForDir: (File, File) -> String,
        activePath: String,
        entries: MutableMap<String, ModelEntry>
    ) {
        if (!root.isDirectory) return
        val dirs = root.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
        for (dir in dirs) {
            val model3 = PathImportHelper.findModel3Json(dir) ?: continue
            val path = model3.absolutePath
            if (entries.containsKey(path)) continue
            // 与内置已安装绝对路径相同则跳过（避免重复）
            if (entries.values.any { pathsEqual(it.model3Path, path) }) continue
            entries[path] = ModelEntry(
                id = path,
                displayName = nameForDir(dir, model3),
                model3Path = path,
                source = source,
                ready = model3.isFile && model3.length() > 0L,
                shortPath = PathImportHelper.shortSummary(path),
                selected = isSelected(activePath, path, isBuiltin = false)
            )
        }
    }

    private fun isSelected(
        activePath: String,
        entryPath: String,
        isBuiltin: Boolean
    ): Boolean {
        if (activePath.isBlank()) return isBuiltin
        if (BuiltInLive2dAssets.pathLooksBuiltin(activePath) && isBuiltin) return true
        return pathsEqual(activePath, entryPath)
    }

    private fun pathsEqual(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        return try {
            File(a).canonicalPath == File(b).canonicalPath
        } catch (_: Exception) {
            a.replace('\\', '/') == b.replace('\\', '/')
        }
    }

    private fun uniqueDirName(parent: File, base: String): String {
        parent.mkdirs()
        var name = base.ifBlank { "model" }
        var i = 2
        while (File(parent, name).exists()) {
            name = "${base}_$i"
            i++
        }
        return name
    }

    private fun needsCopy(srcRoot: File, destRoot: File, model3Name: String): Boolean {
        val destModel = File(destRoot, model3Name)
        if (!destModel.isFile || destModel.length() <= 0L) return true
        val srcModel = File(srcRoot, model3Name)
        if (!srcModel.isFile) return false
        return destModel.length() != srcModel.length() ||
            destModel.lastModified() < srcModel.lastModified()
    }

    private fun copyDir(src: File, dest: File) {
        if (src.isFile) {
            dest.parentFile?.mkdirs()
            copyFile(src, dest)
            return
        }
        dest.mkdirs()
        val kids = src.listFiles() ?: return
        for (child in kids) {
            val target = File(dest, child.name)
            if (child.isDirectory) {
                copyDir(child, target)
            } else {
                copyFile(child, target)
            }
        }
    }

    private fun copyFile(src: File, dest: File) {
        dest.parentFile?.mkdirs()
        FileInputStream(src).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }
}
