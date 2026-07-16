package com.lanxin.android.plugin.dynamic

import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicPluginLoaderTest {

    @Test
    fun `loadPackage fails on missing file without crash`() {
        val loader = DynamicPluginLoader(pluginFactory = { _, _ -> null })
        val result = loader.loadPackage(File("/tmp/no-such-${System.nanoTime()}.apk"))
        assertTrue(result is DynamicPluginLoader.LoadPackageResult.Error)
    }

    @Test
    fun `loadPackage fails when signature rejected`() {
        val apk = writeMinimalApk()
        try {
            val loader = DynamicPluginLoader(
                signatureVerifier = RejectAllPluginSignatureVerifier,
                pluginFactory = { m, _ -> fakePlugin(m.id) }
            )
            val result = loader.loadPackage(apk)
            assertTrue(result is DynamicPluginLoader.LoadPackageResult.Error)
            val err = result as DynamicPluginLoader.LoadPackageResult.Error
            assertTrue(err.reason.contains("签名"))
            assertEquals("demo.plugin", err.pluginId)
        } finally {
            apk.delete()
        }
    }

    @Test
    fun `loadPackage succeeds with pluginFactory`() {
        val apk = writeMinimalApk()
        try {
            val loader = DynamicPluginLoader(
                pluginFactory = { m, _ -> fakePlugin(m.id) }
            )
            val result = loader.loadPackage(apk)
            assertTrue(result is DynamicPluginLoader.LoadPackageResult.Ok)
            val ok = result as DynamicPluginLoader.LoadPackageResult.Ok
            assertEquals("demo.plugin", ok.pkg.plugin.id)
            assertEquals("demo.plugin", ok.pkg.manifest.id)
        } finally {
            apk.delete()
        }
    }

    @Test
    fun `loadPackage rejects minAppVersion`() {
        val apk = writeMinimalApk(minApp = "9.0.0")
        try {
            val loader = DynamicPluginLoader(
                appVersionName = "0.7.6",
                pluginFactory = { m, _ -> fakePlugin(m.id) }
            )
            val result = loader.loadPackage(apk)
            assertTrue(result is DynamicPluginLoader.LoadPackageResult.Error)
            assertTrue(
                (result as DynamicPluginLoader.LoadPackageResult.Error).reason.contains("需要 App")
            )
        } finally {
            apk.delete()
        }
    }

    @Test
    fun `loadPackage rejects id mismatch`() {
        val apk = writeMinimalApk()
        try {
            val loader = DynamicPluginLoader(
                pluginFactory = { _, _ -> fakePlugin("other.id") }
            )
            val result = loader.loadPackage(apk)
            assertTrue(result is DynamicPluginLoader.LoadPackageResult.Error)
            assertTrue(
                (result as DynamicPluginLoader.LoadPackageResult.Error).reason.contains("不一致")
            )
        } finally {
            apk.delete()
        }
    }

    private fun writeMinimalApk(minApp: String = ""): File {
        val apk = File(System.getProperty("java.io.tmpdir"), "dyn-${System.nanoTime()}.apk")
        val minField = if (minApp.isNotBlank()) ",\"minAppVersion\":\"$minApp\"" else ""
        val json =
            """{"id":"demo.plugin","name":"Demo","version":"1.0.0","entryClass":"com.demo.P"$minField}"""
        ZipOutputStream(apk.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry(PluginPackagePaths.MANIFEST_ENTRY))
            zos.write(json.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return apk
    }

    private fun fakePlugin(id: String): LanXinPlugin = object : LanXinPlugin {
        override val id = id
        override val name = "Demo"
        override val version = "1.0.0"
        override val description = ""
        override suspend fun onLoad(context: PluginContext) = Unit
    }
}
