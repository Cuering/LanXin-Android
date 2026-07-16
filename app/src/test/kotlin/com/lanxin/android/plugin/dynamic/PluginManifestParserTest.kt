package com.lanxin.android.plugin.dynamic

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManifestParserTest {

    @Test
    fun `parseJson reads required fields`() {
        val raw = """
            {
              "id": "example.hello",
              "name": "Hello",
              "version": "1.2.3",
              "description": "demo",
              "entryClass": "com.example.HelloPlugin",
              "author": "alice",
              "minAppVersion": "0.7.0",
              "removable": true
            }
        """.trimIndent()
        val m = PluginManifestParser.parseJson(raw)!!
        assertEquals("example.hello", m.id)
        assertEquals("Hello", m.name)
        assertEquals("1.2.3", m.version)
        assertEquals("com.example.HelloPlugin", m.entryClass)
        assertEquals("alice", m.author)
        assertEquals("0.7.0", m.minAppVersion)
        assertTrue(m.removable)
    }

    @Test
    fun `parseJson rejects missing entryClass`() {
        val raw = """{"id":"a","name":"n","version":"1"}"""
        assertNull(PluginManifestParser.parseJson(raw))
    }

    @Test
    fun `parseJson rejects unsafe id`() {
        val raw = """
            {
              "id": "bad id with spaces",
              "name": "n",
              "version": "1",
              "entryClass": "com.x.Y"
            }
        """.trimIndent()
        assertNull(PluginManifestParser.parseJson(raw))
    }

    @Test
    fun `parseJson rejects blank`() {
        assertNull(PluginManifestParser.parseJson(""))
        assertNull(PluginManifestParser.parseJson("{not-json"))
    }

    @Test
    fun `isSafeClassName requires package`() {
        assertTrue(PluginManifestParser.isSafeClassName("com.example.HelloPlugin"))
        assertFalse(PluginManifestParser.isSafeClassName("HelloPlugin"))
        assertFalse(PluginManifestParser.isSafeClassName(""))
    }

    @Test
    fun `parseFromApk reads zip assets entry`() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "manifest-apk-${System.nanoTime()}.apk")
        try {
            writeApkWithManifest(
                tmp,
                """
                {
                  "id": "zip.plugin",
                  "name": "Zip",
                  "version": "0.1.0",
                  "entryClass": "com.zip.Plugin"
                }
                """.trimIndent()
            )
            val m = PluginManifestParser.parseFromApk(tmp)
            assertNotNull(m)
            assertEquals("zip.plugin", m!!.id)
            assertEquals("com.zip.Plugin", m.entryClass)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `parseFromApk missing manifest returns null`() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "empty-apk-${System.nanoTime()}.apk")
        try {
            ZipOutputStream(tmp.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write(byteArrayOf(1, 2, 3))
                zos.closeEntry()
            }
            assertNull(PluginManifestParser.parseFromApk(tmp))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `toMetadata maps fields`() {
        val m = PluginManifest(
            id = "x",
            name = "X",
            version = "1",
            description = "d",
            entryClass = "com.x.P",
            author = "a",
            minAppVersion = "0.1"
        )
        val meta = m.toMetadata()
        assertEquals("x", meta.id)
        assertEquals("0.1", meta.minAppVersion)
        assertTrue(meta.removable)
    }

    private fun writeApkWithManifest(file: File, json: String) {
        ZipOutputStream(file.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry(PluginPackagePaths.MANIFEST_ENTRY))
            zos.write(json.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }
}
