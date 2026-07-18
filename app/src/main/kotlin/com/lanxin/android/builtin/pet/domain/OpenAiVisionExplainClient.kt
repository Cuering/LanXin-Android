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

import com.lanxin.android.data.network.NetworkClient
import com.lanxin.android.data.network.OpenAiModelProbeSupport
import com.lanxin.android.data.repository.SettingRepository
import com.lanxin.android.util.applyPlatformStreamingTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * OpenAI 兼容 chat/completions + image_url 视觉讲解。
 *
 * - 读已配置且 enabled 的 PlatformV2
 * - 无 vision 模型 → [VisionExplainResult.Unavailable]（不编造）
 * - 帧仅内存 base64，不落盘
 */
@Singleton
class OpenAiVisionExplainClient @Inject constructor(
    private val settingRepository: SettingRepository,
    private val networkClient: NetworkClient
) : VisionExplainClient {

    override suspend fun resolveCapability(): VisionCapability {
        val platforms = runCatching { settingRepository.fetchPlatformV2s() }
            .getOrDefault(emptyList())
            .filter { it.enabled }
        if (platforms.isEmpty()) {
            return VisionCapability(
                available = false,
                message = VisionModelCapability.MSG_NO_PLATFORM
            )
        }
        // 优先：enabled 且模型名像 vision
        val visionFirst = platforms.firstOrNull {
            VisionModelCapability.denyVisionReason(
                hasEnabledPlatform = true,
                clientType = it.compatibleType,
                modelId = it.model
            ) == null
        }
        if (visionFirst != null) {
            return VisionCapability(
                available = true,
                message = "${visionFirst.name} · ${visionFirst.model}",
                platformName = visionFirst.name,
                modelId = visionFirst.model,
                clientType = visionFirst.compatibleType,
                apiUrl = visionFirst.apiUrl,
                token = visionFirst.token
            )
        }
        val first = platforms.first()
        val reason = VisionModelCapability.denyVisionReason(
            hasEnabledPlatform = true,
            clientType = first.compatibleType,
            modelId = first.model
        ) ?: VisionModelCapability.MSG_NO_VISION
        return VisionCapability(
            available = false,
            message = reason,
            platformName = first.name,
            modelId = first.model,
            clientType = first.compatibleType,
            apiUrl = first.apiUrl,
            token = first.token
        )
    }

    override suspend fun explain(
        question: String,
        frame: CompanionVisionFrame
    ): VisionExplainResult {
        val cap = resolveCapability()
        if (!cap.available) {
            return VisionExplainResult.Unavailable(cap.message)
        }
        val endpoint = OpenAiModelProbeSupport.chatCompletionsEndpoint(cap.apiUrl)
        if (endpoint.isEmpty()) {
            return VisionExplainResult.Unavailable(VisionModelCapability.MSG_NO_PLATFORM)
        }
        val body = buildVisionBody(
            modelId = cap.modelId,
            question = question.trim(),
            dataUri = frame.dataUri()
        )
        return try {
            var result: VisionExplainResult = VisionExplainResult.Error("network_error")
            networkClient().preparePost(endpoint) {
                applyPlatformStreamingTimeout(45)
                contentType(ContentType.Application.Json)
                cap.token?.takeIf { it.isNotBlank() }?.let { bearerAuth(it) }
                setBody(body)
            }.execute { response ->
                val text = runCatching { response.bodyAsText() }.getOrDefault("")
                if (!response.status.isSuccess()) {
                    result = VisionExplainResult.Error(
                        "视觉请求失败（HTTP ${response.status.value}）。${text.take(80)}"
                    )
                    return@execute
                }
                val content = extractAssistantContent(text)
                result = if (content.isNullOrBlank()) {
                    VisionExplainResult.Error("模型没有返回讲解内容。")
                } else {
                    VisionExplainResult.Ok(
                        replyText = "[[mood=think]]\n$content",
                        modelId = cap.modelId
                    )
                }
            }
            result
        } catch (e: Exception) {
            VisionExplainResult.Error(
                "视觉请求出错：${e.message?.take(120) ?: "network_error"}"
            )
        }
    }

    companion object {
        private val json = Json {
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        /** 纯 body，可单测。 */
        fun buildVisionBody(modelId: String, question: String, dataUri: String): String {
            val userText = if (question.isBlank()) {
                "请用中文简要描述你看到的画面，并像陪伴助手一样讲解。"
            } else {
                "用户在全屏陪伴里问：「$question」\n" +
                    "请结合画面用中文讲解；简洁自然，不要编造看不清的细节。"
            }
            val root = buildJsonObject {
                put("model", modelId)
                put("stream", false)
                put("temperature", 0.4)
                put("max_tokens", 512)
                put(
                    "messages",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put(
                                    "content",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("type", "text")
                                                put("text", userText)
                                            }
                                        )
                                        add(
                                            buildJsonObject {
                                                put("type", "image_url")
                                                put(
                                                    "image_url",
                                                    buildJsonObject {
                                                        put("url", dataUri)
                                                        put("detail", "low")
                                                    }
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }
            return root.toString()
        }

        fun extractAssistantContent(body: String): String? {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }
                .getOrNull() ?: return null
            val choices = root["choices"]?.jsonArray ?: return null
            val first = choices.firstOrNull()?.jsonObject ?: return null
            val message = first["message"]?.jsonObject ?: return null
            return contentAsString(message["content"])
        }

        private fun contentAsString(element: JsonElement?): String? {
            if (element == null) return null
            return when (element) {
                is JsonPrimitive -> element.contentOrNull?.trim()
                is JsonObject -> element["text"]?.jsonPrimitive?.contentOrNull?.trim()
                else -> {
                    // 部分兼容端点 content 为数组
                    runCatching {
                        element.jsonArray.joinToString("") { part ->
                            val obj = part.jsonObject
                            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                                "text" -> obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                else -> ""
                            }
                        }.trim().ifBlank { null }
                    }.getOrNull()
                }
            }
        }
    }
}
