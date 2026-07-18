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

package com.lanxin.android.builtin.persona

import com.lanxin.android.builtin.persona.domain.PersonaMoodFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaMoodFormatterTest {

    @Test
    fun `null or empty dialogs produce empty section`() {
        assertEquals("", PersonaMoodFormatter.formatMoodImitationSection(null))
        assertEquals("", PersonaMoodFormatter.formatMoodImitationSection(emptyList()))
    }

    @Test
    fun `formats alternating user assistant lines`() {
        val section = PersonaMoodFormatter.formatMoodImitationSection(
            listOf("你好呀", "嗨～", "今天怎么样", "超级开心！")
        )
        assertTrue(section.startsWith(PersonaMoodFormatter.SECTION_HEADER))
        assertTrue(section.contains("user: 你好呀"))
        assertTrue(section.contains("assistant: 嗨～"))
        assertTrue(section.contains("user: 今天怎么样"))
        assertTrue(section.contains("assistant: 超级开心！"))
    }

    @Test
    fun `appendToSystemPrompt concatenates with blank line`() {
        val result = PersonaMoodFormatter.appendToSystemPrompt(
            base = "你是兰心",
            dialogs = listOf("hi", "hello")
        )
        assertTrue(result.startsWith("你是兰心"))
        assertTrue(result.contains(PersonaMoodFormatter.SECTION_HEADER))
        assertTrue(result.contains("user: hi"))
        assertTrue(result.contains("assistant: hello"))
    }

    @Test
    fun `appendToSystemPrompt keeps base when no mood`() {
        assertEquals("base", PersonaMoodFormatter.appendToSystemPrompt("base", null))
        assertEquals("base", PersonaMoodFormatter.appendToSystemPrompt("base", emptyList()))
    }

    @Test
    fun `appendToSystemPrompt works with empty base`() {
        val result = PersonaMoodFormatter.appendToSystemPrompt("", listOf("u", "a"))
        assertFalse(result.contains("\n\n\n"))
        assertTrue(result.contains("user: u"))
    }

    @Test
    fun `appendPetMoodTagInstruction injects once`() {
        val once = PersonaMoodFormatter.appendPetMoodTagInstruction("你是兰心")
        assertTrue(once.contains(PersonaMoodFormatter.PET_MOOD_TAG_SECTION_HEADER))
        assertTrue(once.contains("[[mood=joy]]"))
        assertTrue(once.contains("smile, listen, think"))
        val twice = PersonaMoodFormatter.appendPetMoodTagInstruction(once)
        assertEquals(once, twice)
    }
}
