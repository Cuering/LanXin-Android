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

package com.lanxin.android.presentation.ui.chat

/**
 * 发送路径防御性纯函数（可单测）。
 *
 * 覆盖 #112 之外仍会硬崩的点：
 * - 引用三列长度不一致时 [ChatGenerationStatusLogic.refsFromUnifiedKeys] 的 require
 * - 消息列表下标与助手槽位不一致
 * - OpenAI/Gemini 流式 chunk 空 choices / null content 的 first()/!!
 */
object ChatSendPathGuard {

    /**
     * 仅当 keys/texts/subtitles 长度一致时构建引用，否则返回 empty 并标记 mismatch。
     */
    fun safeRefsFromUnifiedKeys(
        keys: List<String>,
        texts: List<String>,
        subtitles: List<String>
    ): SafeRefs {
        if (keys.size != texts.size || texts.size != subtitles.size) {
            return SafeRefs(refs = emptyList(), sizeMismatch = true)
        }
        return SafeRefs(
            refs = ChatGenerationStatusLogic.refsFromUnifiedKeys(keys, texts, subtitles),
            sizeMismatch = false
        )
    }

    data class SafeRefs(
        val refs: List<ChatRef>,
        val sizeMismatch: Boolean
    )

    /**
     * 是否允许对 turn/platform 槽位写入。越界返回 false，调用方跳过而非崩溃。
     */
    fun canWriteAssistantSlot(
        assistantMessages: List<List<*>>,
        turnIndex: Int,
        platformIndex: Int
    ): Boolean {
        if (turnIndex !in assistantMessages.indices) return false
        val row = assistantMessages[turnIndex]
        return platformIndex in row.indices
    }

    /**
     * OpenAI-compatible 流：安全取 content，空 choices / null content → null（不抛）。
     */
    fun openAiDeltaContent(choicesSize: Int, firstDeltaContent: String?): String? {
        if (choicesSize <= 0) return null
        return firstDeltaContent
    }

    /**
     * Gemini 流：candidates 空时返回 empty，不 first()。
     */
    fun geminiPartsOrEmpty(candidatesSize: Int, parts: List<Any>?): List<Any> {
        if (candidatesSize <= 0) return emptyList()
        return parts.orEmpty()
    }
}
