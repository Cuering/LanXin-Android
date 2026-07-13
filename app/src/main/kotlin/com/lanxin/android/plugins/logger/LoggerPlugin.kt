package com.lanxin.android.plugins.logger

import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoggerPlugin @Inject constructor() : LanXinPlugin {

    override val id = "lanxin.logger"
    override val name = "日志查看"
    override val version = "1.0.0"
    override val description = "浏览、过滤、搜索与导出本地日志"

    override suspend fun onLoad(context: PluginContext) {
        // UI-only plugin
    }
}
