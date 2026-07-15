package com.lanxin.android.plugins.unifiedinbox.di

import android.content.Context
import com.lanxin.android.plugin.PluginManager
import com.lanxin.android.plugins.unifiedinbox.UnifiedInboxPlugin
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionDao
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 统一收件箱插件 DI。
 *
 * - CrossSessionDatabase / Dao 由此提供
 * - Repository / Indexer / FileBrowser / Injector 使用 @Inject constructor
 * - UnifiedInboxPlugin 通过 [provideUnifiedInboxPluginRegistration] 注册到 PluginManager
 */
@Module
@InstallIn(SingletonComponent::class)
object UnifiedInboxModule {

    @Provides
    @Singleton
    fun provideCrossSessionDatabase(
        @ApplicationContext context: Context
    ): CrossSessionDatabase = CrossSessionDatabase.getInstance(context)

    @Provides
    fun provideCrossSessionDao(db: CrossSessionDatabase): CrossSessionDao = db.crossSessionDao()

    @Provides
    @Singleton
    fun provideUnifiedInboxPluginRegistration(
        pluginManager: PluginManager,
        plugin: UnifiedInboxPlugin
    ): UnifiedInboxPluginRegistration {
        pluginManager.register(plugin)
        return UnifiedInboxPluginRegistration(plugin)
    }
}

/** 注册副作用载体。 */
data class UnifiedInboxPluginRegistration(val plugin: UnifiedInboxPlugin)
