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
 * - 不塞 Chat 输入框；不拉云端全链路（陪伴优先低延迟离线）
 * - 限短 maxTokens，减少元分析长输出与等待
 * - 出口再清洗一次，避免 think / 元分析泄漏
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
        val system = LocalReplySanitizer.appendOutputConstraint(
            systemPrompt = COMPANION_SYSTEM,
            showThinking = false
        )
        val states = withTimeoutOrNull(COMPANION_TIMEOUT_MS) {
            localProvider.completeAsApiState(
                prompt = text,
                systemPrompt = system,
                maxTokens = COMPANION_MAX_TOKENS
            ).toList()
        } ?: return stub.respond(text)

        val success = states
            .filterIsInstance<ApiState.Success>()
            .joinToString("") { it.textChunk }
            .trim()
        if (success.isBlank()) {
            return stub.respond(text)
        }
        val cleaned = LocalReplySanitizer.forDisplay(success, showThinking = false)
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
        /** 陪伴 system：角色短答，叠加输出约束。 */
        const val COMPANION_SYSTEM: String =
            "你是兰心，温柔、亲近的中文陪伴角色。用户在全屏陪伴里跟你说话。" +
                "用口语短句回复，像面对面聊天；不要列点、不要写报告。"

        /** 陪伴生成上限：短答优先，降低延迟与元分析空间。 */
        const val COMPANION_MAX_TOKENS: Int = 128

        /** 单轮本地推理超时；超时回 stub，避免卡死「思考中」。 */
        const val COMPANION_TIMEOUT_MS: Long = 45_000L
    }
}
