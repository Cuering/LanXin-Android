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
 * 对齐 MNNChat 调用面（不是「少叠约束」那么简单）：
 * - system 默认空；不叠输出约束
 * - Provider skipOutputConstraint → 原文/极轻清洗出口
 * - 质量闸门只拦明显泄漏/空壳，不拦正常多句
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
        // 对齐 MNNChat 调用面：
        // - system 默认空（模型包 chat_template / 自身指令已够用；硬塞 system 反而易跑题）
        // - skipOutputConstraint=true → Provider 走 lightClean 原文出口
        // - 出口不再 forSpeech 硬截 / 重闸门，避免「1.」假阳性
        val states = withTimeoutOrNull(COMPANION_TIMEOUT_MS) {
            localProvider.completeAsApiState(
                prompt = text,
                systemPrompt = null,
                maxTokens = COMPANION_MAX_TOKENS,
                history = emptyList(),
                skipOutputConstraint = true
            ).toList()
        } ?: return stub.respond(text)

        val success = states
            .filterIsInstance<ApiState.Success>()
            .joinToString("") { it.textChunk }
            .trim()
        if (success.isBlank()) {
            return stub.respond(text)
        }
        // Provider 已 lightClean；这里只再剥 [[mood]] 类隐藏标，保留多句自然输出
        val cleaned = LocalReplySanitizer.lightCleanForBareChat(success)
            .ifBlank { success }
            .trim()
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
         * 可选 system（调试/对比用）。默认陪伴路径传 null，贴近 MNNChat 裸 user 消息。
         */
        const val COMPANION_SYSTEM_PROMPT: String =
            "你是兰心。用一两句自然中文直接回答。"

        /**
         * 陪伴生成上限。MNNChat 默认往往更宽松；过小会截断成「1.」半截。
         * 256 在手机小模型上仍可接受。
         */
        const val COMPANION_MAX_TOKENS: Int = 256

        /** 单轮本地推理超时；超时回 stub，避免卡死「思考中」。 */
        const val COMPANION_TIMEOUT_MS: Long = 45_000L

        /** 明显垃圾/泄漏模式 → 丢弃走 stub。 */
        private val GARBAGE_PATTERNS = listOf(
            // 全角/半角括号分数泄漏，如「（0-5 分） 4 你好！」
            Regex("""[（(]\s*0\s*[-~～到至]\s*\d+\s*分\s*[）)]"""),
            Regex("""\d+\s*分[）)\s，,]"""),
            Regex("""系统已明确"""),
            Regex("""输出约束"""),
            Regex("""要表现出"""),
            Regex("""总是能给出"""),
            Regex("""简洁而有洞见"""),
            Regex("""chain of thought""", RegexOption.IGNORE_CASE),
            Regex("""thinking process""", RegexOption.IGNORE_CASE),
            Regex("""assistant\s*:""", RegexOption.IGNORE_CASE),
            // 纯编号大纲壳
            Regex("""^\\d+[\.、．]?$"""),
            Regex("""(?is)^thinking\\s*process.*$"""),
            Regex("""^[\s!！?？.。,，、;；:：…~～]+$""")
        )

        /**
         * 质量闸门：空/过短/标点/泄漏/明显不相关 → false。
         */
        fun isAcceptableReply(userText: String, reply: String): Boolean {
            val r = reply.trim()
            if (r.length < 2) return false
            // 纯符号/空白 → 拒绝（如「！」）
            val hasContent = r.any {
                it.isLetterOrDigit() || it.code in 0x4E00..0x9FFF
            }
            if (!hasContent) return false
            if (GARBAGE_PATTERNS.any { it.containsMatchIn(r) }) return false
            // 只拦「明显泄漏/空壳」；主题相关不再硬拒（MNNChat 不会做这层语义闸门）
            // 名字题若完全答成约束泄漏，已由 GARBAGE_PATTERNS 覆盖
            return true
        }
    }
}
