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

import com.lanxin.android.builtin.pet.domain.DebugOpenSourcePaths
import com.lanxin.android.builtin.pet.domain.MeijuDebugPaths
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 路径解析：配置失效时回落到 openSourceBaseDir（修复开关开了却找不到模型）。
 */
class MeijuDebugPathsResolveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun pathExists_stubAndMissing() {
        assertTrue(MeijuDebugPaths.pathExists("stub://demo"))
        assertFalse(MeijuDebugPaths.pathExists(""))
        assertFalse(MeijuDebugPaths.pathExists("/no/such/path"))
        val f = tmp.newFile("x.bin")
        assertTrue(MeijuDebugPaths.pathExists(f.absolutePath))
    }

    @Test
    fun resolveAsr_fallsBackWhenConfiguredMissing() {
        val filesDir = tmp.newFolder("files")
        val openBase = tmp.newFolder("external")
        val asrDir = File(openBase, DebugOpenSourcePaths.ASR_ZIPFORMER_14M_REL).apply {
            mkdirs()
            File(this, "tokens.txt").writeText("t")
            File(this, "encoder.onnx").writeText("e")
        }
        assertTrue(DebugOpenSourcePaths.isModelDirReady(asrDir))

        val resolved = MeijuDebugPaths.resolveAsrIfPresent(
            filesDir = filesDir,
            configured = "/storage/emulated/0/LanXin/asr/missing",
            openSourceBaseDir = openBase
        )
        assertEquals(asrDir.absolutePath, resolved)
    }

    @Test
    fun resolveAsr_keepsConfiguredWhenExists() {
        val filesDir = tmp.newFolder("files2")
        val openBase = tmp.newFolder("external2")
        val configured = tmp.newFolder("custom-asr").apply {
            File(this, "tokens.txt").writeText("t")
            File(this, "encoder.onnx").writeText("e")
        }
        val resolved = MeijuDebugPaths.resolveAsrIfPresent(
            filesDir = filesDir,
            configured = configured.absolutePath,
            openSourceBaseDir = openBase
        )
        assertEquals(configured.absolutePath, resolved)
    }

    @Test
    fun resolveLocalLlm_fallsBackToOpenSource() {
        val filesDir = tmp.newFolder("files3")
        val openBase = tmp.newFolder("external3")
        val llmDir = File(openBase, DebugOpenSourcePaths.LOCAL_LLM_LIGHT_DIR_REL).apply {
            mkdirs()
            File(this, "llm.mnn").writeText("mnn")
        }
        assertTrue(DebugOpenSourcePaths.isLocalLlmDirReady(llmDir))

        val resolved = MeijuDebugPaths.resolveLocalLlmIfPresent(
            filesDir = filesDir,
            configured = "",
            openSourceBaseDir = openBase
        )
        assertEquals(llmDir.absolutePath, resolved)
    }

    @Test
    fun resolveTts_fallsBackWhenConfiguredMissing() {
        val filesDir = tmp.newFolder("files4")
        val openBase = tmp.newFolder("external4")
        val ttsDir = File(openBase, DebugOpenSourcePaths.TTS_MATCHA_BAKER_REL).apply {
            mkdirs()
            File(this, "model.onnx").writeText("o")
        }
        val resolved = MeijuDebugPaths.resolveTtsModelDirIfPresent(
            filesDir = filesDir,
            configured = "/missing/tts",
            openSourceBaseDir = openBase
        )
        assertEquals(ttsDir.absolutePath, resolved)
    }
}
