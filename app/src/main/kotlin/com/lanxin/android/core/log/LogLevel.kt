package com.lanxin.android.core.log

/**
 * 日志级别（对齐 AstrBot / 标准 logging）。
 */
enum class LogLevel(val priority: Int, val shortName: String) {
    DEBUG(1, "DBUG"),
    INFO(2, "INFO"),
    WARNING(3, "WARN"),
    ERROR(4, "ERRO"),
    CRITICAL(5, "CRIT");

    companion object {
        fun fromName(name: String): LogLevel? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) || it.shortName.equals(name, ignoreCase = true) }
    }
}
