package com.lanxin.android.core.updater

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 从备份 zip 还原 databases / datastore。
 * 注意：Room 打开期间还原可能导致损坏，调用方应在合适时机执行（如冷启动前/更新后）。
 */
@Singleton
class DataRestoreManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun restore(backupZip: File): BackupManifest = withContext(Dispatchers.IO) {
        require(backupZip.exists()) { "备份文件不存在: ${backupZip.absolutePath}" }

        var manifest: BackupManifest? = null
        val dbDir = context.getDatabasePath("chat_v2").parentFile
            ?: File(context.applicationInfo.dataDir, "databases")
        if (!dbDir.exists()) dbDir.mkdirs()
        val dsDir = File(context.filesDir, "datastore").apply { mkdirs() }

        ZipInputStream(BufferedInputStream(FileInputStream(backupZip))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    when {
                        entry.name == "manifest.json" -> {
                            val text = zis.readBytes().toString(Charsets.UTF_8)
                            manifest = json.decodeFromString(BackupManifest.serializer(), text)
                        }
                        entry.name.startsWith("databases/") -> {
                            val name = entry.name.removePrefix("databases/")
                            if (name.isNotBlank() && !name.contains("..")) {
                                val out = File(dbDir, name)
                                FileOutputStream(out).use { fos -> zis.copyTo(fos) }
                            }
                        }
                        entry.name.startsWith("datastore/") -> {
                            val rel = entry.name.removePrefix("datastore/")
                            if (rel.isNotBlank() && !rel.contains("..")) {
                                val out = File(dsDir, rel)
                                out.parentFile?.mkdirs()
                                FileOutputStream(out).use { fos -> zis.copyTo(fos) }
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        manifest ?: BackupManifest(
            backupTime = backupZip.lastModified(),
            appVersion = "unknown"
        )
    }

    suspend fun readManifest(backupZip: File): BackupManifest? = withContext(Dispatchers.IO) {
        if (!backupZip.exists()) return@withContext null
        ZipInputStream(BufferedInputStream(FileInputStream(backupZip))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "manifest.json") {
                    val text = zis.readBytes().toString(Charsets.UTF_8)
                    return@withContext json.decodeFromString(BackupManifest.serializer(), text)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        null
    }
}
