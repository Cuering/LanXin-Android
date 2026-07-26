/*
 * Copyright 2025 LanXin Contributors
 */
package com.lanxin.android.builtin.guide.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 导游已从主程序剥离：不再 register 到 PluginManager。
 * 源码保留；请从插件市场安装动态包后加载。
 */
@Module
@InstallIn(SingletonComponent::class)
object GuideModule
