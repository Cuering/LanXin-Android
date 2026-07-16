package com.lanxin.android.plugin.market.di

import com.lanxin.android.plugin.market.CompositePluginMarketRepository
import com.lanxin.android.plugin.market.DefaultPluginInstaller
import com.lanxin.android.plugin.market.KtorMarketHttpFetcher
import com.lanxin.android.plugin.market.MarketHttpFetcher
import com.lanxin.android.plugin.market.MarketPreferences
import com.lanxin.android.plugin.market.MarketSettings
import com.lanxin.android.plugin.market.PluginInstaller
import com.lanxin.android.plugin.market.PluginMarketRepository
import com.lanxin.android.plugin.market.RemotePluginMarketRepository
import com.lanxin.android.plugin.market.SamplePluginMarketRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PluginMarketBindModule {

    @Binds
    @Singleton
    abstract fun bindMarketHttpFetcher(impl: KtorMarketHttpFetcher): MarketHttpFetcher

    @Binds
    @Singleton
    abstract fun bindPluginInstaller(impl: DefaultPluginInstaller): PluginInstaller

    @Binds
    @Singleton
    abstract fun bindMarketSettings(impl: MarketPreferences): MarketSettings
}

@Module
@InstallIn(SingletonComponent::class)
object PluginMarketProvideModule {

    @Provides
    @Singleton
    fun providePluginMarketRepository(
        fetcher: MarketHttpFetcher,
        settings: MarketSettings
    ): PluginMarketRepository {
        val remote = RemotePluginMarketRepository(
            catalogUrlProvider = { settings.getCatalogUrl() },
            fetcher = fetcher
        )
        return CompositePluginMarketRepository(
            remote = remote,
            sample = SamplePluginMarketRepository(),
            fallbackToSample = true
        )
    }
}
