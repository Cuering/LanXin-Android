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

import com.lanxin.android.builtin.pet.domain.LanXinSafTree
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanXinSafTreeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun isContentUri_basic() {
        assertTrue(LanXinSafTree.isContentUri("content://com.android.externalstorage.documents/tree/primary%3ALanXin"))
        assertFalse(LanXinSafTree.isContentUri(""))
        assertFalse(LanXinSafTree.isContentUri("/storage/emulated/0/LanXin"))
        assertFalse(LanXinSafTree.isContentUri(null))
    }

    @Test
    fun relativeUnderLanXin_stripsRoot() {
        val lanXin = File(tmp.root, "LanXin").apply { mkdirs() }
        val img = File(lanXin, "backgrounds/a.jpg").apply {
            parentFile?.mkdirs()
            writeText("x")
        }
        assertEquals(
            "backgrounds/a.jpg",
            LanXinSafTree.relativeUnderLanXin(img.absolutePath, lanXin)
        )
    }

    @Test
    fun relativeUnderLanXin_outsideReturnsNull() {
        val lanXin = File(tmp.root, "LanXin").apply { mkdirs() }
        val outside = File(tmp.root, "other/b.jpg").apply {
            parentFile?.mkdirs()
            writeText("x")
        }
        assertNull(LanXinSafTree.relativeUnderLanXin(outside.absolutePath, lanXin))
    }

    @Test
    fun resolveUnderLanXin_absoluteAndRelative() {
        val lanXin = File(tmp.root, "LanXin").apply { mkdirs() }
        val img = File(lanXin, "backgrounds/a.jpg").apply {
            parentFile?.mkdirs()
            writeText("img")
        }
        assertEquals(img.canonicalFile, LanXinSafTree.resolveUnderLanXin(img.absolutePath, lanXin)?.canonicalFile)
        assertEquals(
            img.canonicalFile,
            LanXinSafTree.resolveUnderLanXin("backgrounds/a.jpg", lanXin)?.canonicalFile
        )
        assertEquals(
            img.canonicalFile,
            LanXinSafTree.resolveUnderLanXin("LanXin/backgrounds/a.jpg", lanXin)?.canonicalFile
        )
        assertNull(LanXinSafTree.resolveUnderLanXin("backgrounds/missing.jpg", lanXin))
    }

    @Test
    fun mirrorRelativeKey_matchesRelativeUnderLanXin() {
        val lanXin = File(tmp.root, "LanXin").apply { mkdirs() }
        val model = File(lanXin, "asr/zip/tokens.txt").apply {
            parentFile?.mkdirs()
            writeText("t")
        }
        assertEquals(
            "asr/zip/tokens.txt",
            LanXinSafTree.mirrorRelativeKey(model.absolutePath, lanXin)
        )
        assertNull(
            LanXinSafTree.mirrorRelativeKey(File(tmp.root, "out.txt").absolutePath, lanXin)
        )
    }
}
