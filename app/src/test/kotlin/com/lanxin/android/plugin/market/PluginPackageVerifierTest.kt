package com.lanxin.android.plugin.market

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPackageVerifierTest {

    @Test
    fun `verify passes when no expectations`() {
        val f = File.createTempFile("pkg", ".bin")
        try {
            f.writeBytes(byteArrayOf(1, 2, 3))
            assertTrue(PluginPackageVerifier.verify(f).isSuccess)
        } finally {
            f.delete()
        }
    }

    @Test
    fun `size mismatch fails`() {
        val f = File.createTempFile("pkg", ".bin")
        try {
            f.writeBytes(byteArrayOf(1, 2, 3))
            val r = PluginPackageVerifier.verify(f, expectedSize = 99L)
            assertTrue(r.isFailure)
            assertTrue(r.exceptionOrNull()!!.message!!.contains("大小"))
        } finally {
            f.delete()
        }
    }

    @Test
    fun `sha256 mismatch fails`() {
        val f = File.createTempFile("pkg", ".bin")
        try {
            f.writeBytes(byteArrayOf(1, 2, 3))
            val r = PluginPackageVerifier.verify(f, expectedSha256 = "00")
            assertTrue(r.isFailure)
            assertTrue(r.exceptionOrNull()!!.message!!.contains("SHA-256"))
        } finally {
            f.delete()
        }
    }

    @Test
    fun `sha256 match passes`() {
        val f = File.createTempFile("pkg", ".bin")
        try {
            f.writeBytes(byteArrayOf(1, 2, 3))
            val hex = PluginPackageVerifier.sha256Hex(f)
            assertTrue(PluginPackageVerifier.verify(f, expectedSize = 3L, expectedSha256 = hex).isSuccess)
        } finally {
            f.delete()
        }
    }
}
