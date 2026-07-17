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
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuiltInLive2dAssetsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun pathLooksBuiltin_logicalAndInstalled() {
        assertTrue(BuiltInLive2dAssets.pathLooksBuiltin(BuiltInLive2dAssets.LOGICAL_PATH))
        assertTrue(BuiltInLive2dAssets.pathLooksBuiltin("asset://pet/live2d/Mao/Mao.model3.json"))
        assertTrue(
            BuiltInLive2dAssets.pathLooksBuiltin(
                "/data/user/0/app/files/builtin-live2d/Mao/Mao.model3.json"
            )
        )
        assertFalse(BuiltInLive2dAssets.pathLooksBuiltin("/sdcard/custom/foo.model3.json"))
        assertFalse(BuiltInLive2dAssets.pathLooksBuiltin(""))
    }

    @Test
    fun resolveIfPresent_installedVsLogical() {
        val root = tmp.root
        assertEquals(
            BuiltInLive2dAssets.LOGICAL_PATH,
            BuiltInLive2dAssets.resolveIfPresent(root, assumePackaged = true)
        )
        assertEquals(
            "",
            BuiltInLive2dAssets.resolveIfPresent(root, assumePackaged = false)
        )

        val model = BuiltInLive2dAssets.installedModelFile(root)
        model.parentFile!!.mkdirs()
        model.writeText("x")
        assertEquals(model.absolutePath, BuiltInLive2dAssets.resolveIfPresent(root))
        assertTrue(BuiltInLive2dAssets.isInstalled(root))
    }

    @Test
    fun displayController_logicalBuiltin_shellAsset() {
        val d = Live2dDisplayController.decide(BuiltInLive2dAssets.LOGICAL_PATH)
        assertEquals(Live2dDisplayController.Live2dDisplayMode.LIVE2D_SHELL, d.mode)
        assertEquals("live2d_builtin_asset", d.reason)
        assertTrue(d.shortLabel.contains("内置") || d.shortLabel.contains("Live2D"))
        assertTrue(d.model3FileUrl.startsWith("file:///android_asset/"))
    }

    @Test
    fun displayController_installedFile_shellReady() {
        val model = BuiltInLive2dAssets.installedModelFile(tmp.root)
        model.parentFile!!.mkdirs()
        model.writeText(
            """
            {
              "Version": 3,
              "FileReferences": {
                "Moc": "Mao.moc3",
                "Textures": ["Mao.2048/texture_00.png"]
              }
            }
            """.trimIndent()
        )
        val d = Live2dDisplayController.decide(model.absolutePath)
        assertEquals(Live2dDisplayController.Live2dDisplayMode.LIVE2D_SHELL, d.mode)
        assertEquals("live2d_shell_ready", d.reason)
        assertTrue(d.model3FileUrl.startsWith("file://"))
    }

    @Test
    fun constants_pointAtMaoAssets() {
        assertEquals("pet/live2d/Mao", BuiltInLive2dAssets.ASSET_ROOT)
        assertTrue(BuiltInLive2dAssets.MODEL3_ASSET.endsWith("Mao.model3.json"))
        assertTrue(BuiltInLive2dAssets.LOGICAL_PATH.startsWith("asset://"))
        assertTrue(BuiltInLive2dAssets.LICENSE_HINT.contains("live2d.com"))
    }
}
