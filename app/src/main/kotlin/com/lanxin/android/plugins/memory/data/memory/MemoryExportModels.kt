package com.lanxin.android.plugins.memory.data.memory

import kotlinx.serialization.Serializable

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
