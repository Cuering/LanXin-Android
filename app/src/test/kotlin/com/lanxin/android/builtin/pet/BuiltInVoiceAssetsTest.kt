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

package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.BuiltInVoiceAssets
import com.lanxin.android.builtin.pet.domain.DebugOpenSourcePaths
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuiltInVoiceAssetsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun ttsAssetRoot_pointsAtVitsMelo() {
        assertEquals("voice/tts/vits-melo-tts-zh_en", BuiltInVoiceAssets.TTS_ASSET_ROOT)
        assertEquals("tokens.txt", BuiltInVoiceAssets.TTS_TOKENS_MARKER)
        assertEquals(
            "voice/tts/matcha-icefall-zh-baker",
            BuiltInVoiceAssets.TTS_ASSET_ROOT_LEGACY_MATCHA
        )
    }

    @Test
    fun isTtsInstalled_requiresReadyDir() {
        val filesDir = tmp.newFolder("files")
        val dir = BuiltInVoiceAssets.ttsInstalledDir(filesDir)
        dir.mkdirs()
        // 仅 tokens 不够
        File(dir, BuiltInVoiceAssets.TTS_TOKENS_MARKER).writeText("t")
        assertFalse(BuiltInVoiceAssets.isTtsInstalled(filesDir))
        // tokens + 至少一个非空 onnx → ready
        File(dir, "model.onnx").writeBytes(ByteArray(32) { 1 })
        assertTrue(DebugOpenSourcePaths.isTtsModelDirReady(dir))
        assertTrue(BuiltInVoiceAssets.isTtsInstalled(filesDir))
    }

    @Test
    fun installedPaths_underBuiltinVoice() {
        val filesDir = tmp.newFolder("f")
        assertTrue(
            BuiltInVoiceAssets.ttsInstalledDir(filesDir).absolutePath
                .contains("builtin-voice/tts/vits-melo-tts-zh_en")
        )
        assertTrue(
            BuiltInVoiceAssets.asrInstalledDir(filesDir).absolutePath
                .contains("builtin-voice/asr/")
        )
    }
}
