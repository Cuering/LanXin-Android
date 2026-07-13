package com.lanxin.android.presentation

import android.app.Application
import android.content.Context
import com.lanxin.android.plugin.PluginManager
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
    lateinit var memoryPlugin: MemoryPlugin

    /** 触发 MemoryModule 中的插件注册 provide（幂等）。 */
    @Inject
    lateinit var memoryPluginRegistration: MemoryPluginRegistration

    override fun onCreate() {
        super.onCreate()
        // MemoryPluginRegistration 已在 DI 构建时 register；此处再保底一次
        pluginManager.register(memoryPlugin)
        pluginManager.initializeAll(this)
    }
}
