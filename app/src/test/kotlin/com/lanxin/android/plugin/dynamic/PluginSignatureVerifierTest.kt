package com.lanxin.android.plugin.dynamic

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSignatureVerifierTest {

    @Test
    fun `allow all returns trusted`() {
        val f = File(System.getProperty("java.io.tmpdir"), "sig-${System.nanoTime()}.apk").also {
            it.writeText("x")
        }
        try {
            val r = AllowAllPluginSignatureVerifier.verify(f)
            assertTrue(r is PluginSignatureResult.Trusted)
        } finally {
            f.delete()
        }
    }

    @Test
    fun `reject all returns rejected`() {
        val f = File(System.getProperty("java.io.tmpdir"), "sig-${System.nanoTime()}.apk").also {
            it.writeText("x")
        }
        try {
            val r = RejectAllPluginSignatureVerifier.verify(f)
            assertTrue(r is PluginSignatureResult.Rejected)
        } finally {
            f.delete()
        }
    }
}
