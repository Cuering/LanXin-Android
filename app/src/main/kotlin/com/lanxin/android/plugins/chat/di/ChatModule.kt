package com.lanxin.android.plugins.chat.di

import com.lanxin.android.core.engine.ChatHistoryProvider
import com.lanxin.android.plugin.PluginManager
import com.lanxin.android.plugins.chat.ChatPlugin
import com.lanxin.android.plugins.chat.data.ChatHistoryProviderImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 聊天插件 DI。
 *
 * Database / ChatRepository 仍由 [DatabaseModule] / [ChatRepositoryModule] 提供。
 * 本模块负责 ChatHistoryProvider 绑定与插件注册。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChatBindModule {

    @Binds
    @Singleton
    abstract fun bindChatHistoryProvider(impl: ChatHistoryProviderImpl): ChatHistoryProvider
}

@Module
@InstallIn(SingletonComponent::class)
object ChatModule {

    /**
     * 注册 ChatPlugin 到 PluginManager。
     * 返回包装类型，避免与 ChatPlugin 的 @Inject 绑定冲突。
     */
    @Provides
    @Singleton
    fun provideChatPluginRegistration(
        pluginManager: PluginManager,
        plugin: ChatPlugin
    ): ChatPluginRegistration {
        pluginManager.register(plugin)
        return ChatPluginRegistration(plugin)
    }
}

/** 注册副作用载体。 */
data class ChatPluginRegistration(val plugin: ChatPlugin)
