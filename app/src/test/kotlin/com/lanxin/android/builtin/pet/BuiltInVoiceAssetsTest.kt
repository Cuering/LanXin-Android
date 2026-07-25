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
    fun ttsAssetRoot_pointsAtMatchaBaker() {
        assertEquals("voice/tts/matcha-icefall-zh-baker", BuiltInVoiceAssets.TTS_ASSET_ROOT)
        assertEquals("vocos-22khz-univ.onnx", BuiltInVoiceAssets.TTS_VOCODER_MARKER)
    }

    @Test
    fun isTtsInstalled_requiresVocoderReadyDir() {
        val filesDir = tmp.newFolder("files")
        val dir = BuiltInVoiceAssets.ttsInstalledDir(filesDir)
        dir.mkdirs()
        File(dir, "model-steps-3.onnx").writeText("m")
        File(dir, "tokens.txt").writeText("t")
        assertFalse(BuiltInVoiceAssets.isTtsInstalled(filesDir))
        File(dir, BuiltInVoiceAssets.TTS_VOCODER_MARKER).writeText("v")
        assertTrue(DebugOpenSourcePaths.isTtsModelDirReady(dir))
        assertTrue(BuiltInVoiceAssets.isTtsInstalled(filesDir))
    }

    @Test
    fun installedPaths_underBuiltinVoice() {
        val filesDir = tmp.newFolder("f")
        assertTrue(
            BuiltInVoiceAssets.ttsInstalledDir(filesDir).absolutePath
                .contains("builtin-voice/tts/matcha-icefall-zh-baker")
        )
        assertTrue(
            BuiltInVoiceAssets.asrInstalledDir(filesDir).absolutePath
                .contains("builtin-voice/asr/")
        )
    }
}
