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
 * 将 [Persona.moodImitationDialogs] 格式化为 system prompt 片段。
 *
 * 存储结构：交替的 user/assistant 消息，偶数索引 = user，奇数索引 = assistant。
 */
object PersonaMoodFormatter {

    const val SECTION_HEADER = "以下是你的设定对话风格示例："

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
}
