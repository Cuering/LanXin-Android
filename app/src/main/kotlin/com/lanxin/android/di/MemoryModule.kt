package com.lanxin.android.di

import android.content.Context
import com.lanxin.android.data.memory.MemoryDao
import com.lanxin.android.data.memory.MemoryDatabase
import com.lanxin.android.data.memory.MemoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MemoryModule {

    @Provides
    @Singleton
    fun provideMemoryDatabase(@ApplicationContext context: Context): MemoryDatabase =
        MemoryDatabase.getInstance(context)

    @Provides
    fun provideMemoryDao(db: MemoryDatabase): MemoryDao = db.memoryDao()

    // MemoryRepository 使用 @Inject constructor + @Singleton，无需额外 provide
}
