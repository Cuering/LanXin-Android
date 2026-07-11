package com.lanxin.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.lanxin.android.data.database.dao.ChatPlatformModelV2Dao
import com.lanxin.android.data.database.dao.PlatformV2Dao
import com.lanxin.android.data.datastore.SettingDataSource
import com.lanxin.android.data.repository.SettingRepository
import com.lanxin.android.data.repository.SettingRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingRepositoryModule {

    @Provides
    @Singleton
    fun provideSettingRepository(
        settingDataSource: SettingDataSource,
        platformV2Dao: PlatformV2Dao,
        chatPlatformModelV2Dao: ChatPlatformModelV2Dao
    ): SettingRepository = SettingRepositoryImpl(settingDataSource, platformV2Dao, chatPlatformModelV2Dao)
}
