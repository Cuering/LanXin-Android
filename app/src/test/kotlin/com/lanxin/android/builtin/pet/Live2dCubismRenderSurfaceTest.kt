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
    fun desktopPetHtml_softMusicDance_notViolentSway() {
        val html = assetFile("pet/desktop-pet.html").readText()
        // 慢舞路径：强平滑 + 限斜率 + softDanceOffset
        assertTrue(html.contains("advanceMusicBeatSmooth"))
        assertTrue(html.contains("softDanceOffset"))
        assertTrue(html.contains("musicBeatDisplay"))
        assertTrue(html.contains("tickCubismFrame"))
        // 禁止旧的剧烈 beat 位移系数（* beat * 5/6）
        assertFalse(html.contains("beat * 5"))
        assertFalse(html.contains("beat * 6"))
        assertFalse(html.contains("beat * 0.05"))
        // 限幅常量存在
        assertTrue(html.contains("2.8") || html.contains("maxStep"))
        assertTrue(html.contains("0.012") || html.contains("0.018"))
    }

    @Test
    fun desktopPetHtml_fullscreenStage_noCardWindow() {
        val html = assetFile("pet/desktop-pet.html").readText()
        // 全屏舞台：#stage / #live2d-shell 铺满，去掉小窗尺寸与白框
        assertTrue(html.contains("#stage"))
        assertTrue(html.contains("inset: 0") || html.contains("width: 100%"))
        assertTrue(html.contains("#live2d-shell"))
        assertFalse(html.contains("width: 140px; height: 160px"))
        assertTrue(html.contains("stageSize") || html.contains("layoutLive2dModel"))
        // 桌宠构图：偏下 + 更大 scale
        assertTrue(html.contains("0.62") || html.contains("h * 0.62"))
        assertTrue(html.contains("1.18") || html.contains("1.08"))
        assertTrue(html.contains("ResizeObserver") || html.contains("resizePixi"))
        assertTrue(html.contains("background: transparent"))
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
