package com.lanxin.android.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.lanxin.android.data.datastore.SettingDataSource
import com.lanxin.android.data.datastore.SettingDataSourceImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingDataSourceModule {
    @Provides
    @Singleton
    fun provideSettingDataStore(dataStore: DataStore<Preferences>): SettingDataSource = SettingDataSourceImpl(dataStore)
}
