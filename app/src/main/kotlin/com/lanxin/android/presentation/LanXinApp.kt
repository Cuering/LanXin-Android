package com.lanxin.android.presentation

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lanxin.android.builtin.persona.di.PersonaPluginRegistration
import com.lanxin.android.builtin.scheduler.di.SchedulerPluginRegistration
import com.lanxin.android.builtin.statistics.di.StatisticsPluginRegistration
import com.lanxin.android.core.log.LogManager
import com.lanxin.android.plugin.PluginManager
import com.lanxin.android.plugins.chat.di.ChatPluginRegistration
import com.lanxin.android.plugins.logger.di.LoggerPluginRegistration
import com.lanxin.android.plugins.memory.di.MemoryPluginRegistration
import com.lanxin.android.plugins.unifiedinbox.di.UnifiedInboxPluginRegistration
import com.lanxin.android.skill.SkillEngineRegistration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class LanXinApp : Application(), Configuration.Provider {

    @Inject
    lateinit var pluginManager: PluginManager

    @Inject
    lateinit var logManager: LogManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** 触发各插件 Module 中的注册 provide（幂等）。 */
    @Inject
    lateinit var memoryPluginRegistration: MemoryPluginRegistration

    @Inject
    lateinit var chatPluginRegistration: ChatPluginRegistration

    @Inject
    lateinit var loggerPluginRegistration: LoggerPluginRegistration

    @Inject
    lateinit var skillEngineRegistration: SkillEngineRegistration

    @Inject
    lateinit var personaPluginRegistration: PersonaPluginRegistration

    @Inject
    lateinit var statisticsPluginRegistration: StatisticsPluginRegistration

    @Inject
    lateinit var schedulerPluginRegistration: SchedulerPluginRegistration

    @Inject
    lateinit var unifiedInboxPluginRegistration: UnifiedInboxPluginRegistration

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        logManager.initialize(this)
        val log = logManager.getLogger("LanXinApp")
        log.info("LanXin starting…")

        // 插件已通过 DI @Provides 注册到 PluginManager
        // 异步：加载编译期插件 + 扫描 filesDir/plugin-packages 动态插件
        appScope.launch {
            pluginManager.loadAll()
            val dynamic = pluginManager.discoverAndLoadDynamicPlugins()
            if (dynamic.total > 0) {
                log.info(
                    "动态插件: ok=${dynamic.successes.size} fail=${dynamic.failures.size}"
                )
            }
            dynamic.failures.forEach { f ->
                log.warn("动态插件加载失败 id=${f.pluginId}: ${f.reason}")
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
