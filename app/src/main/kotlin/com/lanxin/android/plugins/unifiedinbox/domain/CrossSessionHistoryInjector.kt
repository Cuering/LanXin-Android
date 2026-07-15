package com.lanxin.android.plugins.unifiedinbox.domain

import android.util.Log
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionEntity
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 将跨 session 搜索结果引用到聊天上下文（对标 MemoryInjector 风格）。
 *
 * 默认关闭；由设置或调用方显式开启。
 */
@Singleton
class CrossSessionHistoryInjector @Inject constructor(
    private val repository: CrossSessionRepository
) {
    @Volatile
    var enabled: Boolean = false

    /**
     * 检索相关跨 session 对话并拼到用户问题前。
     * 关闭或无命中时原样返回。
     */
    suspend fun inject(question: String, limit: Int = 5): String {
        if (!enabled || question.isBlank()) return question

        val keyword = extractKeyword(question)
        val hits = try {
            repository.searchForInject(keyword, limit)
        } catch (e: Exception) {
            Log.w(TAG, "search failed: ${e.message}")
            emptyList()
        }

        if (hits.isEmpty()) return question

        val lines = hits.joinToString("\n") { formatHit(it) }
        return buildString {
            appendLine("[跨会话引用]")
            appendLine(lines)
            appendLine("[引用结束]")
            appendLine()
            append(question)
        }
    }

    companion object {
        private const val TAG = "CrossSessionInjector"

        fun extractKeyword(text: String): String {
            val trimmed = text.trim()
            return if (trimmed.length <= 20) trimmed else trimmed.take(20)
        }

        fun formatHit(entity: CrossSessionEntity): String {
            val title = entity.sessionTitle.ifBlank { entity.sessionId }
            val role = entity.role
            val snippet = entity.content.replace('\n', ' ').take(120)
            return "- [${entity.platform}/$title][$role] $snippet"
        }
    }
}
