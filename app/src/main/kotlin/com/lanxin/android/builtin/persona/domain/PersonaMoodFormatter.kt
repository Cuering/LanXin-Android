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

package com.lanxin.android.builtin.persona.domain

/**
 * 将 [Persona.moodImitationDialogs] 格式化为 system prompt 片段，
 * 并注入桌宠隐藏情绪标签协议（本地脑 / 云端同一约定）。
 *
 * 存储结构：交替的 user/assistant 消息，偶数索引 = user，奇数索引 = assistant。
 */
object PersonaMoodFormatter {

    const val SECTION_HEADER = "以下是你的设定对话风格示例："

    /**
     * 桌宠 / 聊天统一：回复首行可带隐藏情绪标签，客户端按标签选表情。
     * 只允许 Mao 现有 exp/motion 反推的 mood 枚举。
     */
    const val PET_MOOD_TAG_SECTION_HEADER = "【桌宠情绪标签】"

    val PET_MOOD_TAG_INSTRUCTION: String = buildString {
        appendLine(PET_MOOD_TAG_SECTION_HEADER)
        appendLine("每条回复正文前可单独一行输出隐藏标签（客户端会剥离，用户不可见）：")
        appendLine("[[mood=joy]]")
        appendLine("允许值（仅下列，勿自造）：smile, listen, think, speak, sorry, idle, joy, music, tap")
        appendLine("兼容写法：[[pet mood=joy]] 或 [[pet:joy]]；无合适情绪时省略标签。")
        append("标签后换行写可见正文，勿把标签念给用户。")
    }

    /**
     * 格式化情绪模仿示例段落；无内容时返回空串。
     */
    fun formatMoodImitationSection(dialogs: List<String>?): String {
        if (dialogs.isNullOrEmpty()) return ""
        return buildString {
            appendLine(SECTION_HEADER)
            appendLine()
            dialogs.forEachIndexed { index, message ->
                val role = if (index % 2 == 0) "user" else "assistant"
                appendLine("$role: $message")
            }
        }.trimEnd()
    }

    /**
     * 将 mood 段落拼接到 system prompt 后；dialogs 为空时原样返回 base。
     */
    fun appendToSystemPrompt(base: String, dialogs: List<String>?): String {
        val section = formatMoodImitationSection(dialogs)
        if (section.isEmpty()) return base
        val trimmed = base.trim()
        return if (trimmed.isEmpty()) section else "$trimmed\n\n$section"
    }

    /**
     * 注入桌宠隐藏情绪标签约定（本地脑与云端同一协议）。
     * 已含段落时不重复追加。
     */
    fun appendPetMoodTagInstruction(base: String): String {
        val trimmed = base.trim()
        if (trimmed.contains(PET_MOOD_TAG_SECTION_HEADER)) return trimmed
        return if (trimmed.isEmpty()) {
            PET_MOOD_TAG_INSTRUCTION
        } else {
            "$trimmed\n\n$PET_MOOD_TAG_INSTRUCTION"
        }
    }
}
