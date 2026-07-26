/*
 * Copyright 2025 LanXin Contributors
 */
package com.lanxin.android.builtin.navigate.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 导航已从主程序剥离：不再 register。
 * 源码保留；市场动态包安装后加载。
 */
@Module
@InstallIn(SingletonComponent::class)
object NavigateModule
