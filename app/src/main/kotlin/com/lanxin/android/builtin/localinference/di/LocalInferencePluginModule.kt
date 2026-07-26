package com.lanxin.android.builtin.localinference.di

import com.lanxin.android.builtin.localinference.LocalInferencePlugin
import com.lanxin.android.builtin.localinference.domain.LocalInferencePluginConfig
import com.lanxin.android.plugin.PluginManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalInferencePluginModule {

    @Provides
    @Singleton
    fun provideLocalInferencePluginRegistration(
        pluginManager: PluginManager,
        plugin: LocalInferencePlugin
    ): LocalInferencePluginRegistration {
        pluginManager.register(plugin, defaultEnabled = LocalInferencePluginConfig.DEFAULT_ENABLED)
        return LocalInferencePluginRegistration(plugin)
    }
}

data class LocalInferencePluginRegistration(val plugin: LocalInferencePlugin)
