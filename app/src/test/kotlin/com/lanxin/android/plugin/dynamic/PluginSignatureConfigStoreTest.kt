package com.lanxin.android.plugin.dynamic

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSignatureConfigStoreTest {

    @Test
    fun `default policy when file missing`() {
        val root = File(System.getProperty("java.io.tmpdir"), "sig-cfg-${System.nanoTime()}")
        try {
            root.mkdirs()
            val store = PluginSignatureConfigStore(
                file = File(root, "plugin-signature.json"),
                defaultPolicy = SignaturePolicy.ALLOW_ALL
            )
            assertEquals(SignaturePolicy.ALLOW_ALL, store.load().policy)
            assertTrue(store.load().allowlist.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `save and reload allowlist`() {
        val root = File(System.getProperty("java.io.tmpdir"), "sig-cfg-${System.nanoTime()}")
        try {
            root.mkdirs()
            val file = File(root, "plugin-signature.json")
            val store = PluginSignatureConfigStore(
                file = file,
                defaultPolicy = SignaturePolicy.DENY_ALL
            )
            store.save(
                PluginSignatureConfig(
                    policy = SignaturePolicy.ALLOWLIST,
                    allowlist = setOf("AA:BB", "ccdd")
                )
            )
            assertTrue(file.isFile)

            val reloaded = PluginSignatureConfigStore(
                file = file,
                defaultPolicy = SignaturePolicy.ALLOW_ALL
            )
            val cfg = reloaded.load()
            assertEquals(SignaturePolicy.ALLOWLIST, cfg.policy)
            assertEquals(setOf("aabb", "ccdd"), cfg.allowlist)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `setPolicy and setAllowlist helpers`() {
        val root = File(System.getProperty("java.io.tmpdir"), "sig-cfg-${System.nanoTime()}")
        try {
            root.mkdirs()
            val store = PluginSignatureConfigStore(
                file = File(root, "plugin-signature.json"),
                defaultPolicy = SignaturePolicy.ALLOW_ALL
            )
            store.setPolicy(SignaturePolicy.DENY_ALL)
            store.setAllowlist(listOf("deadbeef"))
            val cfg = store.load()
            assertEquals(SignaturePolicy.DENY_ALL, cfg.policy)
            assertEquals(setOf("deadbeef"), cfg.allowlist)
        } finally {
            root.deleteRecursively()
        }
    }
}
