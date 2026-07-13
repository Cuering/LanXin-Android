package com.lanxin.android.plugins.memory.domain.memory

/**
 * 记忆导入策略。
 */
enum class ImportStrategy {
    /** 清空数据库后导入 */
    REPLACE,

    /** 按 ID 跳过已存在的，仅新增 */
    MERGE_BY_ID,

    /** 按 content + type 去重后新增 */
    MERGE_DEDUP
}
