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
 * 另提供 [reportNonFatal]：Service/Overlay 等软失败写 error-*.log + lanxin.log，
 * 不杀进程、不弹崩溃页，便于复现「闪一下就没了」的场景。
 *
 * 注意：JNI Abort / SIGABRT **不会**走本 handler，只覆盖 Java/Kotlin 未捕获异常。
 * native 侧已改用 NewString(UTF-16)，不再用 NewStringUTF（后者对 emoji 会 abort）。
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val TAG = "CrashHandler"

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        this.appContext = context.applicationContext
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
        logI("installed")
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        val crashInfo = getCrashInfo(ex)
        val deviceInfo = getDeviceInfo()
        val time = now()

        // 1) 写崩溃文件（同步，进程即将退出）
        writeLogFile(
            prefix = "crash",
            header = "=== LanXin Crash ===",
            deviceInfo = deviceInfo,
            time = time,
            threadName = thread.name,
            body = crashInfo,
            level = "CRITICAL",
            tag = "uncaught on ${thread.name}"
        )

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
                logE("start CrashDisplayActivity failed", t)
            }
        }

        // 3) 交给系统默认（杀进程 / 系统对话框）
        try {
            defaultHandler?.uncaughtException(thread, ex)
        } catch (_: Throwable) {
            // ignore
        }
    }

    /**
     * 非致命错误上报：写 error-*.log + 追加 lanxin.log。
     * 用于 FGS 被拒、悬浮窗 attach 失败、协程内异常等「进程不一定死但功能挂了」的场景。
     *
     * @param where 短标签，如 `FloatingPetService.onCreate`
     * @param error 异常；可与 [detail] 二选一
     * @param detail 无异常时的文字说明
     */
    fun reportNonFatal(
        where: String,
        error: Throwable? = null,
        detail: String? = null
    ) {
        val body = buildString {
            appendLine("where: $where")
            if (!detail.isNullOrBlank()) appendLine(detail)
            if (error != null) {
                appendLine("--- stack ---")
                append(getCrashInfo(error))
            }
        }
        logE("non-fatal [$where]: ${error?.message ?: detail}")
        writeLogFile(
            prefix = "error",
            header = "=== LanXin Non-Fatal ===",
            deviceInfo = getDeviceInfo(),
            time = now(),
            threadName = Thread.currentThread().name,
            body = body,
            level = "ERROR",
            tag = "non-fatal [$where]"
        )
    }

    private fun writeLogFile(
        prefix: String,
        header: String,
        deviceInfo: String,
        time: String,
        threadName: String,
        body: String,
        level: String,
        tag: String
    ) {
        val ctx = appContext ?: return
        try {
            val dir = File(ctx.filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "$prefix-$stamp.log")
            file.writeText(
                buildString {
                    appendLine(header)
                    appendLine("time: $time")
                    appendLine("thread: $threadName")
                    appendLine(deviceInfo)
                    appendLine(body)
                },
                Charsets.UTF_8
            )
            val current = File(dir, "lanxin.log")
            current.appendText(
                "\n[$time] [$level] [CrashHandler]: $tag\n$body\n",
                Charsets.UTF_8
            )
            logE("$prefix written: ${file.absolutePath}")
        } catch (t: Throwable) {
            logE("writeLogFile($prefix) failed", t)
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

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    /** JVM 单测 android.jar 上 Log 为 stub，会抛 RuntimeException，全部包一层。 */
    private fun logI(msg: String) {
        runCatching { Log.i(TAG, msg) }
    }

    private fun logE(msg: String, t: Throwable? = null) {
        runCatching {
            if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
        }
    }
}
