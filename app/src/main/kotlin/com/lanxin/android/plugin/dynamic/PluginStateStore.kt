package com.lanxin.android.plugin.dynamic

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 插件 enable / disable 状态持久化。
 *
 * 文件格式：
 * ```json
 * { "enabled": { "plugin.id": true, "other": false } }
 * ```
 *
 * 未出现在 map 中的 id 视为 **默认启用**。
 */
class PluginStateStore(
    private val stateFile: File
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    private val enabledMap = linkedMapOf<String, Boolean>()

    init {
        reload()
    }

    fun reload() {
        enabledMap.clear()
        if (!stateFile.isFile) return
        val text = runCatching { stateFile.readText(Charsets.UTF_8) }.getOrNull() ?: return
        if (text.isBlank()) return
        try {
            val root = json.parseToJsonElement(text).jsonObject
            val enabled = root["enabled"]?.jsonObject ?: return
            for ((k, v) in enabled) {
                val flag = v.jsonPrimitive.booleanOrNull
                    ?: v.jsonPrimitive.contentOrNull?.toBooleanStrictOrNull()
                if (flag != null) {
                    enabledMap[k] = flag
                }
            }
        } catch (_: Exception) {
            // 损坏文件：保持空 map（全部默认启用），不崩溃
        }
    }

    fun isEnabled(pluginId: String): Boolean =
        enabledMap[pluginId] ?: true

    fun setEnabled(pluginId: String, enabled: Boolean) {
        enabledMap[pluginId] = enabled
        persist()
    }

    fun snapshot(): Map<String, Boolean> = enabledMap.toMap()

    fun persist() {
        val parent = stateFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val body = buildJsonObject {
            put(
                "enabled",
                buildJsonObject {
                    enabledMap.forEach { (id, on) -> put(id, on) }
                }
            )
        }
        runCatching {
            stateFile.writeText(body.toString(), Charsets.UTF_8)
        }
    }
}
