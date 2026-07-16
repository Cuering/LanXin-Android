package com.lanxin.android.plugin.dynamic

import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.PluginManager
import com.lanxin.android.plugin.ToolDef
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManagerDynamicTest {

    @Test
    fun `discover loads dynamic plugin and registers tools`() = runBlocking {
        val root = newFilesRoot()
        try {
            val packages = PluginPackagePaths.ensurePackagesDir(root)
            writeApk(File(packages, "hello.apk"), id = "dyn.hello")

            val manager = PluginManager(FakeContext(root))
            manager.configureDynamicLoading(appVersionName = "0.7.6")
            manager.testPluginFactory = { m, _ -> toolPlugin(m.id, "hello_tool") }

            val result = manager.discoverAndLoadDynamicPlugins(packages)
            assertEquals(1, result.successes.size)
            assertEquals(0, result.failures.size)
            assertTrue(result.successes[0].loaded)
            assertEquals("dyn.hello", manager.getPlugins().single().id)

            val tools = manager.getTools().map { it.name }
            assertTrue(tools.contains("hello_tool"))

            val call = manager.callTool("hello_tool", buildJsonObject { put("x", 1) })
            assertEquals("dyn.hello", call["from"]?.jsonPrimitive?.contentOrNull)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `conflict with compiled plugin fails without crash`() = runBlocking {
        val root = newFilesRoot()
        try {
            val packages = PluginPackagePaths.ensurePackagesDir(root)
            writeApk(File(packages, "mem.apk"), id = "lanxin.memory")

            val manager = PluginManager(FakeContext(root))
            manager.register(toolPlugin("lanxin.memory", "mem_tool"))
            manager.loadAll()
            manager.testPluginFactory = { m, _ -> toolPlugin(m.id, "dyn_tool") }

            val result = manager.discoverAndLoadDynamicPlugins(packages)
            assertEquals(0, result.successes.size)
            assertEquals(1, result.failures.size)
            assertTrue(result.failures[0].reason.contains("冲突"))
            // 编译期插件仍在
            assertEquals(1, manager.getPlugins().size)
            assertTrue(manager.getTools().any { it.name == "mem_tool" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `setEnabled disable removes tools and enable restores`() = runBlocking {
        val root = newFilesRoot()
        try {
            val packages = PluginPackagePaths.ensurePackagesDir(root)
            writeApk(File(packages, "t.apk"), id = "dyn.toggle")

            val manager = PluginManager(FakeContext(root))
            manager.testPluginFactory = { m, _ -> toolPlugin(m.id, "toggle_tool") }
            manager.discoverAndLoadDynamicPlugins(packages)
            assertTrue(manager.getTools().any { it.name == "toggle_tool" })

            manager.setEnabled("dyn.toggle", false)
            assertFalse(manager.isEnabled("dyn.toggle"))
            assertFalse(manager.getTools().any { it.name == "toggle_tool" })

            manager.setEnabled("dyn.toggle", true)
            assertTrue(manager.isEnabled("dyn.toggle"))
            assertTrue(manager.getTools().any { it.name == "toggle_tool" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `unloadPlugin removes dynamic but not compiled`() = runBlocking {
        val root = newFilesRoot()
        try {
            val packages = PluginPackagePaths.ensurePackagesDir(root)
            writeApk(File(packages, "u.apk"), id = "dyn.unload")

            val manager = PluginManager(FakeContext(root))
            manager.register(toolPlugin("compiled.one", "c_tool"))
            manager.loadAll()
            manager.testPluginFactory = { m, _ -> toolPlugin(m.id, "u_tool") }
            manager.discoverAndLoadDynamicPlugins(packages)

            assertFalse(manager.unloadPlugin("compiled.one"))
            assertTrue(manager.unloadPlugin("dyn.unload"))
            assertEquals(1, manager.getPlugins().size)
            assertEquals("compiled.one", manager.getPlugins().single().id)
            assertFalse(manager.getTools().any { it.name == "u_tool" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `bad packages become failures and do not crash discover`() = runBlocking {
        val root = newFilesRoot()
        try {
            val packages = PluginPackagePaths.ensurePackagesDir(root)
            File(packages, "broken.apk").writeText("nope")
            writeApk(File(packages, "ok.apk"), id = "dyn.ok")

            val manager = PluginManager(FakeContext(root))
            manager.testPluginFactory = { m, _ -> toolPlugin(m.id, "ok_tool") }
            val result = manager.discoverAndLoadDynamicPlugins(packages)
            assertEquals(1, result.successes.size)
            assertEquals(1, result.failures.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `getPluginRecords marks source`() = runBlocking {
        val root = newFilesRoot()
        try {
            val packages = PluginPackagePaths.ensurePackagesDir(root)
            writeApk(File(packages, "r.apk"), id = "dyn.rec")

            val manager = PluginManager(FakeContext(root))
            manager.register(toolPlugin("compiled.two", "c2"))
            manager.loadAll()
            manager.testPluginFactory = { m, _ -> toolPlugin(m.id, "r_tool") }
            manager.discoverAndLoadDynamicPlugins(packages)

            val records = manager.getPluginRecords()
            assertEquals(2, records.size)
            val dyn = records.first { it.id == "dyn.rec" }
            val comp = records.first { it.id == "compiled.two" }
            assertEquals(PluginSource.DYNAMIC, dyn.source)
            assertTrue(dyn.removable)
            assertEquals(PluginSource.COMPILED, comp.source)
            assertFalse(comp.removable)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun newFilesRoot(): File =
        File(System.getProperty("java.io.tmpdir"), "pm-dyn-${System.nanoTime()}").also { it.mkdirs() }

    private fun writeApk(file: File, id: String) {
        val json =
            """{"id":"$id","name":"N","version":"1.0.0","entryClass":"com.example.P"}"""
        ZipOutputStream(file.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry(PluginPackagePaths.MANIFEST_ENTRY))
            zos.write(json.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    private fun toolPlugin(id: String, toolName: String): LanXinPlugin = object : LanXinPlugin {
        override val id = id
        override val name = "P-$id"
        override val version = "1.0.0"
        override val description = "test"
        override suspend fun onLoad(context: PluginContext) {
            context.registerTool(
                ToolDef(
                    name = toolName,
                    description = "t",
                    handler = {
                        buildJsonObject { put("from", id) }
                    }
                )
            )
        }
    }
}

private class FakeContext(private val files: File) : android.content.ContextWrapper(null) {
    override fun getApplicationContext(): android.content.Context = this
    override fun getFilesDir(): File = files.also { it.mkdirs() }
    override fun getPackageName(): String = "com.lanxin.android"
    override fun getPackageManager(): android.content.pm.PackageManager {
        throw UnsupportedOperationException("not needed when appVersion injected")
    }
}
