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

import com.lanxin.android.builtin.pet.domain.CompanionBackgrounds
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CompanionBackgroundsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `backgroundsDir under LanXin`() {
        val dir = CompanionBackgrounds.backgroundsDir(tmp.root)
        assertTrue(dir.path.replace('\\', '/').endsWith("LanXin/backgrounds"))
    }

    @Test
    fun `presets include default sakura`() {
        assertTrue(CompanionBackgrounds.PRESETS.any { it.id == CompanionBackgrounds.DEFAULT_ID })
        assertEquals("sakura", CompanionBackgrounds.DEFAULT_ID)
        assertTrue(CompanionBackgrounds.isKnownPreset("sakura"))
        assertFalse(CompanionBackgrounds.isKnownPreset("custom"))
        assertFalse(CompanionBackgrounds.isKnownPreset(""))
    }

    @Test
    fun `resolve preset by id`() {
        val r = CompanionBackgrounds.resolve("mint", "")
        assertTrue(r is CompanionBackgrounds.Resolved.Preset)
        assertEquals("mint", (r as CompanionBackgrounds.Resolved.Preset).preset.id)
    }

    @Test
    fun `resolve unknown falls back to default`() {
        val r = CompanionBackgrounds.resolve("nope", "")
        assertTrue(r is CompanionBackgrounds.Resolved.Preset)
        assertEquals(CompanionBackgrounds.DEFAULT_ID, (r as CompanionBackgrounds.Resolved.Preset).preset.id)
    }

    @Test
    fun `resolve custom image when file valid`() {
        val img = tmp.newFile("bg.jpg")
        img.writeBytes(byteArrayOf(1, 2, 3, 4))
        val r = CompanionBackgrounds.resolve(CompanionBackgrounds.CUSTOM_ID, img.absolutePath)
        assertTrue(r is CompanionBackgrounds.Resolved.Image)
        assertEquals(img.absolutePath, (r as CompanionBackgrounds.Resolved.Image).path)
    }

    @Test
    fun `resolve custom falls back when missing`() {
        val r = CompanionBackgrounds.resolve(CompanionBackgrounds.CUSTOM_ID, "/no/such/bg.png")
        assertTrue(r is CompanionBackgrounds.Resolved.Preset)
        assertEquals(CompanionBackgrounds.DEFAULT_ID, (r as CompanionBackgrounds.Resolved.Preset).preset.id)
    }

    @Test
    fun `listImages finds image extensions`() {
        val dir = File(tmp.root, "LanXin/backgrounds").apply { mkdirs() }
        File(dir, "a.jpg").writeBytes(byteArrayOf(1))
        File(dir, "b.png").writeBytes(byteArrayOf(1))
        File(dir, "c.txt").writeText("nope")
        File(dir, "empty.webp").writeBytes(byteArrayOf())
        val list = CompanionBackgrounds.listImages(dir)
        assertEquals(2, list.size)
        assertTrue(list.any { it.name == "a.jpg" })
        assertTrue(list.any { it.name == "b.png" })
    }

    @Test
    fun `isImageFile rejects empty and non-image`() {
        val empty = tmp.newFile("x.png")
        assertFalse(CompanionBackgrounds.isImageFile(empty))
        val ok = tmp.newFile("y.webp")
        ok.writeBytes(byteArrayOf(1))
        assertTrue(CompanionBackgrounds.isImageFile(ok))
        val bad = tmp.newFile("z.bin")
        bad.writeBytes(byteArrayOf(1))
        assertFalse(CompanionBackgrounds.isImageFile(bad))
    }

    @Test
    fun `resolve relative path under lanXinDir`() {
        val lanXin = File(tmp.root, "LanXin").apply { mkdirs() }
        val img = File(lanXin, "backgrounds/rel.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val r = CompanionBackgrounds.resolve(
            CompanionBackgrounds.CUSTOM_ID,
            "backgrounds/rel.jpg",
            lanXinDir = lanXin
        )
        assertTrue(r is CompanionBackgrounds.Resolved.Image)
        assertEquals(img.absolutePath, (r as CompanionBackgrounds.Resolved.Image).path)
    }

    @Test
    fun `storePathKey prefers relative`() {
        val lanXin = File(tmp.root, "LanXin").apply { mkdirs() }
        val img = File(lanXin, "backgrounds/k.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        assertEquals(
            "backgrounds/k.jpg",
            CompanionBackgrounds.storePathKey(img.absolutePath, lanXin)
        )
    }
}
