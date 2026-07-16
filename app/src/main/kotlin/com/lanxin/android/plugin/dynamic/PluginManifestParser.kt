package com.lanxin.android.plugin.dynamic

import java.io.File
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 解析动态插件清单。
 *
 * 使用 ZipFile 读取 APK 内 entry，JVM 单测可构造最小 zip 验证。
 */
object PluginManifestParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 从 JSON 文本解析清单；字段非法时返回 null。
     */
    fun parseJson(raw: String): PluginManifest? {
        if (raw.isBlank()) return null
        return try {
            val obj = json.parseToJsonElement(raw).jsonObject
            parseObject(obj)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 从 APK（zip）读取 assets/lanxin-plugin.json。
     */
    fun parseFromApk(apkFile: File): PluginManifest? {
        if (!apkFile.isFile) return null
        return try {
            ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry(PluginPackagePaths.MANIFEST_ENTRY)
                    ?: zip.getEntry(PluginPackagePaths.MANIFEST_ENTRY_ALT)
                    ?: return null
                zip.getInputStream(entry).use { input ->
                    val text = input.readBytes().toString(Charsets.UTF_8)
                    parseJson(text)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseObject(obj: JsonObject): PluginManifest? {
        val id = obj.string("id")?.trim().orEmpty()
        val name = obj.string("name")?.trim().orEmpty()
        val version = obj.string("version")?.trim().orEmpty()
        val entryClass = obj.string("entryClass")?.trim().orEmpty()
        if (id.isEmpty() || name.isEmpty() || version.isEmpty() || entryClass.isEmpty()) {
            return null
        }
        if (!isSafeId(id)) return null
        if (!isSafeClassName(entryClass)) return null

        return PluginManifest(
            id = id,
            name = name,
            version = version,
            description = obj.string("description")?.trim().orEmpty(),
            entryClass = entryClass,
            author = obj.string("author")?.trim().orEmpty(),
            minAppVersion = obj.string("minAppVersion")?.trim().orEmpty(),
            removable = obj["removable"]?.jsonPrimitive?.booleanOrNull ?: true
        )
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    /** 插件 id：字母数字、点、下划线、连字符。 */
    fun isSafeId(id: String): Boolean =
        id.matches(Regex("^[A-Za-z0-9._-]+$")) && id.length in 1..128

    /** 入口类：简单 Java 全限定名检查。 */
    fun isSafeClassName(className: String): Boolean =
        className.matches(Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+$")) &&
            className.length in 3..256
}
