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

import com.lanxin.android.builtin.pet.domain.BuiltInMusicAssets
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuiltInMusicAssetsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `musicDir under LanXin`() {
        val base = tmp.root
        val dir = BuiltInMusicAssets.musicDir(base)
        assertTrue(dir.path.replace('\\', '/').endsWith("LanXin/music"))
    }

    @Test
    fun `listTracks finds audio extensions`() {
        val music = File(tmp.root, "LanXin/music").apply { mkdirs() }
        File(music, "a.mp3").writeBytes(byteArrayOf(1, 2, 3))
        File(music, "b.wav").writeBytes(byteArrayOf(1, 2, 3))
        File(music, "c.txt").writeText("nope")
        File(music, "sub").mkdirs()
        File(music, "sub/d.ogg").writeBytes(byteArrayOf(9))
        val tracks = BuiltInMusicAssets.listTracks(music)
        assertEquals(3, tracks.size)
        assertTrue(tracks.any { it.name == "a.mp3" })
        assertTrue(tracks.any { it.name == "b.wav" })
        assertTrue(tracks.any { it.name == "d.ogg" })
    }

    @Test
    fun `isAudioFile rejects empty and non-audio`() {
        val empty = tmp.newFile("x.mp3")
        assertFalse(BuiltInMusicAssets.isAudioFile(empty))
        val ok = tmp.newFile("y.m4a")
        ok.writeBytes(byteArrayOf(1))
        assertTrue(BuiltInMusicAssets.isAudioFile(ok))
        val bad = tmp.newFile("z.bin")
        bad.writeBytes(byteArrayOf(1))
        assertFalse(BuiltInMusicAssets.isAudioFile(bad))
    }

    @Test
    fun `asset constants stable`() {
        assertEquals("pet/music", BuiltInMusicAssets.ASSET_ROOT)
        assertTrue(BuiltInMusicAssets.TEST_TRACK_ASSET.endsWith("test-loop.wav"))
        assertTrue(BuiltInMusicAssets.MUSIC_DIR_REL.startsWith("LanXin/"))
    }
}
