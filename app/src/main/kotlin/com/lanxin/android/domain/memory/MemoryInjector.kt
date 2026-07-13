package com.lanxin.android.domain.memory

import com.lanxin.android.data.memory.MemoryRepository
import com.lanxin.android.data.memory.MemoryType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在发送聊天消息前检索本地记忆并注入上下文。
 * 当前使用关键词 LIKE 搜索（Phase 2.1 再升级向量检索）。
 */
@Singleton
class MemoryInjector @Inject constructor(
    private val memoryRepository: MemoryRepository
) {
    @Volatile
    var enabled: Boolean = true

    /**
     * 将匹配记忆注入到用户消息前面。
     * 若无匹配或注入关闭，原样返回。
     */
    suspend fun inject(question: String, limit: Int = 5): String {
        if (!enabled || question.isBlank()) return question

        val keyword = extractKeyword(question)
        val memories = memoryRepository.searchForInject(keyword, limit)
        if (memories.isEmpty()) return question

        val lines = memories.joinToString("\n") { memory ->
            val typeLabel = MemoryType.displayName(memory.type)
            "- [$typeLabel] ${memory.content}"
        }

        return buildString {
            appendLine("[我的记忆]")
            appendLine(lines)
            appendLine("[记忆结束]")
            appendLine()
            append(question)
        }
    }

    /**
     * 提取检索关键词：取前 20 个字或整句。
     */
    private fun extractKeyword(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.length <= 20) trimmed else trimmed.take(20)
    }
}
