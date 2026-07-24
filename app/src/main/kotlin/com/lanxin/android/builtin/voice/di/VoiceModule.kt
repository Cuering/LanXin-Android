/*
 * Copyright 2025 LanXin Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lanxin.android.builtin.voice.di

import com.lanxin.android.builtin.voice.data.AndroidMicPermissionChecker
import com.lanxin.android.builtin.voice.data.AndroidTtsFallback
import com.lanxin.android.builtin.voice.data.AsrPreferences
import com.lanxin.android.builtin.voice.data.SherpaAsrEngine
import com.lanxin.android.builtin.voice.data.SherpaTtsEngine
import com.lanxin.android.builtin.voice.data.TtsPreferences
import com.lanxin.android.builtin.voice.domain.AsrEngine
import com.lanxin.android.builtin.voice.domain.AsrSettings
import com.lanxin.android.builtin.voice.domain.MicPermissionChecker
import com.lanxin.android.builtin.voice.domain.SystemTtsSpeaker
import com.lanxin.android.builtin.voice.domain.TtsEngine
import com.lanxin.android.builtin.voice.domain.TtsSettings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

/**
 * 语音 DI（ASR + TTS）。
 *
 * ASR：绑定 [SherpaAsrEngine]（native 可用则真识别，否则路径合法时 stub 降级）。
 * TTS：绑定 [SherpaTtsEngine]（native OfflineTts 可用则真合成，否则 stub 降级）。
 * SystemTTS：绑定 [AndroidTtsFallback]（系统 TextToSpeech 后备）。
 */
@Module
@DisableInstallInCheck
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    @Singleton
    abstract fun bindAsrEngine(impl: SherpaAsrEngine): AsrEngine

    @Binds
    @Singleton
    abstract fun bindAsrSettings(impl: AsrPreferences): AsrSettings

    @Binds
    @Singleton
    abstract fun bindMicPermissionChecker(impl: AndroidMicPermissionChecker): MicPermissionChecker

    @Binds
    @Singleton
    abstract fun bindTtsEngine(impl: SherpaTtsEngine): TtsEngine

    @Binds
    @Singleton
    abstract fun bindTtsSettings(impl: TtsPreferences): TtsSettings

    @Binds
    @Singleton
    abstract fun bindSystemTtsSpeaker(impl: AndroidTtsFallback): SystemTtsSpeaker
}
