package com.lanxin.android.plugin.dynamic

import java.io.File

/**
 * 签名策略（Phase 5.6）。
 *
 * wire 名称写入 plugin-signature.json / 日志 / UI。
 */
enum class SignaturePolicy(val wireName: String) {
    /** 开发默认：全部信任。 */
    ALLOW_ALL("allow_all"),

    /** 全部拒绝（安全基线 / 测试）。 */
    DENY_ALL("deny_all"),

    /** 证书 SHA-256 白名单；名单为空时失败关闭。 */
    ALLOWLIST("allowlist");

    companion object {
        fun fromWire(raw: String?, fallback: SignaturePolicy = ALLOW_ALL): SignaturePolicy {
            val key = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireName == key } ?: fallback
        }
    }
}

/**
 * 插件签名校验结果。
 */
sealed class PluginSignatureResult {
    data class Trusted(
        val policy: String,
        val certificateSha256: List<String> = emptyList()
    ) : PluginSignatureResult()

    data class Rejected(
        val reason: String,
        val policy: String = "",
        val certificateSha256: List<String> = emptyList()
    ) : PluginSignatureResult()
}

/**
 * 插件 APK 签名验证钩子。
 *
 * 加载管线在 ClassLoader 创建之前调用；[PluginSignatureResult.Rejected] 则不加载。
 */
fun interface PluginSignatureVerifier {
    fun verify(apkFile: File): PluginSignatureResult
}

/** 全部信任（debug / 开发默认）。 */
object AllowAllPluginSignatureVerifier : PluginSignatureVerifier {
    override fun verify(apkFile: File): PluginSignatureResult =
        PluginSignatureResult.Trusted(policy = SignaturePolicy.ALLOW_ALL.wireName)
}

/** 全部拒绝（测试 / 强制关闭动态插件）。 */
object RejectAllPluginSignatureVerifier : PluginSignatureVerifier {
    override fun verify(apkFile: File): PluginSignatureResult =
        PluginSignatureResult.Rejected(
            reason = "signature rejected by policy",
            policy = SignaturePolicy.DENY_ALL.wireName
        )
}

/**
 * 可配置策略验证器：AllowAll / DenyAll / Allowlist。
 *
 * 证书摘要由 [ApkCertDigestProvider] 提供，便于单测注入 Fake。
 */
class ConfigurablePluginSignatureVerifier(
    private val policyProvider: () -> SignaturePolicy,
    private val allowlistProvider: () -> Set<String>,
    private val certProvider: ApkCertDigestProvider = JarApkCertDigestProvider()
) : PluginSignatureVerifier {

    override fun verify(apkFile: File): PluginSignatureResult {
        val policy = policyProvider()
        return when (policy) {
            SignaturePolicy.ALLOW_ALL ->
                PluginSignatureResult.Trusted(policy = policy.wireName)

            SignaturePolicy.DENY_ALL ->
                PluginSignatureResult.Rejected(
                    reason = "策略 deny_all：拒绝所有动态插件",
                    policy = policy.wireName
                )

            SignaturePolicy.ALLOWLIST -> verifyAllowlist(apkFile, policy)
        }
    }

    private fun verifyAllowlist(
        apkFile: File,
        policy: SignaturePolicy
    ): PluginSignatureResult {
        val allowlist = allowlistProvider()
            .map { CertDigestUtils.normalize(it) }
            .filter { it.isNotEmpty() }
            .toSet()

        if (allowlist.isEmpty()) {
            return PluginSignatureResult.Rejected(
                reason = "策略 allowlist 但白名单为空（失败关闭）",
                policy = policy.wireName
            )
        }

        val digestsResult = certProvider.digests(apkFile)
        val digests = digestsResult.getOrElse { e ->
            return PluginSignatureResult.Rejected(
                reason = "无法读取 APK 签名证书：${e.message ?: e.javaClass.simpleName}",
                policy = policy.wireName
            )
        }.map { CertDigestUtils.normalize(it) }.filter { it.isNotEmpty() }

        if (digests.isEmpty()) {
            return PluginSignatureResult.Rejected(
                reason = "APK 无可用签名证书摘要（未签名或非 V1/可读证书）",
                policy = policy.wireName
            )
        }

        val matched = digests.any { allowlist.contains(it) }
        return if (matched) {
            PluginSignatureResult.Trusted(
                policy = policy.wireName,
                certificateSha256 = digests
            )
        } else {
            val preview = digests.take(2).joinToString(",")
            PluginSignatureResult.Rejected(
                reason = "证书不在白名单（sha256=$preview）",
                policy = policy.wireName,
                certificateSha256 = digests
            )
        }
    }
}

/** 基于 [PluginSignatureConfigStore] 的生产验证器。 */
class StoreBackedPluginSignatureVerifier(
    private val store: PluginSignatureConfigStore,
    private val certProvider: ApkCertDigestProvider = JarApkCertDigestProvider()
) : PluginSignatureVerifier {

    private val delegate = ConfigurablePluginSignatureVerifier(
        policyProvider = { store.load().policy },
        allowlistProvider = { store.load().allowlist },
        certProvider = certProvider
    )

    override fun verify(apkFile: File): PluginSignatureResult = delegate.verify(apkFile)
}
