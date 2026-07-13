package com.lanxin.android.core.log

/**
 * 单条日志记录。
 */
data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val throwable: Throwable? = null
) {
    fun formatLine(): String {
        val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date(timestamp))
        val base = "[$time] [${level.shortName}] [$tag]: $message"
        return if (throwable != null) {
            base + "\n" + throwable.stackTraceToString()
        } else {
            base
        }
    }
}
