package com.lanxin.android.plugin.market

import com.lanxin.android.plugin.PluginCatalog
import com.lanxin.android.plugin.dynamic.DynamicDiscoverResult
import com.lanxin.android.plugin.dynamic.PluginLoadResult
import com.lanxin.android.plugin.dynamic.PluginRecord
import com.lanxin.android.plugin.dynamic.PluginSource
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPluginInstallerTest {

    @Test
    fun `install downloads verifies and loads`() = runBlocking {
        val root = File(System.getProperty("java.io.tmpdir"), "mkt-inst-${System.nanoTime()}")
        root.mkdirs()
        try {
            val packages = File(root, "plugin-packages").also { it.mkdirs() }
            val payload = byteArrayOf(9, 8, 7, 6)
            val fetcher = object : MarketHttpFetcher {
                override suspend fun getText(url: String): Result<String> =
                    Result.failure(UnsupportedOperationException("not used"))
                override suspend fun downloadToFile(
                    url: String,
                    destFile: File,
                    onProgress: (Float) -> Unit
                ): Result<Long> {
                    destFile.parentFile?.mkdirs()
                    destFile.writeBytes(payload)
                    onProgress(1f)
                    return Result.success(payload.size.toLong())
                }
            }
            val catalog = object : PluginCatalog {
                var loaded: File? = null
                override fun getPluginRecords(): List<PluginRecord> = emptyList()
                override fun getLastDynamicFailures() = emptyList<PluginLoadResult.Failure>()
                override suspend fun setEnabled(pluginId: String, enabled: Boolean) = false
                override suspend fun unloadPlugin(pluginId: String) = false
                override suspend fun discoverAndLoadDynamicPlugins(packagesDir: File?) =
                    DynamicDiscoverResult()
                override suspend fun loadDynamicPlugin(apkFile: File): PluginLoadResult {
                    loaded = apkFile
                    return PluginLoadResult.Success(
                        record = PluginRecord(
                            id = "example.hello",
                            name = "Hello",
                            version = "1.0.0",
                            description = "",
                            source = PluginSource.DYNAMIC,
                            enabled = true,
                            removable = true,
                            apkPath = apkFile.absolutePath
                        ),
                        loaded = true
                    )
                }
                override fun packagesDirectory(): File = packages
            }
            val installer = DefaultPluginInstaller(fetcher, catalog)
            val entry = MarketPluginEntry(
                id = "example.hello",
                name = "Hello",
                version = "1.0.0",
                downloadUrl = "https://example.invalid/hello.apk",
                size = payload.size.toLong(),
                checksum = PluginPackageVerifier.sha256Hex(
                    File(root, "seed").also { it.writeBytes(payload) }
                )
            )
            val result = installer.install(entry)
            assertTrue(result is PluginInstallResult.Success)
            val success = result as PluginInstallResult.Success
            assertEquals("example.hello", success.pluginId)
            assertTrue(success.loaded)
            assertTrue(File(success.apkPath).isFile)
            assertEquals(File(packages, "example.hello.apk").absolutePath, success.apkPath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `empty download url fails`() = runBlocking {
        val packages = File(System.getProperty("java.io.tmpdir"), "mkt-empty-${System.nanoTime()}")
        packages.mkdirs()
        try {
            val catalog = object : PluginCatalog {
                override fun getPluginRecords() = emptyList<PluginRecord>()
                override fun getLastDynamicFailures() = emptyList<PluginLoadResult.Failure>()
                override suspend fun setEnabled(pluginId: String, enabled: Boolean) = false
                override suspend fun unloadPlugin(pluginId: String) = false
                override suspend fun discoverAndLoadDynamicPlugins(packagesDir: File?) =
                    DynamicDiscoverResult()
                override suspend fun loadDynamicPlugin(apkFile: File) =
                    PluginLoadResult.Failure(apkFile.path, null, "n/a")
                override fun packagesDirectory(): File = packages
            }
            val fetcher = object : MarketHttpFetcher {
                override suspend fun getText(url: String): Result<String> =
                    Result.failure(Exception("not used"))
                override suspend fun downloadToFile(
                    url: String,
                    destFile: File,
                    onProgress: (Float) -> Unit
                ): Result<Long> = Result.failure(Exception("not used"))
            }
            val installer = DefaultPluginInstaller(fetcher, catalog)
            val result = installer.install(
                MarketPluginEntry(
                    id = "x",
                    name = "x",
                    version = "1",
                    downloadUrl = ""
                )
            )
            assertTrue(result is PluginInstallResult.Failure)
        } finally {
            packages.deleteRecursively()
        }
    }

    @Test
    fun `resolveInstallStatus`() {
        assertEquals(
            MarketInstallStatus.NOT_INSTALLED,
            DefaultPluginInstaller.resolveInstallStatus(null, "1.0.0")
        )
        assertEquals(
            MarketInstallStatus.INSTALLED,
            DefaultPluginInstaller.resolveInstallStatus("1.0.0", "1.0.0")
        )
        assertEquals(
            MarketInstallStatus.UPDATE_AVAILABLE,
            DefaultPluginInstaller.resolveInstallStatus("1.0.0", "1.1.0")
        )
    }
}
