package com.lanxin.android.plugins.memory.data.memory

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class MemoryExportPayload(
    val version: Int = 1,
    val exportedAt: Long,
    val memories: List<MemoryExportItem>
)

@Serializable
data class MemoryExportItem(
    val id: Long = 0,
    val content: String,
    val type: String = MemoryType.CHAT,
    val importance: Float = 5.0f,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val status: String = "active",
    val lifecycle: String = "permanent",
    val metadata: String? = null
)

data class MemoryImportResult(
    val imported: Int,
    val skipped: Int,
    val total: Int
) {
    val message: String
        get() = if (skipped > 0) {
            "导入完成：新增 $imported 条，跳过 $skipped 条"
        } else {
            "导入完成：共 $imported 条"
        }
}

/**
 * P4：记忆导出格式。
 */
enum class MemoryExportFormat {
    JSON,
    MARKDOWN
}

/**
 * P4：将记忆列表渲染为可读 Markdown。
 *
 * 分组规则：
 * 1. metadata 含 auto_knowledge → auto_knowledge
 * 2. type == relationship → relationship
 * 3. lifecycle == permanent 且 type 非上述 → permanent
 * 4. 其余 → memory（再按原始 type 二级分组）
 *
 * 每条输出：**content** + tags + createdAt + score(importance)
 */
object MemoryMarkdownExporter {

    private val metaJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val groupOrder = listOf(
        "permanent",
        "memory",
        "auto_knowledge",
        "relationship"
    )

    fun build(
        memories: List<MemoryExportItem>,
        exportedAt: Long,
        typeFilter: String? = null
    ): String {
        val filtered = if (typeFilter.isNullOrBlank()) {
            memories
        } else {
            memories.filter { matchesTypeFilter(it, typeFilter) }
        }

        val stamp = formatTimestamp(exportedAt)
        val sb = StringBuilder()
        sb.appendLine("# LanXin Memory Export")
        sb.appendLine()
        sb.appendLine("- version: 1")
        sb.appendLine("- exportedAt: $stamp")
        sb.appendLine("- total: ${filtered.size}")
        sb.appendLine(
            "- filter: ${if (typeFilter.isNullOrBlank()) "all" else typeFilter}"
        )
        sb.appendLine()

        if (filtered.isEmpty()) {
            sb.appendLine("_（无记忆条目）_")
            sb.appendLine()
            return sb.toString()
        }

        val grouped = filtered.groupBy { resolveExportGroup(it) }
        val orderedKeys = groupOrder.filter { it in grouped.keys } +
            grouped.keys.filter { it !in groupOrder }.sorted()

        for (group in orderedKeys) {
            val items = grouped[group].orEmpty()
                .sortedWith(
                    compareByDescending<MemoryExportItem> { it.importance }
                        .thenByDescending { it.createdAt }
                )
            sb.appendLine("## $group (${items.size})")
            sb.appendLine()

            // memory 组再按原始 type 二级分组，便于阅读
            if (group == "memory") {
                val byType = items.groupBy { it.type.ifBlank { MemoryType.CHAT } }
                for ((type, typeItems) in byType.toSortedMap()) {
                    sb.appendLine("### type: $type")
                    sb.appendLine()
                    typeItems.forEachIndexed { index, item ->
                        appendItem(sb, index + 1, item)
                    }
                }
            } else {
                items.forEachIndexed { index, item ->
                    appendItem(sb, index + 1, item)
                }
            }
        }

        return sb.toString()
    }

    /**
     * 过滤：
     * - 直接匹配 type
     * - 或匹配导出分组名（permanent / memory / auto_knowledge / relationship）
     * - 或匹配 lifecycle
     */
    fun matchesTypeFilter(item: MemoryExportItem, typeFilter: String): Boolean {
        val key = typeFilter.trim()
        if (key.isEmpty()) return true
        if (item.type.equals(key, ignoreCase = true)) return true
        if (item.lifecycle.equals(key, ignoreCase = true)) return true
        return resolveExportGroup(item).equals(key, ignoreCase = true)
    }

    fun resolveExportGroup(item: MemoryExportItem): String {
        if (item.metadata?.contains("auto_knowledge") == true) {
            return "auto_knowledge"
        }
        if (item.type.equals("relationship", ignoreCase = true)) {
            return "relationship"
        }
        if (item.lifecycle.equals("permanent", ignoreCase = true)) {
            return "permanent"
        }
        return "memory"
    }

    fun extractTags(metadata: String?): List<String> {
        if (metadata.isNullOrBlank()) return emptyList()
        return runCatching {
            val element = metaJson.parseToJsonElement(metadata)
            val obj = element as? JsonObject ?: return emptyList()
            val tagsEl = obj["tags"] ?: return emptyList()
            when (tagsEl) {
                is JsonArray -> tagsEl.mapNotNull {
                    it.jsonPrimitive.contentOrNull?.trim()?.takeIf { t -> t.isNotEmpty() }
                }
                is JsonPrimitive -> {
                    val raw = tagsEl.contentOrNull.orEmpty()
                    if (raw.isBlank()) emptyList()
                    else raw.split(',', ';', '，').map { it.trim() }.filter { it.isNotEmpty() }
                }
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun appendItem(sb: StringBuilder, index: Int, item: MemoryExportItem) {
        val tags = extractTags(item.metadata)
        val created = if (item.createdAt > 0) formatTimestamp(item.createdAt) else "-"
        val score = String.format(Locale.US, "%.1f", item.importance)
        val content = item.content.trim().ifEmpty { "(empty)" }

        sb.appendLine("$index. **$content**")
        sb.appendLine("   - tags: ${if (tags.isEmpty()) "-" else tags.joinToString(", ")}")
        sb.appendLine("   - createdAt: $created")
        sb.appendLine("   - score: $score")
        sb.appendLine("   - type: ${item.type}")
        sb.appendLine("   - lifecycle: ${item.lifecycle}")
        sb.appendLine()
    }

    private fun formatTimestamp(millis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return fmt.format(Date(millis))
    }
}
