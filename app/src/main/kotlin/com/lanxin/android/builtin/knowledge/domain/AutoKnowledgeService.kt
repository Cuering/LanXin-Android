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

package com.lanxin.android.builtin.knowledge.domain

import android.util.Log
import com.lanxin.android.builtin.knowledge.data.AutoKnowledgeSettings
import com.lanxin.android.data.dto.ApiState
import com.lanxin.android.plugins.chat.data.ChatRepository
import com.lanxin.android.plugins.chat.data.entity.MessageV2
import com.lanxin.android.plugins.chat.data.entity.PlatformV2
import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import com.lanxin.android.plugins.memory.data.memory.MemoryRepository
import com.lanxin.android.data.repository.SettingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * P3：从对话自动抽取知识点 → 向量去重/合并 → 记忆库 + 向量库。
 *
 * 错误一律静默降级，不影响主对话链路。
 */
@Singleton
class AutoKnowledgeService @Inject constructor(
    private val vectorPipeline: VectorPipeline,
    private val vectorStore: VectorStore,
    private val memoryRepository: MemoryRepository,
    private val settings: AutoKnowledgeSettings,
    private val settingRepository: SettingRepository,
    private val chatRepository: ChatRepository
) {

    /**
     * 从消息列表抽取知识并入库。
     * @return 成功新增（含 merge 更新）的条数；失败返回 success(0)
     */
    suspend fun extractAndStore(
        sessionId: String,
        messages: List<ConversationMessage>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (!settings.isEnabled()) return@runCatching 0
            val cleaned = messages
                .map { it.copy(content = it.content.trim()) }
                .filter { it.content.isNotBlank() }
            if (cleaned.isEmpty()) return@runCatching 0

            val window = settings.getHistoryWindowSize()
            val windowed = cleaned.takeLast(window)
            val prompt = AutoKnowledgeMath.buildExtractionPrompt(windowed)
            val raw = callLlm(prompt) ?: return@runCatching 0
            val items = AutoKnowledgeMath.parseExtractionResponse(raw)
            if (items.isEmpty()) return@runCatching 0

            runCatching { vectorPipeline.warmUp() }

            var stored = 0
            for (item in items) {
                val ok = processItem(sessionId, item)
                if (ok) stored++
            }
            stored
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { t ->
                Log.w(TAG, "extractAndStore failed (silent)", t)
                Result.success(0)
            }
        )
    }

    /** 供 UI / 测试：从 MessageV2 历史构建 ConversationMessage。 */
    fun toConversationMessages(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platformIndex: Int = 0
    ): List<ConversationMessage> {
        val result = mutableListOf<ConversationMessage>()
        val n = minOf(userMessages.size, assistantMessages.size)
        for (i in 0 until n) {
            val u = userMessages[i].content.trim()
            if (u.isNotBlank()) {
                result.add(ConversationMessage(role = "user", content = u))
            }
            val a = assistantMessages[i]
                .getOrNull(platformIndex)
                ?.content
                ?.trim()
                .orEmpty()
            if (a.isNotBlank() && !a.contains("[Response stopped:")) {
                result.add(ConversationMessage(role = "assistant", content = a))
            }
        }
        return result
    }

    suspend fun listStored(): List<MemoryEntity> =
        memoryRepository.getAutoKnowledge()

    suspend fun clearStored(): Int = withContext(Dispatchers.IO) {
        val removed = memoryRepository.clearAutoKnowledge()
        runCatching { vectorStore.deleteBySource(VectorSource.AUTO_KNOWLEDGE) }
        removed
    }

    private suspend fun processItem(sessionId: String, item: ExtractedKnowledgeItem): Boolean {
        val hits = runCatching {
            vectorPipeline.search(
                query = item.content,
                topK = 5,
                source = VectorSource.AUTO_KNOWLEDGE
            )
        }.getOrDefault(emptyList())

        val best = hits.maxByOrNull { it.score }
        return when (AutoKnowledgeMath.decideDedupAction(best?.score)) {
            AutoKnowledgeMath.DedupAction.SKIP -> false

            AutoKnowledgeMath.DedupAction.MERGE -> {
                val target = best ?: return insertItem(sessionId, item)
                mergeItem(sessionId, item, target)
            }

            AutoKnowledgeMath.DedupAction.INSERT -> insertItem(sessionId, item)
        }
    }

    private suspend fun insertItem(sessionId: String, item: ExtractedKnowledgeItem): Boolean {
        val meta = AutoKnowledgeMath.encodeMetadata(
            tags = item.tags,
            knowledgeType = item.type,
            sessionId = sessionId,
            importance01 = item.importance
        )
        val memoryId = memoryRepository.addMemory(
            content = item.content,
            type = item.type,
            importance = AutoKnowledgeMath.toMemoryImportance(item.importance),
            lifecycle = "permanent",
            metadata = meta
        )
        if (memoryId <= 0L) return false
        val vid = vectorPipeline.index(
            externalId = memoryId,
            source = VectorSource.AUTO_KNOWLEDGE,
            text = item.content
        )
        return vid > 0L
    }

    private suspend fun mergeItem(
        sessionId: String,
        item: ExtractedKnowledgeItem,
        hit: VectorHit
    ): Boolean {
        val existing = memoryRepository.getMemoryById(hit.externalId)
            ?: return insertItem(sessionId, item)

        val oldMeta = AutoKnowledgeMath.decodeMetadata(existing.metadata)
        val mergedTags = AutoKnowledgeMath.mergeTags(
            existing = oldMeta?.tags.orEmpty(),
            incoming = item.tags
        )
        val mergedImportance01 = AutoKnowledgeMath.mergeImportance(
            existing = oldMeta?.importance01
                ?: AutoKnowledgeMath.fromMemoryImportance(existing.importance),
            incoming = item.importance
        )
        val knowledgeType = oldMeta?.knowledgeType?.takeIf { it.isNotBlank() } ?: item.type

        // 内容：保留更长/更完整的陈述
        val mergedContent = if (item.content.length > existing.content.length) {
            item.content
        } else {
            existing.content
        }

        val meta = AutoKnowledgeMath.encodeMetadata(
            tags = mergedTags,
            knowledgeType = knowledgeType,
            sessionId = sessionId.ifBlank { oldMeta?.sessionId.orEmpty() },
            importance01 = mergedImportance01
        )

        memoryRepository.updateMemory(
            existing.copy(
                content = mergedContent,
                type = knowledgeType,
                importance = AutoKnowledgeMath.toMemoryImportance(mergedImportance01),
                metadata = meta
            )
        )
        vectorPipeline.index(
            externalId = existing.id,
            source = VectorSource.AUTO_KNOWLEDGE,
            text = mergedContent
        )
        return true
    }

    private suspend fun callLlm(prompt: String): String? {
        val platform = resolvePlatform() ?: return null
        val userMsg = MessageV2(
            chatId = 0,
            content = prompt,
            platformType = null
        )
        val assistantRow = listOf(
            MessageV2(
                chatId = 0,
                content = "",
                platformType = platform.uid
            )
        )
        val sb = StringBuilder()
        var failed = false
        runCatching {
            chatRepository.completeChat(
                userMessages = listOf(userMsg),
                assistantMessages = listOf(assistantRow),
                platform = platform.copy(systemPrompt = EXTRACTION_SYSTEM_PROMPT)
            ).collect { state ->
                when (state) {
                    is ApiState.Success -> sb.append(state.textChunk)
                    is ApiState.Error -> {
                        Log.w(TAG, "LLM extraction error: ${state.message}")
                        failed = true
                    }
                    else -> Unit
                }
            }
        }.onFailure { t ->
            Log.w(TAG, "LLM extraction failed", t)
            failed = true
        }
        if (failed) return null
        return sb.toString().trim().takeIf { it.isNotBlank() }
    }

    private suspend fun resolvePlatform(): PlatformV2? {
        val all = runCatching { settingRepository.fetchPlatformV2s() }.getOrDefault(emptyList())
        return all.firstOrNull { it.enabled && it.model.isNotBlank() }
            ?: all.firstOrNull { it.model.isNotBlank() }
    }

    companion object {
        private const val TAG = "AutoKnowledge"
        private const val EXTRACTION_SYSTEM_PROMPT =
            "你只输出严格 JSON，不要 markdown，不要额外说明。"
    }
}
