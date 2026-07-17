package com.lanxin.android.data.network

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
import kotlin.system.measureTimeMillis
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
 * Minimal non-streaming `POST {apiUrl}/chat/completions` probe for settings latency checks.
 *
 * Uses [OpenAiModelProbeSupport.PROBE_USER_PROMPT] (neutral token echo — not a greeting).
 * Never mutates provider configuration.
 */
@Singleton
class OpenAiModelProbeClient @Inject constructor(
    private val networkClient: NetworkClient
) {

    suspend fun probe(
        apiUrl: String,
        token: String?,
        model: String,
        timeoutSeconds: Int = OpenAiModelProbeSupport.DEFAULT_TIMEOUT_SECONDS
    ): OpenAiModelProbeResult {
        val modelId = model.trim()
        if (modelId.isEmpty()) {
            return OpenAiModelProbeResult(
                modelId = model,
                success = false,
                latencyMs = 0L,
                detail = "empty_model"
            )
        }
        val endpoint = OpenAiModelProbeSupport.chatCompletionsEndpoint(apiUrl)
        if (endpoint.isEmpty()) {
            return OpenAiModelProbeResult(
                modelId = modelId,
                success = false,
                latencyMs = 0L,
                detail = "empty_api_url"
            )
        }

        val body = buildProbeBody(modelId)
        var detail = "network_error"
        var success = false
        val latencyMs = measureTimeMillis {
            try {
                networkClient().preparePost(endpoint) {
                    applyPlatformStreamingTimeout(timeoutSeconds.coerceAtLeast(5))
                    contentType(ContentType.Application.Json)
                    token?.takeIf { it.isNotBlank() }?.let { bearerAuth(it) }
                    setBody(body)
                }.execute { response ->
                    val text = runCatching { response.bodyAsText() }.getOrDefault("")
                    if (!response.status.isSuccess()) {
                        detail = OpenAiModelProbeSupport.humanizeListError(
                            "http_${response.status.value}:${text.take(80)}"
                        )
                        success = false
                        return@execute
                    }
                    val content = extractAssistantContent(text)
                    success = OpenAiModelProbeSupport.responseContainsExpectedToken(content)
                    detail = if (success) {
                        "ok"
                    } else if (content.isNullOrBlank()) {
                        "empty_completion"
                    } else {
                        "unexpected_completion"
                    }
                }
            } catch (e: Exception) {
                success = false
                detail = OpenAiModelProbeSupport.humanizeListError(
                    e.message?.take(160) ?: "network_error"
                )
            }
        }
        return OpenAiModelProbeResult(
            modelId = modelId,
            success = success,
            latencyMs = latencyMs,
            detail = detail
        )
    }

    companion object {
        private val json = Json {
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        /** Pure body builder for unit tests. */
        fun buildProbeBody(modelId: String): String {
            val root = buildJsonObject {
                put("model", modelId)
                put("stream", false)
                put("temperature", 0)
                put("max_tokens", OpenAiModelProbeSupport.DEFAULT_MAX_TOKENS)
                put(
                    "messages",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", OpenAiModelProbeSupport.PROBE_USER_PROMPT)
                            }
                        )
                    }
                )
            }
            return root.toString()
        }

        /** Pure response parser for unit tests. */
        fun extractAssistantContent(body: String): String? {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) return null
            return try {
                val root = json.parseToJsonElement(trimmed).jsonObject
                val choices = root["choices"]?.jsonArray ?: return null
                val first = choices.firstOrNull()?.jsonObject ?: return null
                val message = first["message"]?.jsonObject
                val contentEl = message?.get("content") ?: first["text"]
                contentAsString(contentEl)
            } catch (_: Exception) {
                null
            }
        }

        private fun contentAsString(element: JsonElement?): String? {
            if (element == null) return null
            return when (element) {
                is JsonPrimitive -> element.contentOrNull
                is JsonObject -> element["text"]?.jsonPrimitive?.contentOrNull
                else -> {
                    // content: [{type:text,text:...}, ...]
                    runCatching {
                        element.jsonArray.joinToString("") { part ->
                            part.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        }.ifBlank { null }
                    }.getOrNull()
                }
            }
        }
    }
}

data class OpenAiModelProbeResult(
    val modelId: String,
    val success: Boolean,
    val latencyMs: Long,
    val detail: String
)
