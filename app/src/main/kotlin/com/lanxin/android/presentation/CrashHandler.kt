package com.lanxin.android.presentation

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 全局异常处理器
 * 当 App 发生未捕获的异常时，跳转到 CrashDisplayActivity 显示错误信息
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var context: Context? = null

    fun init(appContext: Context) {
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        this.context = appContext
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(
        thread: Thread,
        ex: Throwable
    ) {
        val crashInfo = getCrashInfo(ex)

        // 保存崩溃信息到 Intent extras
        val intent = Intent(context, CrashDisplayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("crash_title", "💥 兰心出了点问题")
            putExtra("crash_device", getDeviceInfo())
            putExtra("crash_time", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(System.currentTimeMillis()))
            putExtra("crash_stack", crashInfo)
        }

        context?.startActivity(intent)

        // 不退出进程，让 CrashDisplayActivity 显示
        defaultHandler?.uncaughtException(thread, ex)
    }

    private fun getCrashInfo(ex: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        ex.printStackTrace(pw)
        var cause = ex.cause
        while (cause != null) {
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
        appendLine("应用包名：${context?.packageName}")
    }
}
