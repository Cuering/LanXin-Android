package com.lanxin.android.data.network

/**
 * OpenAI-compatible model latency probe helpers.
 *
 * Probe uses a **neutral, verifiable** short completion — never greetings like
 * "hello" / "你好" / "hi" / "hey" (user hard constraint).
 *
 * Results are settings-local only; failures must not rewrite platform config.
 */
object OpenAiModelProbeSupport {

    /** Fixed token the model is instructed to echo. */
    const val PROBE_EXPECTED_TOKEN: String = "ping-lx-1"

    /**
     * Neutral user prompt for a minimal completion probe.
     * Must contain [PROBE_EXPECTED_TOKEN] and must **not** be a greeting.
     * Token is inlined so source/CI can grep `ping-lx-1` on this constant.
     */
    const val PROBE_USER_PROMPT: String =
        "Reply with exactly this token and nothing else: ping-lx-1"

    /** Soft cap on how many models the user can probe at once (quota safety). */
    const val MAX_PROBE_MODELS: Int = 3

    /**
     * Cap for "fetch models then measure latency" bulk path (settings / add provider).
     * Keeps quota reasonable while covering the common head of a catalog.
     */
    const val MAX_BULK_LATENCY_PROBE_MODELS: Int = 24

    /** Concurrent in-flight probe requests. */
    const val MAX_PROBE_CONCURRENCY: Int = 2

    /** Slightly higher concurrency for bulk latency ranking. */
    const val MAX_BULK_PROBE_CONCURRENCY: Int = 4

    const val DEFAULT_MAX_TOKENS: Int = 16
    const val DEFAULT_TIMEOUT_SECONDS: Int = 15

    /** Greetings that must never appear in the probe prompt (regression guard). */
    val FORBIDDEN_GREETING_SUBSTRINGS: List<String> = listOf(
        "hello",
        "你好",
        "hi",
        "hey",
        "hola",
        "bonjour"
    )

    fun chatCompletionsEndpoint(apiUrl: String): String {
        val trimmed = apiUrl.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.endsWith("/")) {
            "${trimmed}chat/completions"
        } else {
            "$trimmed/chat/completions"
        }
    }

    /**
     * Case-insensitive substring filter for model picker UX.
     * Blank query returns the full list (already normalized).
     */
    fun filterModelIds(models: List<String>, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return models
        return models.filter { it.contains(q, ignoreCase = true) }
    }

    /**
     * Cap selection for probe: prefer current model, then checked ids, max [MAX_PROBE_MODELS].
     */
    fun resolveProbeTargets(
        checkedModelIds: Collection<String>,
        currentModel: String?,
        availableModels: List<String>
    ): List<String> {
        // availableModels reserved for future "only probe listed" modes; currently advisory.
        @Suppress("UNUSED_PARAMETER")
        val _available = availableModels
        val ordered = LinkedHashSet<String>()
        currentModel?.trim()?.takeIf { it.isNotEmpty() }?.let { ordered.add(it) }
        checkedModelIds.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { ordered.add(it) }
        return ordered.take(MAX_PROBE_MODELS)
    }

    fun responseContainsExpectedToken(content: String?): Boolean {
        if (content.isNullOrBlank()) return false
        return content.contains(PROBE_EXPECTED_TOKEN, ignoreCase = false)
    }

    /**
     * Models to probe when ranking by latency after a full list fetch.
     * Prefer [preferredModel] first, then list order; hard-cap [MAX_BULK_LATENCY_PROBE_MODELS].
     */
    fun resolveBulkLatencyTargets(
        modelIds: List<String>,
        preferredModel: String? = null,
        max: Int = MAX_BULK_LATENCY_PROBE_MODELS
    ): List<String> {
        if (max <= 0) return emptyList()
        val ordered = LinkedHashSet<String>()
        preferredModel?.trim()?.takeIf { it.isNotEmpty() }?.let { preferred ->
            val match = modelIds.firstOrNull { it.equals(preferred, ignoreCase = true) }
            ordered.add(match ?: preferred)
        }
        modelIds.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { ordered.add(it) }
        return ordered.take(max.coerceAtLeast(0))
    }

    /**
     * Sort probe results: success first (fast → slow), then failures (fast → slow),
     * then by model id for stability.
     */
    fun sortProbeResultsByLatency(
        results: List<OpenAiModelProbeResult>
    ): List<OpenAiModelProbeResult> {
        return results.sortedWith(
            compareBy<OpenAiModelProbeResult> { !it.success }
                .thenBy { it.latencyMs }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.modelId }
        )
    }

    /**
     * Apply latency ranking to a model id list.
     * - Probed successes: ascending latency
     * - Probed failures: after successes, ascending latency
     * - Unprobed ids: keep original relative order at the end
     */
    fun sortModelIdsByLatency(
        modelIds: List<String>,
        results: List<OpenAiModelProbeResult>
    ): List<String> {
        if (modelIds.isEmpty()) return emptyList()
        val byId = LinkedHashMap<String, OpenAiModelProbeResult>()
        results.forEach { r ->
            val key = r.modelId.trim().lowercase()
            if (key.isNotEmpty()) byId[key] = r
        }
        val ranked = ArrayList<String>(modelIds.size)
        val used = HashSet<String>()

        val successSorted = results
            .filter { it.success }
            .sortedWith(
                compareBy<OpenAiModelProbeResult> { it.latencyMs }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.modelId }
            )
        successSorted.forEach { r ->
            val original = modelIds.firstOrNull { it.equals(r.modelId, ignoreCase = true) }
                ?: r.modelId
            val key = original.lowercase()
            if (used.add(key)) ranked.add(original)
        }

        val failSorted = results
            .filter { !it.success }
            .sortedWith(
                compareBy<OpenAiModelProbeResult> { it.latencyMs }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.modelId }
            )
        failSorted.forEach { r ->
            val original = modelIds.firstOrNull { it.equals(r.modelId, ignoreCase = true) }
                ?: r.modelId
            val key = original.lowercase()
            if (used.add(key)) ranked.add(original)
        }

        modelIds.forEach { id ->
            val key = id.lowercase()
            if (used.add(key)) ranked.add(id)
        }
        return ranked
    }

    /**
     * Map raw client / HTTP error codes to short user-facing Chinese/English-neutral keys
     * that the UI string resources can wrap.
     */
    fun humanizeListError(raw: String): String {
        val msg = raw.trim()
        if (msg.isEmpty()) return "unknown_error"
        val lower = msg.lowercase()
        return when {
            msg == "empty_api_url" || lower.contains("empty_api_url") -> "empty_api_url"
            msg == "unsupported_type" -> "unsupported_type"
            msg.startsWith("http_401") || lower.contains("unauthorized") -> "http_401"
            msg.startsWith("http_403") || lower.contains("forbidden") -> "http_403"
            msg.startsWith("http_404") -> "http_404"
            msg.startsWith("http_429") || lower.contains("rate limit") -> "http_429"
            msg.startsWith("http_5") -> "http_5xx"
            msg == "no_models" || msg == "empty_body" -> msg
            lower.contains("unable to resolve") ||
                lower.contains("unknown host") ||
                lower.contains("failed to connect") ||
                lower.contains("connection refused") ||
                lower.contains("timeout") ||
                lower.contains("timed out") -> "network_error"
            else -> msg.take(120)
        }
    }
}
