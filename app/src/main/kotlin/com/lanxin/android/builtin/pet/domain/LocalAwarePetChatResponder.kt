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
 * 推理尽量对齐 MNN 官方 App：
 * - 不塞长 system / 输出约束（[skipOutputConstraint]=true）
 * - 不硬砍 maxTokens（用设置默认，通常 512）
 * - 引擎 generate 前 reset KV + ChatMessages 模板
 * - 出口只做轻量 forDisplay（剥 think/标签），**不再** limitToOneSentence
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
                systemPrompt = null,
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
        // 轻量清洗 only：不硬截一句
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
        /**
         * 陪伴不再覆盖 maxTokens；测试侧用此常量表示「走默认」哨兵。
         * Provider 收到 null 时用设置页 [LocalInferenceConfig.maxTokens]（默认 512）。
         */
        const val COMPANION_MAX_TOKENS: Int = 0

        /** 单轮本地推理超时；超时回 stub，避免卡死「思考中」。 */
        const val COMPANION_TIMEOUT_MS: Long = 45_000L
    }
}
