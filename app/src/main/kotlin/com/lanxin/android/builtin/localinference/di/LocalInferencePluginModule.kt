package com.lanxin.android.builtin.localinference.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 本地脑已从主程序剥离：不再向 PluginManager 注册编译期插件。
 * 能力源码仍保留供单测 / 后续动态插件包引用；用户从插件市场安装动态包后加载。
 *
 * 空 Module 保留，避免旧 import 断链；真正入口见 docs/plugins/。
 */
@Module
@InstallIn(SingletonComponent::class)
object LocalInferencePluginModule
