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

package com.lanxin.android.builtin.pet.di

import com.lanxin.android.builtin.pet.data.KtorAssetDownloadTransport
import com.lanxin.android.builtin.pet.data.PetPreferences
import com.lanxin.android.builtin.pet.domain.AssetDownloadTransport
import com.lanxin.android.builtin.pet.domain.OpenAiVisionExplainClient
import com.lanxin.android.builtin.pet.domain.LocalAwarePetChatResponder
import com.lanxin.android.builtin.pet.domain.PetChatResponder
import com.lanxin.android.builtin.pet.domain.PetSettings
import com.lanxin.android.builtin.pet.domain.VisionExplainClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

/**
 * 桌宠 / 语音会话 DI。
 *
 * [PetChatResponder] → [LocalAwarePetChatResponder]（本地就绪走本地，否则 stub）。
 */
@Module
@DisableInstallInCheck
@InstallIn(SingletonComponent::class)
abstract class PetModule {

    @Binds
    @Singleton
    abstract fun bindPetSettings(impl: PetPreferences): PetSettings

    @Binds
    @Singleton
    abstract fun bindPetChatResponder(impl: LocalAwarePetChatResponder): PetChatResponder

    @Binds
    @Singleton
    abstract fun bindAssetDownloadTransport(
        impl: KtorAssetDownloadTransport
    ): AssetDownloadTransport

    @Binds
    @Singleton
    abstract fun bindVisionExplainClient(impl: OpenAiVisionExplainClient): VisionExplainClient
}
