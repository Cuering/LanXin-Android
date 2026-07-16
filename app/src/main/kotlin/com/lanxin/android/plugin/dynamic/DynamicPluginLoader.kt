package com.lanxin.android.plugin.dynamic

import com.lanxin.android.core.updater.VersionComparator
import com.lanxin.android.plugin.LanXinPlugin
import java.io.File

/**
 * 动态插件加载编排（扫描 → 校验 → ClassLoader → 实例）。
 *
 * 不直接写 [com.lanxin.android.plugin.PluginManager] 注册表，
 * 由 Manager 调用本类并决定 register / onLoad。
 */
class DynamicPluginLoader(
    private val classLoaderFactory: DynamicPluginClassLoaderFactory = AndroidPathClassLoaderFactory,
    private val signatureVerifier: PluginSignatureVerifier = AllowAllPluginSignatureVerifier,
    private val parentClassLoader: ClassLoader = LanXinPlugin::class.java.classLoader
        ?: ClassLoader.getSystemClassLoader(),
    private val appVersionName: String = "",
    /**
     * 可选：不走 ClassLoader，直接由外部提供实例（单测用）。
     * 返回 null 时回退真实实例化。
     */
    private val pluginFactory: ((PluginManifest, File) -> LanXinPlugin?)? = null
) {

    /**
     * 加载单个 APK：解析清单、签名、版本、实例化。
     * 成功时 [LoadedPackage.plugin] 非空；不在此调用 onLoad。
     */
    fun loadPackage(apkFile: File): LoadPackageResult {
        if (!apkFile.isFile) {
            return LoadPackageResult.Error(
                apkPath = apkFile.absolutePath,
                pluginId = null,
                reason = "文件不存在: ${apkFile.path}"
            )
        }

        val manifest = PluginManifestParser.parseFromApk(apkFile)
            ?: return LoadPackageResult.Error(
                apkPath = apkFile.absolutePath,
                pluginId = null,
                reason = "无法解析插件清单"
            )

        when (val sig = signatureVerifier.verify(apkFile)) {
            is PluginSignatureResult.Rejected ->
                return LoadPackageResult.Error(
                    apkPath = apkFile.absolutePath,
                    pluginId = manifest.id,
                    reason = formatSignatureReject(sig)
                )
            is PluginSignatureResult.Trusted -> Unit
        }

        if (manifest.minAppVersion.isNotBlank() && appVersionName.isNotBlank()) {
            // 宿主版本 < minAppVersion 则拒绝
            if (VersionComparator.compareVersion(appVersionName, manifest.minAppVersion) < 0) {
                return LoadPackageResult.Error(
                    apkPath = apkFile.absolutePath,
                    pluginId = manifest.id,
                    reason = "需要 App >= ${manifest.minAppVersion}，当前 $appVersionName"
                )
            }
        }

        val fromFactory = pluginFactory?.invoke(manifest, apkFile)
        if (fromFactory != null) {
            if (fromFactory.id != manifest.id) {
                return LoadPackageResult.Error(
                    apkPath = apkFile.absolutePath,
                    pluginId = manifest.id,
                    reason = "插件实例 id(${fromFactory.id}) 与清单 id(${manifest.id}) 不一致"
                )
            }
            return LoadPackageResult.Ok(
                LoadedPackage(
                    manifest = manifest,
                    apkFile = apkFile,
                    classLoader = null,
                    plugin = fromFactory,
                    signature = signatureVerifier.verify(apkFile).toInfo()
                )
            )
        }

        val cl = classLoaderFactory.create(apkFile, parentClassLoader)
            ?: return LoadPackageResult.Error(
                apkPath = apkFile.absolutePath,
                pluginId = manifest.id,
                reason = "无法创建 ClassLoader（当前环境可能不支持 dex 加载）"
            )

        val instantiated = DynamicPluginInstantiator.instantiate(cl, manifest.entryClass)
        val plugin = instantiated.getOrElse { e ->
            return LoadPackageResult.Error(
                apkPath = apkFile.absolutePath,
                pluginId = manifest.id,
                reason = "实例化失败: ${e.message ?: e::class.java.simpleName}"
            )
        }

        if (plugin.id != manifest.id) {
            return LoadPackageResult.Error(
                apkPath = apkFile.absolutePath,
                pluginId = manifest.id,
                reason = "插件实例 id(${plugin.id}) 与清单 id(${manifest.id}) 不一致"
            )
        }

        return LoadPackageResult.Ok(
            LoadedPackage(
                manifest = manifest,
                apkFile = apkFile,
                classLoader = cl,
                plugin = plugin,
                signature = signatureVerifier.verify(apkFile).toInfo()
            )
        )
    }

    data class LoadedPackage(
        val manifest: PluginManifest,
        val apkFile: File,
        val classLoader: ClassLoader?,
        val plugin: LanXinPlugin,
        val signature: PluginSignatureInfo = PluginSignatureInfo.unknown()
    )

    sealed class LoadPackageResult {
        data class Ok(val pkg: LoadedPackage) : LoadPackageResult()
        data class Error(
            val apkPath: String?,
            val pluginId: String?,
            val reason: String
        ) : LoadPackageResult()
    }

    companion object {
        fun formatSignatureReject(sig: PluginSignatureResult.Rejected): String {
            val policyPart = if (sig.policy.isNotBlank()) " [${sig.policy}]" else ""
            return "签名校验失败$policyPart: ${sig.reason}"
        }
    }
}

private fun PluginSignatureResult.toInfo(): PluginSignatureInfo = when (this) {
    is PluginSignatureResult.Trusted -> PluginSignatureInfo(
        status = PluginSignatureStatus.TRUSTED,
        policy = policy,
        certificateSha256 = certificateSha256,
        detail = null
    )
    is PluginSignatureResult.Rejected -> PluginSignatureInfo(
        status = PluginSignatureStatus.REJECTED,
        policy = policy,
        certificateSha256 = certificateSha256,
        detail = reason
    )
}
