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

import com.lanxin.android.builtin.pet.domain.MusicBeatAnalyzer
import com.lanxin.android.builtin.pet.domain.PetBridgeCommand
import com.lanxin.android.builtin.pet.domain.PetBridgeProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicBeatAnalyzerTest {

    @Test
    fun `silent waveform yields near zero`() {
        val wave = ByteArray(128) { 128.toByte() }
        val level = MusicBeatAnalyzer.rmsFromWaveform(wave)
        assertTrue(level < 0.05f)
    }

    @Test
    fun `loud waveform is high`() {
        val wave = ByteArray(64) { i -> if (i % 2 == 0) 0 else 255.toByte() }
        val level = MusicBeatAnalyzer.rmsFromWaveform(wave)
        assertTrue(level > 0.5f)
    }

    @Test
    fun `levelFromCapture smooths and clamps`() {
        val wave = ByteArray(32) { 200.toByte() }
        val a = MusicBeatAnalyzer.levelFromCapture(wave, previous = 0f, smooth = 0.5f)
        val b = MusicBeatAnalyzer.levelFromCapture(wave, previous = a, smooth = 0.5f)
        assertTrue(a in 0f..1f)
        assertTrue(b in 0f..1f)
        assertTrue(b >= a - 0.01f)
    }

    @Test
    fun `levelFromCapture rate-limits single frame jump`() {
        // 从 0 跳到极响：单帧不应超过 MAX_LEVEL_STEP
        val loud = ByteArray(64) { i -> if (i % 2 == 0) 0 else 255.toByte() }
        val next = MusicBeatAnalyzer.levelFromCapture(loud, previous = 0f, smooth = 0.9f, gain = 4f)
        assertTrue(next in 0f..1f)
        assertTrue(
            "expected step <= ${MusicBeatAnalyzer.MAX_LEVEL_STEP}, got $next",
            next <= MusicBeatAnalyzer.MAX_LEVEL_STEP + 1e-4f
        )
    }

    @Test
    fun `fallbackPulse in range`() {
        val p = MusicBeatAnalyzer.fallbackPulse(250L, 0f)
        assertTrue(p in 0f..1f)
        // 慢伪节拍幅度更小
        assertTrue(p < 0.25f)
    }

    @Test
    fun `setMusicBeatMessage encodes level`() {
        val msg = PetBridgeProtocol.setMusicBeatMessage(0.42f, enabled = true)
        assertEquals(PetBridgeCommand.SET_MUSIC_BEAT, msg.command)
        val wire = PetBridgeProtocol.encode(msg)
        val back = PetBridgeProtocol.decode(wire)
        assertEquals(PetBridgeCommand.SET_MUSIC_BEAT, back.command)
        val lv = back.payload[PetBridgeProtocol.KEY_BEAT_LEVEL]!!.toFloat()
        assertTrue(lv in 0.41f..0.43f)
        assertEquals("true", back.payload[PetBridgeProtocol.KEY_BEAT_ENABLED])
    }
}
