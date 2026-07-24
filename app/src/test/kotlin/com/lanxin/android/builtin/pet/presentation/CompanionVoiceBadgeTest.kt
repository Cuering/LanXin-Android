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

package com.lanxin.android.builtin.pet.presentation

import com.lanxin.android.builtin.voice.domain.VoiceChatPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanionVoiceBadgeTest {

    @Test
    fun listening_shows_partial_or_default() {
        assertEquals(
            "听：你好",
            companionVoiceBadge(VoiceChatPhase.LISTENING, "你好", enabled = true)
        )
        assertEquals(
            "正在听…",
            companionVoiceBadge(VoiceChatPhase.LISTENING, "", enabled = true)
        )
    }

    @Test
    fun idle_enabled_shows_open() {
        assertEquals(
            "语音已开",
            companionVoiceBadge(VoiceChatPhase.IDLE, "", enabled = true)
        )
        assertNull(companionVoiceBadge(VoiceChatPhase.IDLE, "", enabled = false))
    }

    @Test
    fun busy_phases() {
        assertEquals("识别中…", companionVoiceBadge(VoiceChatPhase.TRANSCRIBING, "", true))
        assertEquals("想回复…", companionVoiceBadge(VoiceChatPhase.WAITING_REPLY, "", true))
        assertEquals("正在说…", companionVoiceBadge(VoiceChatPhase.SPEAKING, "", true))
    }
}
