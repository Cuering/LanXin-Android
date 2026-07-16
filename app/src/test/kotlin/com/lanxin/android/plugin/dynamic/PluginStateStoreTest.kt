package com.lanxin.android.plugin.dynamic

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginStateStoreTest {

    @Test
    fun `default enabled when unknown`() {
        val file = File(System.getProperty("java.io.tmpdir"), "ps-${System.nanoTime()}.json")
        try {
            val store = PluginStateStore(file)
            assertTrue(store.isEnabled("any.plugin"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `setEnabled persists and reloads`() {
        val file = File(System.getProperty("java.io.tmpdir"), "ps-${System.nanoTime()}.json")
        try {
            val store = PluginStateStore(file)
            store.setEnabled("lanxin.demo", false)
            assertFalse(store.isEnabled("lanxin.demo"))
            assertTrue(file.isFile)

            val reloaded = PluginStateStore(file)
            assertFalse(reloaded.isEnabled("lanxin.demo"))
            assertTrue(reloaded.isEnabled("other"))
            assertEquals(mapOf("lanxin.demo" to false), reloaded.snapshot())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `corrupt file does not crash and defaults enabled`() {
        val file = File(System.getProperty("java.io.tmpdir"), "ps-bad-${System.nanoTime()}.json")
        try {
            file.writeText("{not valid json!!")
            val store = PluginStateStore(file)
            assertTrue(store.isEnabled("x"))
        } finally {
            file.delete()
        }
    }
}
