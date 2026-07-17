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
import com.lanxin.android.builtin.pet.domain.Live2dDisplayController
import com.lanxin.android.builtin.pet.domain.PetBridgeCommand
import com.lanxin.android.builtin.pet.domain.PetBridgeProtocol
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Live2dDisplayControllerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun blankPath_placeholder() {
        val d = Live2dDisplayController.decide("")
        assertEquals(Live2dDisplayController.Live2dDisplayMode.PLACEHOLDER, d.mode)
        assertEquals("live2d_path_empty", d.reason)
        assertEquals("占位", d.shortLabel)
        assertTrue(d.model3FileUrl.isEmpty())
    }

    @Test
    fun stubPath_placeholder() {
        val d = Live2dDisplayController.decide("stub://mao")
        assertEquals(Live2dDisplayController.Live2dDisplayMode.PLACEHOLDER, d.mode)
        assertEquals("live2d_stub", d.reason)
    }

    @Test
    fun builtinLogical_shellUsesAssetUrl() {
        val d = Live2dDisplayController.decide(BuiltInLive2dAssets.LOGICAL_PATH)
        assertEquals(Live2dDisplayController.Live2dDisplayMode.LIVE2D_SHELL, d.mode)
        assertEquals("live2d_builtin_asset", d.reason)
        assertTrue(d.shortLabel.contains("内置"))
        assertTrue(d.model3FileUrl.contains("android_asset"))
        assertTrue(d.model3FileUrl.contains("Mao.model3.json"))
    }

    @Test
    fun missingFile_fallback() {
        val missing = File(tmp.root, "nope.model3.json").absolutePath
        val d = Live2dDisplayController.decide(missing)
        assertEquals(Live2dDisplayController.Live2dDisplayMode.FALLBACK, d.mode)
        assertEquals("live2d_file_missing", d.reason)
        assertTrue(d.shortLabel.contains("降级"))
    }

    @Test
    fun validModel3_shellMode() {
        val f = tmp.newFile("Mao.model3.json")
        f.writeText(
            """
            {
              "Version": 3,
              "FileReferences": {
                "Moc": "Mao.moc3",
                "Textures": ["texture_00.png"]
              }
            }
            """.trimIndent()
        )
        val d = Live2dDisplayController.decide(f.absolutePath)
        assertEquals(Live2dDisplayController.Live2dDisplayMode.LIVE2D_SHELL, d.mode)
        assertEquals("live2d_shell_ready", d.reason)
        assertEquals("Live2D 壳", d.shortLabel)
        assertTrue(d.model3FileUrl.startsWith("file://"))
        assertTrue(d.model3FileUrl.contains("Mao.model3.json"))
        assertTrue(d.modelDirFileUrl.startsWith("file://"))
    }

    @Test
    fun invalidJsonContent_fallback() {
        val f = tmp.newFile("broken.model3.json")
        f.writeText("{ \"hello\": 1 }")
        val d = Live2dDisplayController.decide(f.absolutePath)
        assertEquals(Live2dDisplayController.Live2dDisplayMode.FALLBACK, d.mode)
        assertEquals("live2d_model3_invalid", d.reason)
    }

    @Test
    fun looksLikeModel3_acceptsMocHint() {
        val f = tmp.newFile("x.json")
        f.writeText("""{"Version":3,"FileReferences":{"Moc":"a.moc3"}}""")
        assertTrue(Live2dDisplayController.looksLikeModel3(f))
    }

    @Test
    fun looksLikeModel3_rejectsEmpty() {
        val f = tmp.newFile("empty.json")
        f.writeText("")
        assertFalse(Live2dDisplayController.looksLikeModel3(f))
    }

    @Test
    fun toFileUrl_prefixesFileScheme() {
        val f = File("/data/user/0/app/files/Mao.model3.json")
        assertEquals("file:///data/user/0/app/files/Mao.model3.json", Live2dDisplayController.toFileUrl(f))
    }

    @Test
    fun loadLive2dMessage_encodesModeAndUrls() {
        val f = tmp.newFile("Mao.model3.json")
        f.writeText("""{"Version":3,"FileReferences":{"Moc":"a.moc3"}}""")
        val d = Live2dDisplayController.decide(f.absolutePath)
        val wire = PetBridgeProtocol.encode(PetBridgeProtocol.loadLive2dMessage(d, timestampMs = 9L))
        assertTrue(wire.contains("command=LOAD_LIVE2D"))
        assertTrue(wire.contains("live2dMode=LIVE2D_SHELL"))
        assertTrue(wire.contains("live2dReason=live2d_shell_ready"))
        assertTrue(wire.contains("live2dFileUrl=file://"))
        val decoded = PetBridgeProtocol.decode(wire)
        assertEquals(PetBridgeCommand.LOAD_LIVE2D, decoded.command)
        assertEquals("LIVE2D_SHELL", decoded.payload[PetBridgeProtocol.KEY_LIVE2D_MODE])
    }

    @Test
    fun live2dStatusMessage_roundTrip() {
        val wire = PetBridgeProtocol.encode(
            PetBridgeProtocol.live2dStatusMessage("FALLBACK", "live2d_load_fail", 3L)
        )
        val msg = PetBridgeProtocol.decode(wire)
        assertEquals(PetBridgeCommand.LIVE2D_STATUS, msg.command)
        assertEquals("FALLBACK", msg.payload[PetBridgeProtocol.KEY_LIVE2D_MODE])
        assertEquals("live2d_load_fail", msg.payload[PetBridgeProtocol.KEY_LIVE2D_REASON])
    }

    @Test
    fun readinessDetail_forModes() {
        assertTrue(
            Live2dDisplayController.readinessDetailForMode(
                Live2dDisplayController.Live2dDisplayMode.LIVE2D_SHELL
            ).contains("渲染壳")
        )
    }
}
