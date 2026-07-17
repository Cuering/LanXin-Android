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

package com.lanxin.android.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PathImportHelperTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun shortSummary_blank() {
        assertEquals("未选择", PathImportHelper.shortSummary(""))
        assertEquals("未选择", PathImportHelper.shortSummary("   "))
    }

    @Test
    fun shortSummary_stub() {
        assertEquals("stub://demo", PathImportHelper.shortSummary("stub://demo"))
    }

    @Test
    fun shortSummary_shortPath() {
        val p = "/data/a/b.model3.json"
        assertEquals(p, PathImportHelper.shortSummary(p, maxTail = 80))
    }

    @Test
    fun shortSummary_longPath_hasNameAndEllipsis() {
        val p = "/data/user/0/com.lanxin.android/files/user-picked/live2d/import_1/Mao.model3.json"
        val s = PathImportHelper.shortSummary(p, maxTail = 20)
        assertTrue(s.contains("Mao.model3.json"))
        assertTrue(s.contains("…"))
    }

    @Test
    fun kindRoot_and_newImportDir() {
        val root = tmp.root
        val dir = PathImportHelper.newImportDir(root, PathImportHelper.Kind.ASR, stampMs = 42L)
        assertEquals(
            File(root, "user-picked/asr/import_42").absolutePath,
            dir.absolutePath
        )
    }

    @Test
    fun findModel3Json_nested() {
        val root = tmp.newFolder("model")
        val sub = File(root, "Mao").apply { mkdirs() }
        val model = File(sub, "Mao.model3.json").apply { writeText("{}") }
        File(sub, "Mao.moc3").apply { writeText("x") }
        assertEquals(model.absolutePath, PathImportHelper.findModel3Json(root)?.absolutePath)
    }

    @Test
    fun findModel3Json_missing() {
        val root = tmp.newFolder("empty")
        File(root, "readme.txt").writeText("hi")
        assertNull(PathImportHelper.findModel3Json(root))
    }

    @Test
    fun sanitizeFileName_stripsIllegal() {
        assertEquals("a_b.json", PathImportHelper.sanitizeFileName("a/b.json"))
        assertTrue(PathImportHelper.sanitizeFileName("  ").isNotBlank())
    }
}
