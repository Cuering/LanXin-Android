package com.lanxin.android.plugin.market

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * 下载包校验（size + 可选 sha256）。
 *
 * 5.6 签名校验走 [com.lanxin.android.plugin.dynamic.PluginSignatureVerifier]；
 * 本类仅负责市场侧文件完整性钩子。
 */
object PluginPackageVerifier {

    fun verify(
        file: File,
        expectedSize: Long = 0L,
        expectedSha256: String = ""
    ): Result<Unit> {
        if (!file.isFile) {
            return Result.failure(IllegalStateException("文件不存在: ${file.absolutePath}"))
        }
        val actualSize = file.length()
        if (expectedSize > 0L && actualSize != expectedSize) {
            return Result.failure(
                IllegalStateException(
                    "大小不匹配：期望 $expectedSize，实际 $actualSize"
                )
            )
        }
        val want = expectedSha256.trim().lowercase()
        if (want.isNotEmpty()) {
            val actual = sha256Hex(file)
            if (actual != want) {
                return Result.failure(
                    IllegalStateException("SHA-256 不匹配：期望 $want，实际 $actual")
                )
            }
        }
        return Result.success(Unit)
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { b ->
            ((b.toInt() and 0xff) + 0x100).toString(16).substring(1)
        }
    }
}
