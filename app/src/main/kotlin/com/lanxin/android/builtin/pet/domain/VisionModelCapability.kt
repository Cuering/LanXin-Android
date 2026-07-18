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

import com.lanxin.android.data.model.ClientType

/**
 * 视觉/多模态能力启发式（纯逻辑，可单测）。
 *
 * **禁止**在无 vision 模型时假装本地 VLM 会看图。
 * 仅当 Provider 为 OpenAI 兼容路径且模型名像 vision 时才允许送图。
 */
object VisionModelCapability {

    /** 用户可见：当前配置看不了图。 */
    const val MSG_NO_VISION: String =
        "当前模型看不了图。请在设置里配置支持视觉的多模态模型（如 gpt-4o / gemini / qwen-vl），" +
            "我不会假装本地已经会看。"

    const val MSG_NO_PLATFORM: String =
        "还没有可用的云端 Provider。配置好支持视觉的模型后，再开「看世界」提问讲解。"

    const val MSG_PROVIDER_PATH_UNSUPPORTED: String =
        "当前 Provider 类型暂未接线视觉讲解（V1 仅 OpenAI 兼容 chat/completions + image_url）。" +
            "请改用 OpenAI / OpenRouter / 自定义兼容端点上的 vision 模型。"

    const val MSG_CAPTURE_FAILED: String =
        "这轮没看清画面，先按文字聊。你可以点「看一眼」或再说一次。"

    /**
     * 模型 id 是否像支持图像理解。
     * 保守：宁可漏判也不假装会看。
     */
    fun looksLikeVisionModel(modelId: String): Boolean {
        val m = modelId.trim().lowercase()
        if (m.isEmpty()) return false
        // 明确排除纯文本常见模型（避免 gpt-4 无 -vision 误伤：gpt-4o 另匹配）
        if (m == "gpt-4" || m.startsWith("gpt-4-0") || m.startsWith("gpt-3.5")) return false
        val needles = listOf(
            "gpt-4o",
            "gpt-4.1",
            "gpt-4-turbo",
            "gpt-4-vision",
            "o1",
            "o3",
            "o4",
            "vision",
            "gemini",
            "claude-3",
            "claude-4",
            "claude-sonnet",
            "claude-opus",
            "claude-haiku",
            "qwen-vl",
            "qwen2-vl",
            "qwen2.5-vl",
            "llava",
            "pixtral",
            "glm-4v",
            "internvl",
            "phi-3.5-vision",
            "phi-4-multimodal",
            "minicpm-v"
        )
        return needles.any { m.contains(it) }
    }

    /** V1 仅 OpenAI 兼容 image_url 路径。 */
    fun supportsOpenAiVisionPath(clientType: ClientType): Boolean = when (clientType) {
        ClientType.OPENAI,
        ClientType.OPENROUTER,
        ClientType.CUSTOM,
        ClientType.GROQ,
        ClientType.OLLAMA -> true
        ClientType.ANTHROPIC,
        ClientType.GOOGLE,
        ClientType.LANXIN -> false
    }

    /**
     * 综合判断是否允许送图讲解。
     * @return null = 允许；非 null = 拒绝原因（用户可见文案）
     */
    fun denyVisionReason(
        hasEnabledPlatform: Boolean,
        clientType: ClientType?,
        modelId: String?
    ): String? {
        if (!hasEnabledPlatform || clientType == null || modelId.isNullOrBlank()) {
            return MSG_NO_PLATFORM
        }
        if (!supportsOpenAiVisionPath(clientType)) {
            return MSG_PROVIDER_PATH_UNSUPPORTED
        }
        if (!looksLikeVisionModel(modelId)) {
            return MSG_NO_VISION
        }
        return null
    }
}
