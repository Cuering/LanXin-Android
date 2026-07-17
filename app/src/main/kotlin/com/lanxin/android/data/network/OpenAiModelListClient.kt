package com.lanxin.android.data.network

import com.lanxin.android.data.dto.openai.response.OpenAiModelsResponse
import com.lanxin.android.util.applyPlatformStreamingTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Fetches remote model ids from OpenAI-compatible `GET {apiUrl}/models`.
 * Aligns with AstrBot ProviderOpenAIOfficial.get_models.
 *
 * Failures surface as [OpenAiModelListResult.Error] — never throws to callers.
 */
@Singleton
class OpenAiModelListClient @Inject constructor(
    private val networkClient: NetworkClient
) {

    suspend fun listModels(
        apiUrl: String,
        token: String?,
        timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS
    ): OpenAiModelListResult {
        val endpoint = ProviderModelListSupport.modelsEndpoint(apiUrl)
        if (endpoint.isEmpty()) {
            return OpenAiModelListResult.Error("empty_api_url")
        }

        return try {
            networkClient().prepareGet(endpoint) {
                applyPlatformStreamingTimeout(timeoutSeconds.coerceAtLeast(5))
                token?.takeIf { it.isNotBlank() }?.let { bearerAuth(it) }
            }.execute { response ->
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                if (!response.status.isSuccess()) {
                    return@execute OpenAiModelListResult.Error(
                        "http_${response.status.value}:${body.take(120)}"
                    )
                }
                parseModelsBody(body)
            }
        } catch (e: Exception) {
            OpenAiModelListResult.Error(e.message?.take(160) ?: "network_error")
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 20

        private val json = Json {
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        /**
         * Pure parser for unit tests — accepts full OpenAI envelope or bare id arrays.
         */
        fun parseModelsBody(body: String): OpenAiModelListResult {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) {
                return OpenAiModelListResult.Error("empty_body")
            }
            return try {
                val parsed = json.decodeFromString(OpenAiModelsResponse.serializer(), trimmed)
                val ids = ProviderModelListSupport.normalizeModelIds(parsed.data.map { it.id })
                if (ids.isEmpty()) {
                    OpenAiModelListResult.Error("no_models")
                } else {
                    OpenAiModelListResult.Success(ids)
                }
            } catch (_: Exception) {
                // Some proxies return `["gpt-4o", …]`
                try {
                    val bare = json.decodeFromString<List<String>>(trimmed)
                    val ids = ProviderModelListSupport.normalizeModelIds(bare)
                    if (ids.isEmpty()) {
                        OpenAiModelListResult.Error("no_models")
                    } else {
                        OpenAiModelListResult.Success(ids)
                    }
                } catch (e: Exception) {
                    OpenAiModelListResult.Error(e.message?.take(120) ?: "parse_error")
                }
            }
        }
    }
}

sealed class OpenAiModelListResult {
    data class Success(val modelIds: List<String>) : OpenAiModelListResult()
    data class Error(val message: String) : OpenAiModelListResult()
}
