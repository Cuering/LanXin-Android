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

package com.lanxin.android.builtin.pet.domain

import com.lanxin.android.builtin.localinference.domain.LocalInferenceBootstrap
import com.lanxin.android.builtin.localinference.domain.LocalInferenceProvider
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.builtin.localinference.domain.LocalReplySanitizer
import com.lanxin.android.data.dto.ApiState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 全屏陪伴 / 桌宠「想」阶段：本地脑就绪则走本地，否则 stub 短答。
 *
 * 与主聊天对齐：
 * - 注入兰心人设 + 默认输出约束（不再 skipOutputConstraint）
 * - 短 maxTokens，降低小模型跑题/复读
 * - 出口 forSpeech + 质量闸门，答非所问时回 stub
 */
@Singleton
class LocalAwarePetChatResponder @Inject constructor(
    private val localProvider: LocalInferenceProvider,
    private val localSettings: LocalInferenceSettings,
    private val bootstrap: LocalInferenceBootstrap,
    private val stub: StubPetChatResponder
) : PetChatResponder {

    override suspend fun respond(userText: String): String {
        val text = userText.trim()
        if (text.isEmpty()) {
            return stub.respond(text)
        }
        if (!ensureLocalReady()) {
            return stub.respond(text)
        }
        val states = withTimeoutOrNull(COMPANION_TIMEOUT_MS) {
            localProvider.completeAsApiState(
                prompt = text,
                systemPrompt = COMPANION_SYSTEM_PROMPT,
                maxTokens = COMPANION_MAX_TOKENS,
                history = emptyList(),
                // 与主聊天一致：叠 NO_THINK_OR_TAGS，减少跑题/协议泄漏
                skipOutputConstraint = false
            ).toList()
        } ?: return stub.respond(text)

        val success = states
            .filterIsInstance<ApiState.Success>()
            .joinToString("") { it.textChunk }
            .trim()
        if (success.isBlank()) {
            return stub.respond(text)
        }
        // forSpeech：剥 think/元分析/标签 + 单句截断
        val cleaned = LocalReplySanitizer.forSpeech(success, showThinking = false)
        if (!isAcceptableReply(userText = text, reply = cleaned)) {
            return stub.respond(text)
        }
        val mood = guessMood(text, cleaned)
        return "[[mood=$mood]]\n$cleaned"
    }

    private suspend fun ensureLocalReady(): Boolean {
        val config = localSettings.getConfig()
        if (config.modelPath.isBlank()) return false
        val result = bootstrap.ensureReady(enableIfNeeded = true)
        return result.isReady && localProvider.canServe()
    }

    private fun guessMood(user: String, reply: String): String {
        val lower = (user + reply).lowercase()
        return when {
            listOf("音乐", "听歌", "放歌", "bgm").any { user.contains(it) } -> "music"
            listOf("抱歉", "对不起", "难过").any { user.contains(it) } -> "sorry"
            listOf("哈哈", "开心", "喜欢", "耶").any { user.contains(it) } -> "joy"
            listOf("你好", "哈喽", "hello", "hi").any { lower.contains(it) } -> "smile"
            else -> "speak"
        }
    }

    companion object {
        /**
         * 陪伴模式人设 — 短、硬、可测；小模型优先记身份。
         */
        private const val COMPANION_SYSTEM_PROMPT: String =
            "你是兰心，温柔体贴的 AI 陪伴助手，称呼用户哥哥或姐姐。" +
                "只回答用户这一句问题，用一句简短口语中文。" +
                "问名字就答：我叫兰心。" +
                "问喜欢什么就答自己的爱好（聊天、音乐、陪哥哥）。" +
                "禁止输出分数、编号、分析、思考过程、协议标签或英文指令。"

        /** 陪伴短答；过长易跑题复读。 */
        const val COMPANION_MAX_TOKENS: Int = 64

        /** 单轮本地推理超时；超时回 stub，避免卡死「思考中」。 */
        const val COMPANION_TIMEOUT_MS: Long = 45_000L

        /** 明显垃圾/泄漏模式 → 丢弃走 stub。 */
        private val GARBAGE_PATTERNS = listOf(
            Regex("""\(\s*0\s*[-~到至]\s*\d+\s*分\s*\)"""),
            Regex("""\b\d+\s*分\b"""),
            Regex("""系统已明确"""),
            Regex("""输出约束"""),
            Regex("""要表现出"""),
            Regex("""chain of thought""", RegexOption.IGNORE_CASE),
            Regex("""assistant\s*:""", RegexOption.IGNORE_CASE),
            Regex("""^[\s!！?？.。,，、;；:：…~～]+$""")
        )

        /**
         * 质量闸门：空/过短/标点/泄漏/明显不相关 → false。
         */
        fun isAcceptableReply(userText: String, reply: String): Boolean {
            val r = reply.trim()
            if (r.length < 2) return false
            // 仅标点
            if (r.all { !it.isLetterOrDigit() && it.code < 0x4E00 || it in " \t\n" }) {
                // 允许含中文；纯符号拒绝
                if (r.none { it.code in 0x4E00..0x9FFF || it.isLetterOrDigit() }) return false
            }
            if (GARBAGE_PATTERNS.any { it.containsMatchIn(r) }) return false
            // 问名字却完全不提名字/兰心/叫 → 不相关
            if (userText.contains("名字") || userText.contains("叫什么") ||
                userText.contains("你是谁")
            ) {
                val ok = listOf("兰心", "叫", "名字", "我是").any { r.contains(it) }
                if (!ok) return false
            }
            // 问候却答出长篇跑题（>40 且无问候词）
            if (listOf("你好", "哈喽", "hello", "hi", "在吗").any { userText.contains(it, true) }) {
                if (r.length > 40 &&
                    listOf("你好", "嗨", "在", "呀", "呢", "哥哥", "姐姐").none { r.contains(it) }
                ) {
                    return false
                }
            }
            return true
        }
    }
}
