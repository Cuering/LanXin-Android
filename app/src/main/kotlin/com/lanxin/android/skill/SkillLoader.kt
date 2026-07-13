package com.lanxin.android.skill

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从 `assets/skills/` 与 `filesDir/skills/` 加载 Skill。
 *
 * 目录约定：
 * ```
 * skills/
 *   <skill-name>/
 *     SKILL.md          # 必需
 *     scripts/          # 可选
 *     assets/           # 可选
 * ```
 *
 * filesDir 中的同名 skill 覆盖 assets 内置版本。
 */
@Singleton
class SkillLoader @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    /**
     * 加载全部 skill（assets + 本地 files）。
     */
    fun loadAll(): List<Skill> {
        val merged = linkedMapOf<String, Skill>()
        loadFromAssets().forEach { merged[it.name] = it }
        loadFromFilesDir().forEach { merged[it.name] = it }
        return merged.values.toList()
    }

    /**
     * 按名称查找（优先 filesDir）。
     */
    fun loadByName(name: String): Skill? =
        loadAll().firstOrNull { it.name == name }

    fun loadFromAssets(root: String = ASSETS_ROOT): List<Skill> {
        val names = try {
            appContext.assets.list(root)?.toList().orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "list assets/$root failed: ${e.message}")
            emptyList()
        }
        return names.mapNotNull { dirName ->
            loadSkillFromAssets(root, dirName)
        }
    }

    fun loadFromFilesDir(skillsDir: File = File(appContext.filesDir, FILES_ROOT)): List<Skill> {
        if (!skillsDir.isDirectory) return emptyList()
        return skillsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { loadSkillFromDirectory(it, SkillSource.FILES) }
            .orEmpty()
    }

    /**
     * 从普通文件系统目录加载（便于单测 / 外部路径）。
     */
    fun loadSkillFromDirectory(dir: File, source: SkillSource = SkillSource.FILES): Skill? {
        val skillMd = File(dir, SKILL_MD)
        if (!skillMd.isFile) {
            Log.w(TAG, "skip ${dir.name}: missing $SKILL_MD")
            return null
        }
        val raw = runCatching { skillMd.readText(Charsets.UTF_8) }.getOrElse {
            Log.w(TAG, "read ${skillMd.path} failed: ${it.message}")
            return null
        }
        val scripts = loadScriptsFromDir(File(dir, "scripts"))
        val assets = listAssetFiles(File(dir, "assets"))
        return SkillMdParser.parse(
            rawMd = raw,
            nameFallback = dir.name,
            scripts = scripts,
            assets = assets,
            source = source
        )
    }

    private fun loadSkillFromAssets(root: String, dirName: String): Skill? {
        val skillPath = "$root/$dirName/$SKILL_MD"
        val raw = readAssetText(skillPath) ?: return null
        val scripts = loadScriptsFromAssets("$root/$dirName/scripts")
        val assets = listAssetPaths("$root/$dirName/assets")
        return SkillMdParser.parse(
            rawMd = raw,
            nameFallback = dirName,
            scripts = scripts,
            assets = assets,
            source = SkillSource.ASSETS
        )
    }

    private fun loadScriptsFromAssets(scriptsRoot: String): Map<String, String> {
        val names = try {
            appContext.assets.list(scriptsRoot)?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        if (names.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, String>()
        for (name in names) {
            // 仅一层脚本文件；子目录跳过
            val content = readAssetText("$scriptsRoot/$name")
            if (content != null) {
                result[name] = content
            }
        }
        return result
    }

    private fun loadScriptsFromDir(scriptsDir: File): Map<String, String> {
        if (!scriptsDir.isDirectory) return emptyMap()
        return scriptsDir.listFiles()
            ?.filter { it.isFile }
            ?.associate { it.name to it.readText(Charsets.UTF_8) }
            .orEmpty()
    }

    private fun listAssetPaths(assetsRoot: String): List<String> {
        val names = try {
            appContext.assets.list(assetsRoot)?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        return names.map { "$assetsRoot/$it" }
    }

    private fun listAssetFiles(assetsDir: File): List<String> {
        if (!assetsDir.isDirectory) return emptyList()
        return assetsDir.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(assetsDir).path }
            .toList()
    }

    private fun readAssetText(path: String): String? {
        return try {
            appContext.assets.open(path).use { input ->
                input.readBytes().toString(Charset.forName("UTF-8"))
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "SkillLoader"
        const val ASSETS_ROOT = "skills"
        const val FILES_ROOT = "skills"
        const val SKILL_MD = "SKILL.md"
    }
}
