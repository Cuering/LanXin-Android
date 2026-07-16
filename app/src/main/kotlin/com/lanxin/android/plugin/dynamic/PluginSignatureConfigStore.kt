package com.lanxin.android.plugin.dynamic

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
 * 签名策略配置（内存快照）。
 */
data class PluginSignatureConfig(
    val policy: SignaturePolicy,
    val allowlist: Set<String> = emptySet()
) {
    fun allowlistCsv(): String = allowlist.sorted().joinToString(",")
}

/**
 * 持久化 `filesDir/plugin-signature.json`。
 *
 * 格式：
 * ```json
 * { "policy": "allow_all", "allowlist": ["abcd..."] }
 * ```
 *
 * 默认策略：
 * - debug： [SignaturePolicy.ALLOW_ALL]
 * - release： [SignaturePolicy.ALLOWLIST]（空名单 = 拒绝加载）
 */
class PluginSignatureConfigStore(
    private val file: File,
    private val defaultPolicy: SignaturePolicy
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    @Volatile
    private var cached: PluginSignatureConfig? = null

    fun load(): PluginSignatureConfig {
        cached?.let { return it }
        val loaded = readFromDisk() ?: PluginSignatureConfig(policy = defaultPolicy)
        cached = loaded
        return loaded
    }

    fun save(config: PluginSignatureConfig) {
        val normalized = PluginSignatureConfig(
            policy = config.policy,
            allowlist = config.allowlist.map { CertDigestUtils.normalize(it) }
                .filter { it.isNotEmpty() }
                .toSet()
        )
        file.parentFile?.mkdirs()
        val body = buildJsonObject {
            put("policy", normalized.policy.wireName)
            put(
                "allowlist",
                buildJsonArray {
                    normalized.allowlist.sorted().forEach { add(JsonPrimitive(it)) }
                }
            )
        }
        file.writeText(json.encodeToString(JsonObject.serializer(), body), Charsets.UTF_8)
        cached = normalized
    }

    fun setPolicy(policy: SignaturePolicy) {
        val current = load()
        save(current.copy(policy = policy))
    }

    fun setAllowlist(digests: Collection<String>) {
        val current = load()
        save(
            current.copy(
                allowlist = digests.map { CertDigestUtils.normalize(it) }
                    .filter { it.isNotEmpty() }
                    .toSet()
            )
        )
    }

    fun invalidateCache() {
        cached = null
    }

    private fun readFromDisk(): PluginSignatureConfig? {
        if (!file.isFile) return null
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        if (text.isBlank()) return null
        return runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            val policy = SignaturePolicy.fromWire(
                root["policy"]?.jsonPrimitive?.contentOrNull,
                fallback = defaultPolicy
            )
            val allow = linkedSetOf<String>()
            val arr = root["allowlist"]
            if (arr is JsonArray) {
                for (el in arr) {
                    val n = CertDigestUtils.normalize(el.jsonPrimitive.contentOrNull.orEmpty())
                    if (n.isNotEmpty()) allow += n
                }
            } else {
                val csv = root["allowlist_csv"]?.jsonPrimitive?.contentOrNull.orEmpty()
                allow += CertDigestUtils.parseAllowlist(csv)
            }
            PluginSignatureConfig(policy = policy, allowlist = allow)
        }.getOrNull()
    }
}
