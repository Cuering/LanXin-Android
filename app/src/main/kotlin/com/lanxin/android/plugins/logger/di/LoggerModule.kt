package com.lanxin.android.plugins.logger.di

import com.lanxin.android.plugin.PluginManager
import com.lanxin.android.plugins.logger.LoggerPlugin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LoggerModule {

    @Provides
    @Singleton
    fun provideLoggerPluginRegistration(
        pluginManager: PluginManager,
        plugin: LoggerPlugin
    ): LoggerPluginRegistration {
        pluginManager.register(plugin)
        return LoggerPluginRegistration(plugin)
    }
}

data class LoggerPluginRegistration(val plugin: LoggerPlugin)
