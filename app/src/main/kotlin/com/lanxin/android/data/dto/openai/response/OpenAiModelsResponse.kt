package com.lanxin.android.data.dto.openai.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI-compatible `GET /v1/models` response body.
 * Many gateways (Ollama, OpenRouter, Groq, NewAPI, AIHubMix) share this shape.
 */
@Serializable
data class OpenAiModelsResponse(
    val data: List<OpenAiModelItem> = emptyList(),
    @SerialName("object")
    val objectType: String? = null
)

@Serializable
data class OpenAiModelItem(
    val id: String,
    @SerialName("object")
    val objectType: String? = null,
    val created: Long? = null,
    @SerialName("owned_by")
    val ownedBy: String? = null
)
