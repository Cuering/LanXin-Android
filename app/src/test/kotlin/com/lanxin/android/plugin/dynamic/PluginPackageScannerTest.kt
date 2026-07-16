package com.lanxin.android.plugin.dynamic

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPackageScannerTest {

    @Test
    fun `scan empty or missing dir returns empty`() {
        val missing = File(System.getProperty("java.io.tmpdir"), "no-such-${System.nanoTime()}")
        assertTrue(PluginPackageScanner.scan(missing).isEmpty())

        val empty = File(System.getProperty("java.io.tmpdir"), "scan-empty-${System.nanoTime()}").also { it.mkdirs() }
        try {
            assertTrue(PluginPackageScanner.scan(empty).isEmpty())
        } finally {
            empty.deleteRecursively()
        }
    }

    @Test
    fun `scan lists apk only sorted`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "scan-apk-${System.nanoTime()}").also { it.mkdirs() }
        try {
            File(dir, "b.apk").writeText("x")
            File(dir, "a.apk").writeText("x")
            File(dir, "note.txt").writeText("x")
            File(dir, ".hide.apk").writeText("x")
            val names = PluginPackageScanner.scan(dir).map { it.name }
            assertEquals(listOf("a.apk", "b.apk"), names)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `scanWithManifests separates ok and failures`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "scan-mf-${System.nanoTime()}").also { it.mkdirs() }
        try {
            val good = File(dir, "good.apk")
            ZipOutputStream(good.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry(PluginPackagePaths.MANIFEST_ENTRY))
                zos.write(
                    """
                    {"id":"g.ok","name":"G","version":"1","entryClass":"com.g.P"}
                    """.trimIndent().toByteArray()
                )
                zos.closeEntry()
            }
            File(dir, "bad.apk").writeText("not-a-zip")

            val report = PluginPackageScanner.scanWithManifests(dir)
            assertEquals(1, report.packages.size)
            assertEquals("g.ok", report.packages[0].second.id)
            assertEquals(1, report.failures.size)
            assertTrue(report.failures[0].reason.contains("清单"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
