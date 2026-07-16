package com.lanxin.android.presentation.ui.plugin

import com.lanxin.android.plugin.PluginCatalog
import com.lanxin.android.plugin.dynamic.DynamicDiscoverResult
import com.lanxin.android.plugin.dynamic.PluginLoadResult
import com.lanxin.android.plugin.dynamic.PluginRecord
import com.lanxin.android.plugin.dynamic.PluginSource
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 5.4：插件管理 ViewModel 纯逻辑单测（Fake [PluginCatalog]）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PluginManagerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var catalog: FakePluginCatalog
    private lateinit var viewModel: PluginManagerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        catalog = FakePluginCatalog()
        catalog.seed(
            PluginRecord(
                id = "compiled.a",
                name = "Compiled A",
                version = "1.0.0",
                description = "builtin",
                source = PluginSource.COMPILED,
                enabled = true,
                removable = false
            ),
            PluginRecord(
                id = "dyn.b",
                name = "Dynamic B",
                version = "2.0.0",
                description = "apk",
                source = PluginSource.DYNAMIC,
                enabled = true,
                removable = true,
                apkPath = "/tmp/plugin-packages/b.apk",
                author = "tester"
            )
        )
        viewModel = PluginManagerViewModel(catalog)
        // init { refresh }
        dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads records from catalog`() = runTest(dispatcher) {
        val state = viewModel.uiState.value
        assertEquals(2, state.records.size)
        assertEquals("compiled.a", state.records[0].id)
        assertEquals(PluginSource.DYNAMIC, state.records.first { it.id == "dyn.b" }.source)
        assertFalse(state.isLoading)
    }

    @Test
    fun `setEnabled updates record and snackbar`() = runTest(dispatcher) {
        viewModel.setEnabled("dyn.b", false)
        advanceUntilIdle()

        val dyn = viewModel.uiState.value.records.first { it.id == "dyn.b" }
        assertFalse(dyn.enabled)
        assertTrue(viewModel.uiState.value.snackbarMessage!!.contains("已停用"))
        assertEquals(false, catalog.enabledMap["dyn.b"])
    }

    @Test
    fun `refresh with scan reports success count`() = runTest(dispatcher) {
        catalog.nextDiscover = DynamicDiscoverResult(
            successes = listOf(
                PluginLoadResult.Success(
                    record = catalog.getPluginRecords().first { it.id == "dyn.b" },
                    loaded = true
                )
            ),
            failures = listOf(
                PluginLoadResult.Failure(
                    apkPath = "/tmp/bad.apk",
                    pluginId = null,
                    reason = "坏包"
                )
            )
        )
        viewModel.refresh(scanDynamic = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.failures.size)
        assertEquals("坏包", state.failures[0].reason)
        assertTrue(state.snackbarMessage!!.contains("成功 1"))
        assertTrue(state.snackbarMessage!!.contains("失败 1"))
        assertEquals(1, catalog.discoverCount)
    }

    @Test
    fun `confirmUnload removes dynamic plugin`() = runTest(dispatcher) {
        viewModel.requestUnload("dyn.b")
        assertEquals("dyn.b", viewModel.uiState.value.unloadConfirmId)

        viewModel.confirmUnload()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.unloadConfirmId)
        assertEquals(1, state.records.size)
        assertEquals("compiled.a", state.records.single().id)
        assertTrue(state.snackbarMessage!!.contains("已卸载"))
        assertTrue(catalog.unloadedIds.contains("dyn.b"))
    }

    @Test
    fun `requestUnload rejects compiled plugin`() = runTest(dispatcher) {
        viewModel.requestUnload("compiled.a")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.unloadConfirmId)
        assertTrue(viewModel.uiState.value.snackbarMessage!!.contains("不可卸载"))
    }

    @Test
    fun `confirmDeleteApk unloads and deletes file when present`() = runTest(dispatcher) {
        val root = File(System.getProperty("java.io.tmpdir"), "pm-ui-${System.nanoTime()}")
        root.mkdirs()
        try {
            val apk = File(root, "b.apk")
            apk.writeText("fake")
            catalog.records["dyn.b"] = catalog.records["dyn.b"]!!.copy(apkPath = apk.absolutePath)

            viewModel.refresh(scanDynamic = false)
            advanceUntilIdle()

            viewModel.requestDeleteApk("dyn.b")
            assertEquals("dyn.b", viewModel.uiState.value.deleteApkConfirmId)
            viewModel.confirmDeleteApk()
            advanceUntilIdle()

            assertFalse(apk.exists())
            assertEquals(1, viewModel.uiState.value.records.size)
            assertTrue(viewModel.uiState.value.snackbarMessage!!.contains("删除"))
        } finally {
            root.deleteRecursively()
        }
    }

    private class FakePluginCatalog : PluginCatalog {
        val records = ConcurrentHashMap<String, PluginRecord>()
        val enabledMap = ConcurrentHashMap<String, Boolean>()
        val unloadedIds = mutableListOf<String>()
        var discoverCount = 0
        var nextDiscover = DynamicDiscoverResult()
        var lastFailures: List<PluginLoadResult.Failure> = emptyList()
        private val packages = File(
            System.getProperty("java.io.tmpdir"),
            "fake-plugin-packages"
        ).also { it.mkdirs() }

        fun seed(vararg items: PluginRecord) {
            records.clear()
            enabledMap.clear()
            items.forEach {
                records[it.id] = it
                enabledMap[it.id] = it.enabled
            }
        }

        override fun getPluginRecords(): List<PluginRecord> =
            records.values
                .map { it.copy(enabled = enabledMap[it.id] ?: it.enabled) }
                .sortedBy { it.id }

        override fun getLastDynamicFailures(): List<PluginLoadResult.Failure> = lastFailures

        override suspend fun setEnabled(pluginId: String, enabled: Boolean): Boolean {
            if (pluginId !in records) {
                enabledMap[pluginId] = enabled
                return false
            }
            enabledMap[pluginId] = enabled
            records[pluginId] = records[pluginId]!!.copy(enabled = enabled)
            return true
        }

        override suspend fun unloadPlugin(pluginId: String): Boolean {
            val rec = records[pluginId] ?: return false
            if (rec.source != PluginSource.DYNAMIC) return false
            records.remove(pluginId)
            unloadedIds += pluginId
            return true
        }

        override suspend fun discoverAndLoadDynamicPlugins(packagesDir: File?): DynamicDiscoverResult {
            discoverCount++
            lastFailures = nextDiscover.failures
            return nextDiscover
        }

        override fun packagesDirectory(): File = packages
    }
}