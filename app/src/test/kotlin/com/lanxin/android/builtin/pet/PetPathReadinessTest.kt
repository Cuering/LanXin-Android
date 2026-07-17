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

import com.lanxin.android.builtin.pet.domain.BuiltInLive2dAssets
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
    fun blankLive2d_notReady_withBuiltinHint() {
        val c = PetPathReadiness.check(PetPathReadiness.Kind.LIVE2D, "")
        assertFalse(c.ready)
        assertEquals("未就绪", c.label)
        assertTrue(
            c.detail.contains("Mao") || c.detail.contains("内置") ||
                c.detail.contains("下载") || c.detail.contains("fetch-debug-assets")
        )
    }

    @Test
    fun blankAsr_notReady_withFetchHint() {
        val c = PetPathReadiness.check(PetPathReadiness.Kind.ASR, "")
        assertFalse(c.ready)
        assertTrue(
            c.detail.contains("一键下载") || c.detail.contains("fetch-debug-assets") ||
                c.detail.contains("debug-assets")
        )
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
    fun builtinLogicalPath_readyAsSample() {
        val c = PetPathReadiness.check(
            PetPathReadiness.Kind.LIVE2D,
            BuiltInLive2dAssets.LOGICAL_PATH
        )
        assertTrue(c.ready)
        assertEquals("已就绪（内置示例）", c.label)
        assertTrue(c.detail.contains("Sample") || c.detail.contains("Mao") || c.detail.contains("许可"))
    }

    @Test
    fun installedBuiltinFile_readyAsSample() {
        val root = tmp.root
        val model = BuiltInLive2dAssets.installedModelFile(root)
        model.parentFile!!.mkdirs()
        model.writeText("""{"Version":3,"FileReferences":{"Moc":"Mao.moc3"}}""")
        val c = PetPathReadiness.check(PetPathReadiness.Kind.LIVE2D, model.absolutePath)
        assertTrue(c.ready)
        assertEquals("已就绪（内置示例）", c.label)
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
        assertTrue(
            s.contains("下载") || s.contains("fetch-debug-assets")
        )
        assertTrue(s.contains("Live2D"))
    }

    @Test
    fun resolveLive2d_prefersInstalledBuiltinOverDebugAssets() {
        val root = tmp.root
        val installed = BuiltInLive2dAssets.installedModelFile(root)
        installed.parentFile!!.mkdirs()
        installed.writeText("builtin")
        val mao = File(root, DebugOpenSourcePaths.LIVE2D_MAO_MODEL3_REL)
        mao.parentFile!!.mkdirs()
        mao.writeText("debug")

        val path = MeijuDebugPaths.resolveLive2dIfPresent(root, "")
        assertEquals(installed.absolutePath, path)
        assertEquals(
            MeijuDebugPaths.ResourceSource.BUILTIN_SAMPLE,
            MeijuDebugPaths.classifySource(true, "", path)
        )
    }

    @Test
    fun resolveLive2d_debugAssetsBeforeMeiju_whenNoBuiltin() {
        val root = tmp.root
        val mao = File(root, DebugOpenSourcePaths.LIVE2D_MAO_MODEL3_REL)
        mao.parentFile!!.mkdirs()
        mao.writeText("{}")
        val meiju = File(root, MeijuDebugPaths.L2D_MODEL3_REL)
        meiju.parentFile!!.mkdirs()
        meiju.writeText("meiju")

        val path = MeijuDebugPaths.resolveLive2dIfPresent(
            filesDir = root,
            configured = "",
            preferBuiltinLogical = false,
            allowMeijuRef = true
        )
        assertEquals(mao.absolutePath, path)
        assertEquals(
            MeijuDebugPaths.ResourceSource.DEBUG_OPEN_SOURCE,
            MeijuDebugPaths.classifySource(true, "", path)
        )
    }

    @Test
    fun resolveLive2d_fallsBackToLogicalBuiltin() {
        val root = tmp.root
        val path = MeijuDebugPaths.resolveLive2dIfPresent(root, "")
        assertEquals(BuiltInLive2dAssets.LOGICAL_PATH, path)
        assertEquals(
            MeijuDebugPaths.ResourceSource.BUILTIN_SAMPLE,
            MeijuDebugPaths.classifySource(false, "", path)
        )
    }

    @Test
    fun resolver_debugUsesOpenSourceForAsr() {
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
    fun release_live2dDefaultsToBuiltin_notOpenSourceAsr() {
        val root = tmp.root
        val mao = File(root, DebugOpenSourcePaths.LIVE2D_MAO_MODEL3_REL)
        mao.parentFile!!.mkdirs()
        mao.writeText("{}")
        val asrDir = File(root, DebugOpenSourcePaths.ASR_ZIPFORMER_14M_REL)
        asrDir.mkdirs()
        File(asrDir, "encoder.onnx").writeText("x")

        val resolved = PetResourceResolver.resolve(
            filesDir = root,
            pet = PetConfig(),
            tts = TtsConfig(),
            asr = AsrConfig(),
            isDebug = false
        )
        // Live2D：debug-assets 仍可被 resolveLive2d 命中（用户可能 adb push），
        // 但无 files 时回落 logical；此处 debug-assets 有文件 → 开源包路径
        // 注意：priority 是 builtin installed → debug-assets → logical
        assertEquals(mao.absolutePath, resolved.live2dModelPath)
        assertEquals("", resolved.asrModelPath)
        assertEquals(MeijuDebugPaths.ResourceSource.PLACEHOLDER, resolved.asrSource)
    }

    @Test
    fun release_emptyFilesDir_live2dLogicalBuiltin() {
        val root = tmp.root
        val resolved = PetResourceResolver.resolve(
            filesDir = root,
            pet = PetConfig(),
            tts = TtsConfig(),
            asr = AsrConfig(),
            isDebug = false
        )
        assertEquals(BuiltInLive2dAssets.LOGICAL_PATH, resolved.live2dModelPath)
        assertEquals(
            MeijuDebugPaths.ResourceSource.BUILTIN_SAMPLE,
            resolved.live2dSource
        )
        assertEquals("", resolved.asrModelPath)
    }
}
