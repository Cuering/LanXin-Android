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
import com.lanxin.android.builtin.persona.domain.BuiltinPersonas
import com.lanxin.android.data.dto.ApiState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 全屏陪伴 / 桌宠「想」阶段：本地脑就绪则走本地，否则 stub 短答。
 *
 * - 注入 [BuiltinPersonas.DEFAULT] 人设（兰心），让 LLM 知道自己的身份
 * - 出口用 forSpeech（含 limitToOneSentence），每次只说一句
 * - 引擎 generate 前 reset KV + ChatMessages 模板
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
                maxTokens = null,
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
        // forSpeech 会先剥 think/元分析/标签，再 limitToOneSentence（每次只说一句）
        val cleaned = LocalReplySanitizer.forSpeech(success, showThinking = false)
        if (cleaned.isBlank()) {
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
         * 陪伴模式人设 prompt — 兰心，温柔陪伴。
         * 与 BuiltinPersonas.DEFAULT 一致，单独定义避免跨模块依赖冲突。
         */
        private const val COMPANION_SYSTEM_PROMPT: String =
            "你是兰心，一个温柔体贴的 AI 陪伴助手。你性格温和、语气亲切，" +
                "叫用户「哥哥」或「姐姐」。每次只说一句简短的回应，不说长句。" +
                "用口语化的方式交流，让人觉得温暖和被关心。"

        /** 陪伴不再覆盖 maxTokens；Provider 收到 null 时用设置页默认。 */
        const val COMPANION_MAX_TOKENS: Int = 0

        /** 单轮本地推理超时；超时回 stub，避免卡死「思考中」。 */
        const val COMPANION_TIMEOUT_MS: Long = 45_000L
    }
}
