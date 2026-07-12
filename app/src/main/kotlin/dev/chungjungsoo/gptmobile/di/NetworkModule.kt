package com.lanxin.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.lanxin.android.data.network.AnthropicAPI
import com.lanxin.android.data.network.AnthropicAPIImpl
import com.lanxin.android.data.network.GoogleAPI
import com.lanxin.android.data.network.GoogleAPIImpl
import com.lanxin.android.data.network.GroqAPI
import com.lanxin.android.data.network.GroqAPIImpl
import com.lanxin.android.data.network.NetworkClient
import com.lanxin.android.data.network.OpenAIAPI
import com.lanxin.android.data.network.OpenAIAPIImpl
import io.ktor.client.engine.cio.CIO
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideNetworkClient(): NetworkClient = NetworkClient(CIO)

    @Provides
    @Singleton
    fun provideOpenAIAPI(networkClient: NetworkClient): OpenAIAPI = OpenAIAPIImpl(networkClient)

    @Provides
    @Singleton
    fun provideGroqAPI(networkClient: NetworkClient): GroqAPI = GroqAPIImpl(networkClient)

    @Provides
    @Singleton
    fun provideAnthropicAPI(networkClient: NetworkClient): AnthropicAPI = AnthropicAPIImpl(networkClient)

    @Provides
    @Singleton
    fun provideGoogleAPI(networkClient: NetworkClient): GoogleAPI = GoogleAPIImpl(networkClient)

    @Provides
    @Singleton
    fun provideLanXinAPI(): LanXinAPI = LanXinAPIImpl()
}
