package com.lanxin.android.plugin.di

import com.lanxin.android.plugin.PluginCatalog
import com.lanxin.android.plugin.PluginManager
import com.lanxin.android.plugin.claw.data.ClawHostPreferences
import com.lanxin.android.plugin.claw.data.DefaultPlatformHost
import com.lanxin.android.plugin.claw.domain.ClawHostSettings
import com.lanxin.android.plugin.claw.domain.PlatformHost
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PluginModule {

    @Binds
    @Singleton
    abstract fun bindPluginCatalog(impl: PluginManager): PluginCatalog

    @Binds
    @Singleton
    abstract fun bindClawHostSettings(impl: ClawHostPreferences): ClawHostSettings

    @Binds
    @Singleton
    abstract fun bindPlatformHost(impl: DefaultPlatformHost): PlatformHost
}
