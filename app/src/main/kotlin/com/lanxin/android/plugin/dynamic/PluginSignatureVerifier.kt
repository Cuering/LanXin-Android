package com.lanxin.android.plugin.dynamic

import java.io.File

/**
 * 插件签名校验结果（Phase 5.6 完整实现预留）。
 */
sealed class PluginSignatureResult {
    data object Trusted : PluginSignatureResult()
    data class Rejected(val reason: String) : PluginSignatureResult()
}

/**
 * 插件 APK 签名验证钩子。
 *
 * 5.3 MVP 使用 [AllowAllPluginSignatureVerifier]；
 * 5.6 替换为证书白名单实现，无需改动加载管线调用点。
 */
fun interface PluginSignatureVerifier {
    fun verify(apkFile: File): PluginSignatureResult
}

/** 默认：全部信任（开发 / MVP）。 */
object AllowAllPluginSignatureVerifier : PluginSignatureVerifier {
    override fun verify(apkFile: File): PluginSignatureResult = PluginSignatureResult.Trusted
}

/** 测试用：全部拒绝。 */
object RejectAllPluginSignatureVerifier : PluginSignatureVerifier {
    override fun verify(apkFile: File): PluginSignatureResult =
        PluginSignatureResult.Rejected("signature rejected by policy")
}
