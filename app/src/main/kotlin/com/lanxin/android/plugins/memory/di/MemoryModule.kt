package com.lanxin.android.plugins.memory.di

import android.content.Context
import com.lanxin.android.plugin.PluginManager
import com.lanxin.android.plugins.memory.MemoryPlugin
import com.lanxin.android.plugins.memory.data.memory.MemoryDao
import com.lanxin.android.plugins.memory.data.memory.MemoryDatabase
import com.lanxin.android.plugins.memory.domain.memory.MemoryIndexRebuilder
import com.lanxin.android.plugins.memory.domain.memory.VectorPipelineMemoryIndexRebuilder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 记忆插件 DI。
 *
 * - MemoryDatabase / MemoryDao 由此提供
 * - MemoryRepository / MemoryInjector 使用 @Inject constructor + @Singleton，Hilt 自动提供（ChatViewModel 可直接注入）
 * - MemoryPlugin 使用 @Inject constructor；通过 [provideMemoryPluginRegistration] 注册到 PluginManager
 *   （LanXinApp 也会 register 一次，PluginManager 对重复 id 幂等）
 */
@Module
@InstallIn(SingletonComponent::class)
object MemoryModule {

    @Provides
    @Singleton
    fun provideMemoryDatabase(@ApplicationContext context: Context): MemoryDatabase =
        MemoryDatabase.getInstance(context)

    @Provides
    fun provideMemoryDao(db: MemoryDatabase): MemoryDao = db.memoryDao()

    /**
     * 注册 MemoryPlugin 到 PluginManager。
     * 返回包装类型，避免与 MemoryPlugin 的 @Inject 绑定冲突。
     * 在 LanXinApp 中注入 [MemoryPluginRegistration] 可确保此 provide 被调用。
     */
    @Provides
    @Singleton
    fun provideMemoryPluginRegistration(
        pluginManager: PluginManager,
        plugin: MemoryPlugin
    ): MemoryPluginRegistration {
        pluginManager.register(plugin)
        return MemoryPluginRegistration(plugin)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MemoryIndexModule {
    @Binds
    @Singleton
    abstract fun bindMemoryIndexRebuilder(
        impl: VectorPipelineMemoryIndexRebuilder
    ): MemoryIndexRebuilder
}

/** 注册副作用载体，避免与 [MemoryPlugin] 的 @Inject 绑定冲突。 */
data class MemoryPluginRegistration(val plugin: MemoryPlugin)
