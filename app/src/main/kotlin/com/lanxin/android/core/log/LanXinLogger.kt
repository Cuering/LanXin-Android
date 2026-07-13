package com.lanxin.android.core.log

import android.util.Log

/**
 * 轻量 Logger，对齐 AstrBot GetLogger() 用法。
 */
class LanXinLogger internal constructor(
    private val tag: String,
    private val manager: LogManager
) {
    fun d(message: String, throwable: Throwable? = null) = log(LogLevel.DEBUG, message, throwable)
    fun i(message: String, throwable: Throwable? = null) = log(LogLevel.INFO, message, throwable)
    fun w(message: String, throwable: Throwable? = null) = log(LogLevel.WARNING, message, throwable)
    fun e(message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, message, throwable)
    fun c(message: String, throwable: Throwable? = null) = log(LogLevel.CRITICAL, message, throwable)

    fun debug(message: String, throwable: Throwable? = null) = d(message, throwable)
    fun info(message: String, throwable: Throwable? = null) = i(message, throwable)
    fun warning(message: String, throwable: Throwable? = null) = w(message, throwable)
    fun error(message: String, throwable: Throwable? = null) = e(message, throwable)
    fun critical(message: String, throwable: Throwable? = null) = c(message, throwable)

    private fun log(level: LogLevel, message: String, throwable: Throwable?) {
        manager.emit(LogEntry(level = level, tag = tag, message = message, throwable = throwable))
        when (level) {
            LogLevel.DEBUG -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
            LogLevel.INFO -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
            LogLevel.WARNING -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
            LogLevel.ERROR, LogLevel.CRITICAL ->
                if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        }
    }
}
