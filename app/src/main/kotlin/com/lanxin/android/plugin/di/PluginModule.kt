package com.lanxin.android.plugin.di

import com.lanxin.android.plugin.PluginCatalog
import com.lanxin.android.plugin.PluginManager
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
}
