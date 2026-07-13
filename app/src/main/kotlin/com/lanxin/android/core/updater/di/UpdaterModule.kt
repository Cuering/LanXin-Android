package com.lanxin.android.core.updater.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 更新系统 DI。
 * UpdateChecker / ApkDownloader / DataBackupManager / DataRestoreManager
 * 均使用 @Inject constructor + @Singleton，Hilt 自动提供。
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdaterModule
