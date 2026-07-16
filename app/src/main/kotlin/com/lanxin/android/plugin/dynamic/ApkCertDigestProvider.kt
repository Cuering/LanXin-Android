package com.lanxin.android.plugin.dynamic

import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.jar.JarFile

/**
 * 从 APK 提取签名证书 SHA-256 摘要（小写 hex，无冒号）。
 *
 * 与 Android 依赖解耦：默认实现走 JVM [JarFile]（V1/JAR 签名可读）；
 * 单测可注入固定摘要。
 */
fun interface ApkCertDigestProvider {
    fun digests(apkFile: File): Result<List<String>>
}

/** 固定摘要（单测 / 假 APK）。 */
class FixedApkCertDigestProvider(
    private val digests: List<String>
) : ApkCertDigestProvider {
    override fun digests(apkFile: File): Result<List<String>> = Result.success(digests)
}

/** 总是失败（单测）。 */
class FailingApkCertDigestProvider(
    private val message: String = "extract failed"
) : ApkCertDigestProvider {
    override fun digests(apkFile: File): Result<List<String>> =
        Result.failure(IllegalStateException(message))
}

/**
 * 通过 [JarFile] 读取 entry 证书并计算 SHA-256。
 *
 * 说明：仅覆盖 JAR/V1 签名路径；纯 V2/V3 且无 V1 的包可能得到空列表，
 * allowlist 策略下会拒绝（失败关闭）。真机后续可换 PackageManager 实现。
 */
class JarApkCertDigestProvider : ApkCertDigestProvider {

    override fun digests(apkFile: File): Result<List<String>> = runCatching {
        if (!apkFile.isFile) {
            error("not a file: ${apkFile.path}")
        }
        val found = linkedSetOf<String>()
        JarFile(apkFile, true).use { jar ->
            val buffer = ByteArray(8192)
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                jar.getInputStream(entry).use { input ->
                    while (input.read(buffer) != -1) {
                        // drain so JAR signature verification populates certs
                    }
                }
                entry.codeSigners?.forEach { signer ->
                    signer.signerCertPath?.certificates?.forEach { cert ->
                        if (cert is X509Certificate) {
                            found += CertDigestUtils.sha256Hex(cert.encoded)
                        }
                    }
                }
                @Suppress("DEPRECATION")
                entry.certificates?.forEach { cert ->
                    if (cert is X509Certificate) {
                        found += CertDigestUtils.sha256Hex(cert.encoded)
                    }
                }
            }
        }
        found.toList()
    }
}

/** 证书摘要规范化与解析。 */
object CertDigestUtils {

    fun normalize(raw: String): String =
        raw.trim()
            .lowercase()
            .replace(":", "")
            .replace(" ", "")
            .replace("\t", "")

    fun parseAllowlist(raw: String): Set<String> =
        raw.split(',', '\n', ';', '\r')
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .toSet()

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { b -> "%02x".format(b) }
    }
}
