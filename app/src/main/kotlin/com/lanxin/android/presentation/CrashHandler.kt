package com.lanxin.android.presentation

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局异常处理器。
 *
 * 未捕获异常时：
 * 1. 同步写入 filesDir/logs/crash-*.log（方便无 adb 时从 App 内导出）
 * 2. 跳转 [CrashDisplayActivity] 展示堆栈，支持一键复制
 *
 * 注意：JNI Abort / SIGABRT（如 NewStringUTF 非法 UTF-8）**不会**走本 handler，
 * 需在 native 侧避免；本 handler 只覆盖 Java/Kotlin 未捕获异常。
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val TAG = "CrashHandler"

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        this.appContext = context.applicationContext
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
        Log.i(TAG, "installed")
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        val crashInfo = getCrashInfo(ex)
        val deviceInfo = getDeviceInfo()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())

        // 1) 写崩溃文件（同步，进程即将退出）
        writeCrashFile(deviceInfo, time, thread.name, crashInfo)

        // 2) 尽量弹展示页
        val ctx = appContext
        if (ctx != null) {
            try {
                val intent = Intent(ctx, CrashDisplayActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                    putExtra("crash_title", "💥 兰心出了点问题")
                    putExtra("crash_device", deviceInfo)
                    putExtra("crash_time", time)
                    putExtra("crash_stack", crashInfo)
                }
                ctx.startActivity(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "start CrashDisplayActivity failed", t)
            }
        }

        // 3) 交给系统默认（杀进程 / 系统对话框）
        try {
            defaultHandler?.uncaughtException(thread, ex)
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun writeCrashFile(
        deviceInfo: String,
        time: String,
        threadName: String,
        crashInfo: String
    ) {
        val ctx = appContext ?: return
        try {
            val dir = File(ctx.filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "crash-$stamp.log")
            file.writeText(
                buildString {
                    appendLine("=== LanXin Crash ===")
                    appendLine("time: $time")
                    appendLine("thread: $threadName")
                    appendLine(deviceInfo)
                    appendLine("--- stack ---")
                    appendLine(crashInfo)
                },
                Charsets.UTF_8
            )
            // 同步追加到当前滚动日志，方便日志页看到
            val current = File(dir, "lanxin.log")
            current.appendText(
                "\n[$time] [CRITICAL] [CrashHandler]: uncaught on $threadName\n$crashInfo\n",
                Charsets.UTF_8
            )
            Log.e(TAG, "crash written: ${file.absolutePath}")
        } catch (t: Throwable) {
            Log.e(TAG, "writeCrashFile failed", t)
        }
    }

    private fun getCrashInfo(ex: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        ex.printStackTrace(pw)
        var cause = ex.cause
        while (cause != null) {
            pw.println("Caused by:")
            cause.printStackTrace(pw)
            cause = cause.cause
        }
        pw.flush()
        return sw.toString()
    }

    private fun getDeviceInfo(): String = buildString {
        appendLine("设备型号：${Build.MODEL}")
        appendLine("品牌：${Build.BRAND}")
        appendLine("系统版本：Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("应用包名：${appContext?.packageName}")
    }
}
