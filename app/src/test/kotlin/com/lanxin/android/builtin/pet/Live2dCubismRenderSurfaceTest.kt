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

import com.lanxin.android.builtin.pet.domain.Live2dDisplayController
import com.lanxin.android.builtin.pet.domain.PetBridgeCommand
import com.lanxin.android.builtin.pet.domain.PetBridgeProtocol
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3：Cubism 真渲染表面契约（不启动 WebView；校验 assets 文案 + bridge 兼容）。
 */
class Live2dCubismRenderSurfaceTest {

    private fun readAsset(relative: String): String {
        val roots = listOf(
            File("app/src/main/assets"),
            File("src/main/assets"),
            File("../app/src/main/assets")
        )
        for (root in roots) {
            val f = File(root, relative)
            if (f.isFile) return f.readText(Charsets.UTF_8)
        }
        // unit test working dir may be module root
        val f = File("src/main/assets/$relative")
        if (f.isFile) return f.readText(Charsets.UTF_8)
        // fallback: walk from user.dir
        val ud = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(ud, "app/src/main/assets/$relative"),
            File(ud, "src/main/assets/$relative"),
            File(ud.parentFile, "app/src/main/assets/$relative")
        )
        for (c in candidates) {
            if (c.isFile) return c.readText(Charsets.UTF_8)
        }
        error("asset not found: $relative under ${ud.absolutePath}")
    }

    private fun assetExists(relative: String): Boolean {
        return runCatching { readAsset(relative); true }.getOrDefault(false) ||
            listOf(
                File("app/src/main/assets/$relative"),
                File("src/main/assets/$relative")
            ).any { it.isFile }
    }

    @Test
    fun cubismRuntimeLibs_present() {
        assertTrue(assetFile("pet/lib/live2dcubismcore.min.js").isFile)
        assertTrue(assetFile("pet/lib/pixi.min.js").isFile)
        assertTrue(assetFile("pet/lib/cubism4.min.js").isFile)
        assertTrue(assetFile("pet/lib/NOTICE.txt").isFile)
        val notice = assetFile("pet/lib/NOTICE.txt").readText()
        assertTrue(notice.contains("Cubism"))
        assertTrue(notice.contains("pixi-live2d-display") || notice.contains("MIT"))
    }

    @Test
    fun desktopPetHtml_wiresCubismAndFallback() {
        val html = assetFile("pet/desktop-pet.html").readText()
        assertTrue(html.contains("lib/live2dcubismcore.min.js"))
        assertTrue(html.contains("lib/pixi.min.js"))
        assertTrue(html.contains("lib/cubism4.min.js"))
        assertTrue(html.contains("LIVE2D_REAL"))
        assertTrue(html.contains("Live2DModel"))
        assertTrue(html.contains("ParamMouthOpenY") || html.contains("setMouthOpen"))
        assertTrue(html.contains("LOAD_LIVE2D"))
        assertTrue(html.contains("LIVE2D_SHELL"))
        assertTrue(html.contains("cubism_load_fail") || html.contains("activateShellFallback"))
        assertTrue(html.contains("__lanxinPetTeardown"))
        assertTrue(html.contains("SET_MUSIC_BEAT") || html.contains("beatLevel"))
    }

    @Test
    fun nativeDecision_stillShellMode_webReportsReal() {
        // Native 侧就绪仍为 LIVE2D_SHELL；Web 成功后回传 LIVE2D_REAL
        val f = File.createTempFile("Mao", ".model3.json")
        f.writeText(
            """{"Version":3,"FileReferences":{"Moc":"Mao.moc3","Textures":["t.png"]}}"""
        )
        f.deleteOnExit()
        val d = Live2dDisplayController.decide(f.absolutePath)
        assertEquals(Live2dDisplayController.Live2dDisplayMode.LIVE2D_SHELL, d.mode)
        assertTrue(d.shortLabel.contains("Live2D"))

        val wire = PetBridgeProtocol.encode(PetBridgeProtocol.loadLive2dMessage(d, 1L))
        assertTrue(wire.contains("command=LOAD_LIVE2D"))
        assertTrue(wire.contains("live2dMode=LIVE2D_SHELL"))

        val status = PetBridgeProtocol.decode(
            PetBridgeProtocol.encode(
                PetBridgeProtocol.live2dStatusMessage("LIVE2D_REAL", "cubism_ok", 2L)
            )
        )
        assertEquals(PetBridgeCommand.LIVE2D_STATUS, status.command)
        assertEquals("LIVE2D_REAL", status.payload[PetBridgeProtocol.KEY_LIVE2D_MODE])
        assertEquals("cubism_ok", status.payload[PetBridgeProtocol.KEY_LIVE2D_REASON])
    }

    @Test
    fun shellFallbackStatus_roundTrip() {
        val status = PetBridgeProtocol.decode(
            PetBridgeProtocol.encode(
                PetBridgeProtocol.live2dStatusMessage("LIVE2D_SHELL", "cubism_load_fail", 3L)
            )
        )
        assertEquals("LIVE2D_SHELL", status.payload[PetBridgeProtocol.KEY_LIVE2D_MODE])
        assertTrue(status.payload[PetBridgeProtocol.KEY_LIVE2D_REASON]!!.contains("cubism"))
    }

    @Test
    fun maoSample_stillPresentForRealLoad() {
        assertTrue(assetFile("pet/live2d/Mao/Mao.model3.json").isFile)
        assertTrue(assetFile("pet/live2d/Mao/Mao.moc3").isFile)
        assertTrue(assetFile("pet/live2d/Mao/Mao.2048/texture_00.png").isFile)
        val model3 = assetFile("pet/live2d/Mao/Mao.model3.json").readText()
        assertTrue(model3.contains("Mao.moc3"))
        assertFalse(model3.contains("妹居"))
    }

    private fun assetFile(relative: String): File {
        val ud = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(ud, "app/src/main/assets/$relative"),
            File(ud, "src/main/assets/$relative"),
            File(ud.parentFile, "app/src/main/assets/$relative"),
            File("app/src/main/assets/$relative"),
            File("src/main/assets/$relative")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("missing asset $relative cwd=${ud.absolutePath}")
    }
}
