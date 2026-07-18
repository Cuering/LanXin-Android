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

package com.lanxin.android.builtin.platform

import com.lanxin.android.builtin.pet.domain.CompanionBackgrounds
import com.lanxin.android.builtin.pet.domain.MoodTagMapper
import com.lanxin.android.builtin.platform.domain.LocalSceneClassifier
import com.lanxin.android.builtin.platform.domain.SceneFeatures
import com.lanxin.android.builtin.platform.domain.SceneLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地启发式场景分类 + 仅映射现有背景/mood。
 */
class LocalSceneClassifierTest {

    private val knownBg = CompanionBackgrounds.PRESETS.map { it.id }.toSet()
    private val moods = MoodTagMapper.ALLOWED_MOODS.toSet()

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    @Test
    fun `featuresFromArgb empty is neutral`() {
        val f = LocalSceneClassifier.featuresFromArgb(intArrayOf())
        assertEquals(0.5f, f.avgBrightness, 0.001f)
    }

    @Test
    fun `night from dark pixels`() {
        val pixels = IntArray(64) { argb(8, 8, 12) }
        val f = LocalSceneClassifier.featuresFromArgb(pixels)
        assertEquals(SceneLabel.NIGHT, LocalSceneClassifier.classify(f))
    }

    @Test
    fun `daylight from bright cool pixels`() {
        val pixels = IntArray(64) { argb(200, 210, 240) }
        val f = LocalSceneClassifier.featuresFromArgb(pixels)
        assertEquals(SceneLabel.DAYLIGHT, LocalSceneClassifier.classify(f))
    }

    @Test
    fun `sunset warm mid brightness`() {
        val pixels = IntArray(64) { argb(210, 120, 60) }
        val f = LocalSceneClassifier.featuresFromArgb(pixels)
        assertEquals(SceneLabel.SUNSET_WARM, LocalSceneClassifier.classify(f))
    }

    @Test
    fun `green nature`() {
        val pixels = IntArray(64) { argb(40, 180, 50) }
        val f = LocalSceneClassifier.featuresFromArgb(pixels)
        assertEquals(SceneLabel.GREEN_NATURE, LocalSceneClassifier.classify(f))
    }

    @Test
    fun `cool indoor mid-dark blue`() {
        val f = SceneFeatures(
            avgBrightness = 0.40f,
            warmBias = -0.2f,
            greenDominance = 0.4f,
            blueDominance = 0.75f
        )
        assertEquals(SceneLabel.COOL_INDOOR, LocalSceneClassifier.classify(f))
    }

    @Test
    fun `toCompanionEffect only known resources`() {
        val effect = LocalSceneClassifier.toCompanionEffect(
            SceneLabel.DAYLIGHT,
            knownBackgroundIds = knownBg,
            allowedMoods = moods
        )
        assertEquals("sky", effect.backgroundPresetId)
        assertEquals("smile", effect.moodHint)
        assertTrue(effect.backgroundPresetId in knownBg)
        assertTrue(effect.moodHint in moods)

        val unknown = LocalSceneClassifier.toCompanionEffect(
            SceneLabel.UNKNOWN,
            knownBackgroundIds = knownBg,
            allowedMoods = moods
        )
        assertNull(unknown.backgroundPresetId)
        assertNull(unknown.moodHint)
    }

    @Test
    fun `toCompanionEffect drops illegal bg and mood`() {
        val effect = LocalSceneClassifier.toCompanionEffect(
            SceneLabel.NIGHT,
            knownBackgroundIds = emptySet(),
            allowedMoods = emptySet()
        )
        assertNull(effect.backgroundPresetId)
        assertNull(effect.moodHint)
        assertEquals(SceneLabel.NIGHT, effect.scene)
    }

    @Test
    fun `mapping table uses only CompanionBackgrounds presets`() {
        val labels = listOf(
            SceneLabel.DAYLIGHT,
            SceneLabel.NIGHT,
            SceneLabel.SUNSET_WARM,
            SceneLabel.GREEN_NATURE,
            SceneLabel.COOL_INDOOR
        )
        for (label in labels) {
            val e = LocalSceneClassifier.toCompanionEffect(label, knownBg, moods)
            val bg = e.backgroundPresetId
            assertTrue("bg for $label must be known", bg != null && bg in knownBg)
            val mood = e.moodHint
            assertTrue("mood for $label must be allowed", mood != null && mood in moods)
        }
    }

    @Test
    fun `SceneLabel fromId`() {
        assertEquals(SceneLabel.NIGHT, SceneLabel.fromId("night"))
        assertEquals(SceneLabel.UNKNOWN, SceneLabel.fromId("nope"))
        assertEquals(SceneLabel.UNKNOWN, SceneLabel.fromId(null))
    }
}
