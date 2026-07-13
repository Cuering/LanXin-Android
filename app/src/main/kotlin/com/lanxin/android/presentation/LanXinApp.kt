package com.lanxin.android.presentation

import android.app.Application
import android.content.Context
import com.lanxin.android.core.log.LogManager
import com.lanxin.android.plugin.PluginManager
import com.lanxin.android.plugins.chat.ChatPlugin
import com.lanxin.android.plugins.chat.di.ChatPluginRegistration
import com.lanxin.android.plugins.logger.LoggerPlugin
import com.lanxin.android.plugins.logger.di.LoggerPluginRegistration
import com.lanxin.android.plugins.memory.MemoryPlugin
import com.lanxin.android.plugins.memory.di.MemoryPluginRegistration
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltAndroidApp
class LanXinApp : Application() {
    // TODO Delete when https://github.com/google/dagger/issues/3601 is resolved.
    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var pluginManager: PluginManager

    @Inject
    lateinit var logManager: LogManager

    @Inject
    lateinit var memoryPlugin: MemoryPlugin

    @Inject
    lateinit var chatPlugin: ChatPlugin

    @Inject
    lateinit var loggerPlugin: LoggerPlugin

    /** 触发各插件 Module 中的注册 provide（幂等）。 */
    @Inject
    lateinit var memoryPluginRegistration: MemoryPluginRegistration

    @Inject
    lateinit var chatPluginRegistration: ChatPluginRegistration

    @Inject
    lateinit var loggerPluginRegistration: LoggerPluginRegistration

    override fun onCreate() {
        super.onCreate()
        logManager.initialize(this)
        logManager.getLogger("LanXinApp").info("LanXin starting…")

        // Registration provides 已在 DI 构建时 register；此处再保底一次
        pluginManager.register(memoryPlugin)
        pluginManager.register(chatPlugin)
        pluginManager.register(loggerPlugin)
        pluginManager.initializeAll(this)
    }
}
