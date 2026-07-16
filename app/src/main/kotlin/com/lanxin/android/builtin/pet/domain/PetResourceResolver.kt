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

package com.lanxin.android.builtin.pet.domain

import com.lanxin.android.builtin.voice.domain.AsrConfig
import com.lanxin.android.builtin.voice.domain.TtsConfig
import java.io.File

/**
 * 将 DataStore 配置 +（可选）debug 妹居旁路解析为运行时路径。
 *
 * VoiceSession / PetOverlay **只读**解析结果，不写死妹居文件名。
 */
object PetResourceResolver {

    data class ResolvedPaths(
        val live2dModelPath: String,
        val ttsModelDir: String,
        val ttsReferenceAudio: String,
        val asrModelPath: String,
        val live2dSource: MeijuDebugPaths.ResourceSource,
        val ttsSource: MeijuDebugPaths.ResourceSource,
        val asrSource: MeijuDebugPaths.ResourceSource
    ) {
        val live2dLabel: String get() = MeijuDebugPaths.sourceLabel(live2dSource)
        val ttsLabel: String get() = MeijuDebugPaths.sourceLabel(ttsSource)
        val asrLabel: String get() = MeijuDebugPaths.sourceLabel(asrSource)
    }

    /**
     * @param filesDir Context.filesDir
     * @param isDebug BuildConfig.DEBUG；false 时绝不自动选用 meiju-ref
     */
    fun resolve(
        filesDir: File,
        pet: PetConfig,
        tts: TtsConfig,
        asr: AsrConfig,
        isDebug: Boolean
    ): ResolvedPaths {
        val live2dConfigured = pet.live2dModelPath
        val ttsDirConfigured = tts.modelDir.ifBlank { tts.modelPath }
        val ttsRefConfigured = tts.referenceAudio
        val asrConfigured = asr.modelPath

        val live2d = if (isDebug) {
            MeijuDebugPaths.resolveLive2dIfPresent(filesDir, live2dConfigured)
        } else {
            live2dConfigured.trim()
        }
        val ttsDir = if (isDebug) {
            MeijuDebugPaths.resolveTtsModelDirIfPresent(filesDir, ttsDirConfigured)
        } else {
            ttsDirConfigured.trim()
        }
        val ttsRef = if (isDebug) {
            MeijuDebugPaths.resolveTtsReferenceIfPresent(filesDir, ttsRefConfigured)
        } else {
            ttsRefConfigured.trim()
        }
        val asrPath = if (isDebug) {
            MeijuDebugPaths.resolveAsrIfPresent(filesDir, asrConfigured)
        } else {
            asrConfigured.trim()
        }

        return ResolvedPaths(
            live2dModelPath = live2d,
            ttsModelDir = ttsDir,
            ttsReferenceAudio = ttsRef,
            asrModelPath = asrPath,
            live2dSource = MeijuDebugPaths.classifySource(isDebug, live2dConfigured, live2d),
            ttsSource = MeijuDebugPaths.classifySource(
                isDebug,
                ttsDirConfigured.ifBlank { ttsRefConfigured },
                ttsDir.ifBlank { ttsRef }
            ),
            asrSource = MeijuDebugPaths.classifySource(isDebug, asrConfigured, asrPath)
        )
    }
}
