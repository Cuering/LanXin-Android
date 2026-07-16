package com.lanxin.android.presentation.ui.plugin

import com.lanxin.android.plugin.PluginCatalog
import com.lanxin.android.plugin.dynamic.DynamicDiscoverResult
import com.lanxin.android.plugin.dynamic.PluginLoadResult
import com.lanxin.android.plugin.dynamic.PluginRecord
import com.lanxin.android.plugin.dynamic.PluginSource
import com.lanxin.android.plugin.market.InstallPhase
import com.lanxin.android.plugin.market.InstallProgress
import com.lanxin.android.plugin.market.MarketDefaults
import com.lanxin.android.plugin.market.MarketInstallStatus
import com.lanxin.android.plugin.market.MarketPluginEntry
import com.lanxin.android.plugin.market.MarketSettings
import com.lanxin.android.plugin.market.PluginInstallResult
import com.lanxin.android.plugin.market.PluginInstaller
import com.lanxin.android.plugin.market.PluginMarketRepository
import com.lanxin.android.plugin.market.SamplePluginMarketRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginMarketViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PluginMarketViewModel
    private lateinit var installer: RecordingInstaller
    private lateinit var settings: FakeMarketSettings
    private lateinit var catalog: FakeCatalog

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        installer = RecordingInstaller()
        settings = FakeMarketSettings()
        catalog = FakeCatalog()
        viewModel = PluginMarketViewModel(
            marketRepository = SamplePluginMarketRepository(),
            installer = installer,
            catalog = catalog,
            marketSettings = settings
        )
        dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads sample items`() = runTest(dispatcher) {
        val state = viewModel.uiState.value
        assertEquals(3, state.items.size)
        assertEquals(MarketDefaults.DEFAULT_CATALOG_URL, state.catalogUrl)
        assertTrue(state.items.any { it.entry.id == "example.hello" })
    }

    @Test
    fun `query filters items`() = runTest(dispatcher) {
        viewModel.onQueryChange("notes")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals("example.notes", viewModel.uiState.value.items.single().entry.id)
    }

    @Test
    fun `install success updates snackbar and status`() = runTest(dispatcher) {
        catalog.records["example.hello"] = PluginRecord(
            id = "example.hello",
            name = "Hello",
            version = "1.0.0",
            description = "",
            source = PluginSource.DYNAMIC,
            enabled = true,
            removable = true
        )
        val entry = viewModel.uiState.value.items.first { it.entry.id == "example.hello" }.entry
        viewModel.install(entry)
        advanceUntilIdle()
        assertEquals(1, installer.calls)
        assertTrue(viewModel.uiState.value.snackbarMessage!!.contains("已安装"))
        val item = viewModel.uiState.value.items.first { it.entry.id == "example.hello" }
        assertEquals(MarketInstallStatus.INSTALLED, item.installStatus)
    }

    @Test
    fun `save catalog url persists`() = runTest(dispatcher) {
        viewModel.openUrlDialog()
        viewModel.onDraftUrlChange("https://example.invalid/custom.json")
        viewModel.saveCatalogUrl()
        advanceUntilIdle()
        assertEquals("https://example.invalid/custom.json", settings.getCatalogUrl())
        assertEquals("https://example.invalid/custom.json", viewModel.uiState.value.catalogUrl)
    }

    private class RecordingInstaller : PluginInstaller {
        var calls = 0
        override suspend fun install(
            entry: MarketPluginEntry,
            onProgress: (InstallProgress) -> Unit
        ): PluginInstallResult {
            calls++
            onProgress(InstallProgress(InstallPhase.DOWNLOADING, 0.5f))
            onProgress(InstallProgress(InstallPhase.DONE, 1f, "ok"))
            return PluginInstallResult.Success(
                pluginId = entry.id,
                apkPath = "/tmp/${entry.id}.apk",
                loaded = true,
                message = "已安装 ${entry.id}"
            )
        }
    }

    private class FakeMarketSettings : MarketSettings {
        private var url: String = MarketDefaults.DEFAULT_CATALOG_URL
        override suspend fun getCatalogUrl(): String = url
        override suspend fun setCatalogUrl(url: String?) {
            this.url = if (url.isNullOrBlank()) MarketDefaults.DEFAULT_CATALOG_URL else url.trim()
        }
    }

    private class FakeCatalog : PluginCatalog {
        val records = linkedMapOf<String, PluginRecord>()
        override fun getPluginRecords(): List<PluginRecord> = records.values.toList()
        override fun getLastDynamicFailures() = emptyList<PluginLoadResult.Failure>()
        override suspend fun setEnabled(pluginId: String, enabled: Boolean) = false
        override suspend fun unloadPlugin(pluginId: String) = false
        override suspend fun discoverAndLoadDynamicPlugins(packagesDir: File?) =
            DynamicDiscoverResult()
        override suspend fun loadDynamicPlugin(apkFile: File): PluginLoadResult =
            PluginLoadResult.Failure(apkFile.path, null, "unused")
        override fun packagesDirectory(): File =
            File(System.getProperty("java.io.tmpdir"), "fake-market-packages")
    }
}
