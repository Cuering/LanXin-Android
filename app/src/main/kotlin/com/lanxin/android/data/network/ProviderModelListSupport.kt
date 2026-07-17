package com.lanxin.android.data.network

import com.lanxin.android.data.model.ClientType

/**
 * Provider alignment helpers: which platforms can remote-fetch model lists
 * via OpenAI-compatible `GET {apiUrl}/models`, and how chat routes map.
 *
 * Does **not** change [com.lanxin.android.builtin.localinference.domain.ChatRouter]
 * (needsTools → cloud etc. stay intact). This only clarifies cloud provider config.
 */
object ProviderModelListSupport {

    /**
     * Client types that speak OpenAI Chat Completions or Responses over an
     * OpenAI-compatible base URL, and typically expose `/models`.
     */
    fun supportsOpenAiCompatibleModelList(clientType: ClientType): Boolean = when (clientType) {
        ClientType.OPENAI,
        ClientType.GROQ,
        ClientType.OLLAMA,
        ClientType.OPENROUTER,
        ClientType.CUSTOM -> true
        ClientType.ANTHROPIC,
        ClientType.GOOGLE,
        ClientType.LANXIN -> false
    }

    /**
     * Whether chat for this client is dispatched through OpenAI-compatible HTTP
     * (Chat Completions or Responses). Mirrors [ChatRepositoryImpl] branches.
     */
    fun usesOpenAiCompatibleChat(clientType: ClientType): Boolean = when (clientType) {
        ClientType.OPENAI,
        ClientType.GROQ,
        ClientType.OLLAMA,
        ClientType.OPENROUTER,
        ClientType.CUSTOM -> true
        ClientType.ANTHROPIC,
        ClientType.GOOGLE,
        ClientType.LANXIN -> false
    }

    /**
     * Build models endpoint from a configured platform API URL.
     * Accepts both `…/v1/` and `…/v1` forms; avoids double slashes.
     */
    fun modelsEndpoint(apiUrl: String): String {
        val trimmed = apiUrl.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.endsWith("/")) {
            "${trimmed}models"
        } else {
            "$trimmed/models"
        }
    }

    /**
     * Sort + de-dupe model ids for UI (stable, case-insensitive).
     */
    fun normalizeModelIds(ids: Iterable<String>): List<String> {
        val seen = LinkedHashSet<String>()
        val result = ArrayList<String>()
        ids.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .forEach { id ->
                val key = id.lowercase()
                if (seen.add(key)) {
                    result.add(id)
                }
            }
        return result
    }
}
