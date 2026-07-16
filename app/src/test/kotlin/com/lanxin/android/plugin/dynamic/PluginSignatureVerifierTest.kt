package com.lanxin.android.plugin.dynamic

import java.io.File
import org.junit.Assert.assertEquals
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
            assertEquals(
                SignaturePolicy.ALLOW_ALL.wireName,
                (r as PluginSignatureResult.Trusted).policy
            )
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
            assertEquals(
                SignaturePolicy.DENY_ALL.wireName,
                (r as PluginSignatureResult.Rejected).policy
            )
        } finally {
            f.delete()
        }
    }

    @Test
    fun `allowlist matches normalized digest`() {
        val f = File(System.getProperty("java.io.tmpdir"), "sig-${System.nanoTime()}.apk").also {
            it.writeText("x")
        }
        try {
            val verifier = ConfigurablePluginSignatureVerifier(
                policyProvider = { SignaturePolicy.ALLOWLIST },
                allowlistProvider = { setOf("AA:BB:CC") },
                certProvider = FixedApkCertDigestProvider(listOf("aabbcc"))
            )
            val r = verifier.verify(f)
            assertTrue(r is PluginSignatureResult.Trusted)
            assertEquals(
                SignaturePolicy.ALLOWLIST.wireName,
                (r as PluginSignatureResult.Trusted).policy
            )
        } finally {
            f.delete()
        }
    }

    @Test
    fun `allowlist rejects unknown cert`() {
        val f = File(System.getProperty("java.io.tmpdir"), "sig-${System.nanoTime()}.apk").also {
            it.writeText("x")
        }
        try {
            val verifier = ConfigurablePluginSignatureVerifier(
                policyProvider = { SignaturePolicy.ALLOWLIST },
                allowlistProvider = { setOf("deadbeef") },
                certProvider = FixedApkCertDigestProvider(listOf("cafebabe"))
            )
            val r = verifier.verify(f)
            assertTrue(r is PluginSignatureResult.Rejected)
            val rej = r as PluginSignatureResult.Rejected
            assertTrue(rej.reason.contains("白名单"))
            assertEquals(listOf("cafebabe"), rej.certificateSha256)
        } finally {
            f.delete()
        }
    }

    @Test
    fun `allowlist empty fails closed`() {
        val f = File(System.getProperty("java.io.tmpdir"), "sig-${System.nanoTime()}.apk").also {
            it.writeText("x")
        }
        try {
            val verifier = ConfigurablePluginSignatureVerifier(
                policyProvider = { SignaturePolicy.ALLOWLIST },
                allowlistProvider = { emptySet() },
                certProvider = FixedApkCertDigestProvider(listOf("aabbcc"))
            )
            val r = verifier.verify(f)
            assertTrue(r is PluginSignatureResult.Rejected)
            assertTrue((r as PluginSignatureResult.Rejected).reason.contains("白名单为空"))
        } finally {
            f.delete()
        }
    }

    @Test
    fun `allowlist fails when cert extract fails`() {
        val f = File(System.getProperty("java.io.tmpdir"), "sig-${System.nanoTime()}.apk").also {
            it.writeText("x")
        }
        try {
            val verifier = ConfigurablePluginSignatureVerifier(
                policyProvider = { SignaturePolicy.ALLOWLIST },
                allowlistProvider = { setOf("aabbcc") },
                certProvider = FailingApkCertDigestProvider("boom")
            )
            val r = verifier.verify(f)
            assertTrue(r is PluginSignatureResult.Rejected)
            assertTrue((r as PluginSignatureResult.Rejected).reason.contains("无法读取"))
        } finally {
            f.delete()
        }
    }

    @Test
    fun `policy switch allow to deny`() {
        var policy = SignaturePolicy.ALLOW_ALL
        val f = File(System.getProperty("java.io.tmpdir"), "sig-${System.nanoTime()}.apk").also {
            it.writeText("x")
        }
        try {
            val verifier = ConfigurablePluginSignatureVerifier(
                policyProvider = { policy },
                allowlistProvider = { emptySet() }
            )
            assertTrue(verifier.verify(f) is PluginSignatureResult.Trusted)
            policy = SignaturePolicy.DENY_ALL
            assertTrue(verifier.verify(f) is PluginSignatureResult.Rejected)
        } finally {
            f.delete()
        }
    }

    @Test
    fun `cert digest normalize strips colons and case`() {
        assertEquals("aabbcc", CertDigestUtils.normalize("AA:BB:CC"))
        assertEquals(setOf("aabb", "ccdd"), CertDigestUtils.parseAllowlist("AA:BB, ccdd\n"))
    }
}
