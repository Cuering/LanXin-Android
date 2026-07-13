package com.lanxin.android.core.log

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日志管理器：初始化文件轮转 + 提供 getLogger()。
 * 参考 AstrBot LogManager。
 */
@Singleton
class LogManager @Inject constructor(
    private val logBroker: LogBroker
) {
    private val initialized = AtomicBoolean(false)
    private val loggers = ConcurrentHashMap<String, LanXinLogger>()
    private val writerExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "lanxin-log-writer").apply { isDaemon = true }
    }

    @Volatile
    private var logDir: File? = null

    @Volatile
    private var minLevel: LogLevel = LogLevel.DEBUG

    @Volatile
    private var maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES

    fun initialize(
        context: Context,
        minLevel: LogLevel = LogLevel.DEBUG,
        maxFileMb: Int = 5
    ) {
        if (!initialized.compareAndSet(false, true)) return
        this.minLevel = minLevel
        this.maxFileBytes = maxFileMb * 1024L * 1024L
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        logDir = dir
    }

    fun getLogger(name: String = "default"): LanXinLogger =
        loggers.getOrPut(name) { LanXinLogger(name, this) }

    fun getLogDir(): File? = logDir

    fun listLogFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    internal fun emit(entry: LogEntry) {
        if (entry.level.priority < minLevel.priority) return
        logBroker.publish(entry)
        writerExecutor.execute { writeToFile(entry) }
    }

    private fun writeToFile(entry: LogEntry) {
        val dir = logDir ?: return
        try {
            val file = File(dir, CURRENT_LOG_NAME)
            rotateIfNeeded(file)
            FileOutputStream(file, true).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    writer.append(entry.formatLine())
                    writer.append('\n')
                }
            }
        } catch (_: Exception) {
            // 避免日志写失败再递归打日志
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() < maxFileBytes) return
        val rotated = File(file.parentFile, "lanxin-${System.currentTimeMillis()}.log")
        file.renameTo(rotated)
        // 仅保留最近 N 个轮转文件 + 当前文件
        val oldFiles = listLogFiles().filter { it.name != CURRENT_LOG_NAME }
        if (oldFiles.size > MAX_ROTATED_FILES) {
            oldFiles.drop(MAX_ROTATED_FILES).forEach { it.delete() }
        }
    }

    companion object {
        const val CURRENT_LOG_NAME = "lanxin.log"
        private const val DEFAULT_MAX_FILE_BYTES = 5L * 1024 * 1024
        private const val MAX_ROTATED_FILES = 5
    }
}
