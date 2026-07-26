package com.lanxin.android.builtin.voice.di

import com.lanxin.android.builtin.voice.AsrPlugin
import com.lanxin.android.builtin.voice.TtsPlugin
import com.lanxin.android.builtin.voice.domain.AsrPluginConfig
import com.lanxin.android.builtin.voice.domain.TtsPluginConfig
import com.lanxin.android.plugin.PluginManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoicePluginModule {

    @Provides
    @Singleton
    fun provideAsrPluginRegistration(
        pluginManager: PluginManager,
        plugin: AsrPlugin
    ): AsrPluginRegistration {
        pluginManager.register(plugin, defaultEnabled = AsrPluginConfig.DEFAULT_ENABLED)
        return AsrPluginRegistration(plugin)
    }

    @Provides
    @Singleton
    fun provideTtsPluginRegistration(
        pluginManager: PluginManager,
        plugin: TtsPlugin
    ): TtsPluginRegistration {
        pluginManager.register(plugin, defaultEnabled = TtsPluginConfig.DEFAULT_ENABLED)
        return TtsPluginRegistration(plugin)
    }
}

data class AsrPluginRegistration(val plugin: AsrPlugin)
data class TtsPluginRegistration(val plugin: TtsPlugin)
