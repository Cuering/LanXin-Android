package com.lanxin.android.core.updater

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Phase 1 硬编码备份：memory.db + chat*.db + DataStore + manifest.json
 */
@Singleton
class DataBackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun createBackup(
        pluginVersions: Map<String, String> = emptyMap()
    ): File = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val zipFile = File(backupDir, "lanxin_backup_$stamp.zip")

        val manifest = BackupManifest(
            backupTime = System.currentTimeMillis(),
            appVersion = currentVersionName(),
            pluginVersions = pluginVersions
        )

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            // manifest
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // databases
            val dbDir = context.getDatabasePath("chat_v2").parentFile
            if (dbDir != null && dbDir.exists()) {
                listOf("lanxin_memory.db", "chat", "chat_v2").forEach { name ->
                    listOf(name, "$name-shm", "$name-wal").forEach { fileName ->
                        val f = File(dbDir, fileName)
                        if (f.exists()) {
                            putFile(zos, f, "databases/$fileName")
                        }
                    }
                }
            }

            // DataStore
            val dsDir = File(context.filesDir, "datastore")
            if (dsDir.exists()) {
                dsDir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val rel = "datastore/${f.relativeTo(dsDir).path}"
                    putFile(zos, f, rel)
                }
            }
        }
        zipFile
    }

    fun listBackups(): List<File> {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    private fun putFile(zos: ZipOutputStream, file: File, entryName: String) {
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { input ->
            input.copyTo(zos)
        }
        zos.closeEntry()
    }

    private fun currentVersionName(): String = try {
        val pm = context.packageManager
        val pkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        pkg.versionName ?: "0.0.0"
    } catch (_: Exception) {
        "0.0.0"
    }
}

@Serializable
data class BackupManifest(
    val backupTime: Long,
    val appVersion: String,
    val pluginVersions: Map<String, String> = emptyMap()
)
