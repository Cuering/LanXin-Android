package com.lanxin.android.plugin.dynamic

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

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
 * 与 [PluginStateStore] 一样使用同步文件 IO，便于 JVM 单测与启动路径。
 *
 * 默认策略：
 * - debug / 缺省可调试： [SignaturePolicy.ALLOW_ALL]
 * - release： [SignaturePolicy.ALLOWLIST]（空名单 = 拒绝加载）
 */
class PluginSignatureConfigStore(
    private val file: File,
    private val defaultPolicy: SignaturePolicy
) {

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
        val json = JSONObject()
            .put("policy", normalized.policy.wireName)
            .put("allowlist", JSONArray(normalized.allowlist.sorted()))
        file.writeText(json.toString())
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
        return runCatching {
            val root = JSONObject(file.readText())
            val policy = SignaturePolicy.fromWire(
                root.optString("policy", defaultPolicy.wireName),
                fallback = defaultPolicy
            )
            val allow = linkedSetOf<String>()
            val arr = root.optJSONArray("allowlist")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val n = CertDigestUtils.normalize(arr.optString(i, ""))
                    if (n.isNotEmpty()) allow += n
                }
            } else {
                val csv = root.optString("allowlist_csv", "")
                allow += CertDigestUtils.parseAllowlist(csv)
            }
            PluginSignatureConfig(policy = policy, allowlist = allow)
        }.getOrNull()
    }
}
