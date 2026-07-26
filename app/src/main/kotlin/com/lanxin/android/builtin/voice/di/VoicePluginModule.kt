package com.lanxin.android.builtin.voice.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * ASR / TTS 已从主程序默认插件剥离：
 * - ASR：不注册；需动态插件或后续市场包
 * - TTS：保留引擎实现供桌宠可选调用，但不作为「默认安装的插件」注册到 PluginManager
 *
 * 空 Module 避免 Hilt 图断裂。
 */
@Module
@InstallIn(SingletonComponent::class)
object VoicePluginModule
