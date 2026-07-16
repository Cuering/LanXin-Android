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
import com.lanxin.android.builtin.pet.domain.PetPathReadiness
import com.lanxin.android.builtin.pet.domain.PetResourceResolver
import com.lanxin.android.builtin.pet.domain.PetConfig
import com.lanxin.android.builtin.voice.domain.AsrConfig
import com.lanxin.android.builtin.voice.domain.TtsConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PetPathReadinessTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun blankPath_notReady_withFetchHint() {
        val c = PetPathReadiness.check(PetPathReadiness.Kind.LIVE2D, "")
        assertFalse(c.ready)
        assertEquals("未就绪", c.label)
        assertTrue(c.detail.contains("fetch-debug-assets"))
    }

    @Test
    fun stubPath_ready() {
        val c = PetPathReadiness.check(PetPathReadiness.Kind.ASR, "stub://demo")
        assertTrue(c.ready)
        assertTrue(c.label.contains("stub"))
    }

    @Test
    fun existingLive2dFile_ready() {
        val f = tmp.newFile("Mao.model3.json")
        val c = PetPathReadiness.check(PetPathReadiness.Kind.LIVE2D, f.absolutePath)
        assertTrue(c.ready)
        assertEquals("已就绪", c.label)
    }

    @Test
    fun missingConfiguredPath_invalid() {
        val c = PetPathReadiness.check(
            PetPathReadiness.Kind.TTS,
            File(tmp.root, "no-such-dir").absolutePath
        )
        assertFalse(c.ready)
        assertEquals("路径无效", c.label)
    }

    @Test
    fun emptyDir_notReadyForAsr() {
        val d = tmp.newFolder("empty-asr")
        val c = PetPathReadiness.check(PetPathReadiness.Kind.ASR, d.absolutePath)
        assertFalse(c.ready)
    }

    @Test
    fun nonEmptyDir_readyForAsr() {
        val d = tmp.newFolder("asr")
        File(d, "tokens.txt").writeText("x")
        val c = PetPathReadiness.check(PetPathReadiness.Kind.ASR, d.absolutePath)
        assertTrue(c.ready)
    }

    @Test
    fun summary_includesFetchWhenMissing() {
        val live = PetPathReadiness.check(PetPathReadiness.Kind.LIVE2D, "")
        val asr = PetPathReadiness.check(PetPathReadiness.Kind.ASR, "")
        val tts = PetPathReadiness.check(PetPathReadiness.Kind.TTS, "")
        val s = PetPathReadiness.summaryMessage(live, asr, tts)
        assertTrue(s.contains("fetch-debug-assets"))
        assertTrue(s.contains("Live2D"))
    }

    @Test
    fun openSourceResolve_prefersDebugAssetsOverMeiju() {
        val root = tmp.root
        val mao = File(root, DebugOpenSourcePaths.LIVE2D_MAO_MODEL3_REL)
        mao.parentFile!!.mkdirs()
        mao.writeText("{}")
        val meiju = File(root, MeijuDebugPaths.L2D_MODEL3_REL)
        meiju.parentFile!!.mkdirs()
        meiju.writeText("meiju")

        val path = MeijuDebugPaths.resolveLive2dIfPresent(root, "")
        assertEquals(mao.absolutePath, path)
        assertEquals(
            MeijuDebugPaths.ResourceSource.DEBUG_OPEN_SOURCE,
            MeijuDebugPaths.classifySource(true, "", path)
        )
    }

    @Test
    fun resolver_debugUsesOpenSource() {
        val root = tmp.root
        val asrDir = File(root, DebugOpenSourcePaths.ASR_ZIPFORMER_14M_REL)
        asrDir.mkdirs()
        File(asrDir, "encoder.onnx").writeText("x")

        val resolved = PetResourceResolver.resolve(
            filesDir = root,
            pet = PetConfig(),
            tts = TtsConfig(),
            asr = AsrConfig(),
            isDebug = true
        )
        assertEquals(asrDir.absolutePath, resolved.asrModelPath)
        assertEquals(
            MeijuDebugPaths.ResourceSource.DEBUG_OPEN_SOURCE,
            resolved.asrSource
        )
    }

    @Test
    fun release_doesNotAutoPickOpenSource() {
        val root = tmp.root
        val mao = File(root, DebugOpenSourcePaths.LIVE2D_MAO_MODEL3_REL)
        mao.parentFile!!.mkdirs()
        mao.writeText("{}")

        val resolved = PetResourceResolver.resolve(
            filesDir = root,
            pet = PetConfig(),
            tts = TtsConfig(),
            asr = AsrConfig(),
            isDebug = false
        )
        assertEquals("", resolved.live2dModelPath)
        assertEquals(MeijuDebugPaths.ResourceSource.PLACEHOLDER, resolved.live2dSource)
    }
}
