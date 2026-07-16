package com.lanxin.android.plugin.dynamic

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPackagePathsTest {

    @Test
    fun `packagesDir is under filesDir plugin-packages`() {
        val root = File("/tmp/lanxin-files")
        val dir = PluginPackagePaths.packagesDir(root)
        assertEquals(File(root, "plugin-packages"), dir)
        assertEquals("plugin-packages", PluginPackagePaths.PACKAGES_DIR_NAME)
    }

    @Test
    fun `stateFile is plugin-state json`() {
        val root = File("/tmp/lanxin-files")
        assertEquals(File(root, "plugin-state.json"), PluginPackagePaths.stateFile(root))
    }

    @Test
    fun `isPluginApk filters by extension and hidden`() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "pp-apk-${System.nanoTime()}").also { it.mkdirs() }
        try {
            val apk = File(tmp, "demo.apk").also { it.writeText("x") }
            val hidden = File(tmp, ".secret.apk").also { it.writeText("x") }
            val txt = File(tmp, "demo.txt").also { it.writeText("x") }
            assertTrue(PluginPackagePaths.isPluginApk(apk))
            assertFalse(PluginPackagePaths.isPluginApk(hidden))
            assertFalse(PluginPackagePaths.isPluginApk(txt))
            assertFalse(PluginPackagePaths.isPluginApk(tmp))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `ensurePackagesDir creates directory`() {
        val root = File(System.getProperty("java.io.tmpdir"), "pp-root-${System.nanoTime()}")
        try {
            val dir = PluginPackagePaths.ensurePackagesDir(root)
            assertTrue(dir.isDirectory)
            assertEquals("plugin-packages", dir.name)
        } finally {
            root.deleteRecursively()
        }
    }
}
