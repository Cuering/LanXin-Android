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
import com.lanxin.android.builtin.pet.domain.Live2dModelCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Live2dModelCatalogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun listModels_alwaysIncludesBuiltin() {
        val filesDir = tmp.newFolder("files")
        val lanXin = tmp.newFolder("LanXin")
        val list = Live2dModelCatalog.listModels(
            configuredPath = "",
            resolvedPath = BuiltInLive2dAssets.LOGICAL_PATH,
            filesDir = filesDir,
            lanXinDir = lanXin
        )
        assertTrue(list.any { it.id == Live2dModelCatalog.BUILTIN_ID })
        assertTrue(list.first { it.id == Live2dModelCatalog.BUILTIN_ID }.selected)
        assertEquals(Live2dModelCatalog.BUILTIN_DISPLAY_NAME, list.first().displayName)
    }

    @Test
    fun listModels_scansLanXinLive2d_caseInsensitiveDir() {
        val filesDir = tmp.newFolder("files")
        val lanXin = tmp.newFolder("LanXin")
        val mao = File(lanXin, "live2d/mao").apply { mkdirs() }
        val model = File(mao, "Mao.model3.json").apply { writeText("""{"Version":3}""") }
        File(mao, "Mao.moc3").writeText("x")

        val custom = File(lanXin, "live2d/MyPet").apply { mkdirs() }
        val customModel = File(custom, "MyPet.model3.json").apply { writeText("{}") }

        val list = Live2dModelCatalog.listModels(
            configuredPath = customModel.absolutePath,
            resolvedPath = customModel.absolutePath,
            filesDir = filesDir,
            lanXinDir = lanXin
        )
        assertTrue(list.any { it.model3Path == model.absolutePath })
        assertTrue(list.any { it.model3Path == customModel.absolutePath && it.selected })
        assertTrue(list.any { it.displayName.contains("Mao") })
        assertTrue(list.any { it.displayName == "MyPet" })
    }

    @Test
    fun listModels_scansLegacyDebugAssets() {
        val filesDir = tmp.newFolder("files")
        val lanXin = tmp.newFolder("LanXin")
        val legacy = File(filesDir, "debug-assets/live2d/OldPet").apply { mkdirs() }
        val model = File(legacy, "OldPet.model3.json").apply { writeText("{}") }

        val list = Live2dModelCatalog.listModels(
            configuredPath = model.absolutePath,
            resolvedPath = model.absolutePath,
            filesDir = filesDir,
            lanXinDir = lanXin
        )
        assertTrue(list.any { it.model3Path == model.absolutePath && it.selected })
        assertTrue(list.any { it.displayName.contains("旧") })
    }

    @Test
    fun listModels_ignoresEmptyDirsWithoutModel3() {
        val filesDir = tmp.newFolder("files")
        val lanXin = tmp.newFolder("LanXin")
        File(lanXin, "live2d/empty").mkdirs()
        File(File(lanXin, "live2d/empty"), "readme.txt").writeText("nope")

        val list = Live2dModelCatalog.listModels(
            configuredPath = "",
            resolvedPath = "",
            filesDir = filesDir,
            lanXinDir = lanXin
        )
        assertEquals(1, list.size) // only builtin
        assertEquals(Live2dModelCatalog.BUILTIN_ID, list.single().id)
    }

    @Test
    fun importModelTree_copiesToLanXinLive2d() {
        val lanXin = tmp.newFolder("LanXin")
        val src = tmp.newFolder("srcModel")
        File(src, "Foo.model3.json").writeText("{}")
        File(src, "Foo.moc3").writeText("bin")

        val dest = Live2dModelCatalog.importModelTree(lanXin, src, preferredName = "Foo")
        assertTrue(dest.isFile)
        assertTrue(dest.absolutePath.contains("/live2d/Foo/"))
        assertTrue(File(dest.parentFile!!, "Foo.moc3").isFile)

        val list = Live2dModelCatalog.listModels(
            configuredPath = dest.absolutePath,
            resolvedPath = dest.absolutePath,
            filesDir = tmp.newFolder("files2"),
            lanXinDir = lanXin
        )
        assertTrue(list.any { it.displayName == "Foo" && it.selected })
    }

    @Test
    fun importModel3File_uniqueNameOnCollision() {
        val lanXin = tmp.newFolder("LanXin")
        File(lanXin, "live2d/Bar").mkdirs()
        File(File(lanXin, "live2d/Bar"), "Bar.model3.json").writeText("old")

        val src = tmp.newFile("Bar.model3.json").apply { writeText("new") }
        val dest = Live2dModelCatalog.importModel3File(lanXin, src, preferredName = "Bar")
        assertTrue(dest.absolutePath.contains("/live2d/Bar_2/"))
        assertEquals("new", dest.readText())
    }

    @Test
    fun resolveSwitchPath_builtinUsesInstalledOrNull() {
        val filesDir = tmp.newFolder("files")
        val entry = Live2dModelCatalog.ModelEntry(
            id = Live2dModelCatalog.BUILTIN_ID,
            displayName = Live2dModelCatalog.BUILTIN_DISPLAY_NAME,
            model3Path = BuiltInLive2dAssets.LOGICAL_PATH,
            source = Live2dModelCatalog.Source.BUILTIN,
            ready = true,
            shortPath = "asset"
        )
        assertEquals(null, Live2dModelCatalog.resolveSwitchPath(entry, filesDir))

        val installed = BuiltInLive2dAssets.installedModelFile(filesDir)
        installed.parentFile!!.mkdirs()
        installed.writeText("x")
        assertEquals(
            installed.absolutePath,
            Live2dModelCatalog.resolveSwitchPath(entry, filesDir)
        )
    }

    @Test
    fun live2dRootDisplay_underLanXin() {
        val lanXin = File("/storage/emulated/0/LanXin")
        assertEquals(
            "/storage/emulated/0/LanXin/live2d",
            Live2dModelCatalog.live2dRootDisplay(lanXin)
        )
    }

    @Test
    fun currentDisplayName_prefersSelected() {
        val models = listOf(
            Live2dModelCatalog.ModelEntry(
                id = "a",
                displayName = "A",
                model3Path = "/a",
                source = Live2dModelCatalog.Source.LANXIN,
                ready = true,
                shortPath = "a",
                selected = false
            ),
            Live2dModelCatalog.ModelEntry(
                id = "b",
                displayName = "B",
                model3Path = "/b",
                source = Live2dModelCatalog.Source.LANXIN,
                ready = true,
                shortPath = "b",
                selected = true
            )
        )
        assertEquals("B", Live2dModelCatalog.currentDisplayName(models, "/b", "/b"))
        assertNotNull(Live2dModelCatalog.currentDisplayName(emptyList(), "", ""))
        assertFalse(
            Live2dModelCatalog.currentDisplayName(emptyList(), "", "").isBlank()
        )
    }
}
