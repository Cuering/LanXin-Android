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

import com.lanxin.android.builtin.persona.domain.BuiltinPersonas
import com.lanxin.android.builtin.persona.domain.Persona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinPersonasTest {

    @Test
    fun `all presets have unique ids and non-blank prompts`() {
        val ids = BuiltinPersonas.ALL.map { it.id }.toSet()
        assertEquals(BuiltinPersonas.ALL.size, ids.size)
        BuiltinPersonas.ALL.forEach { persona ->
            assertTrue(persona.name.isNotBlank())
            assertTrue(persona.systemPrompt.isNotBlank())
            assertTrue(persona.isBuiltin)
        }
    }

    @Test
    fun `default cute professional ids match constants`() {
        assertEquals("default", BuiltinPersonas.DEFAULT.id)
        assertEquals("cute", BuiltinPersonas.CUTE.id)
        assertEquals("professional", BuiltinPersonas.PROFESSIONAL.id)
    }

    @Test
    fun `builtin personas have default values for optional fields`() {
        BuiltinPersonas.ALL.forEach { p ->
            assertNull("beginDialogs should be null for builtin", p.beginDialogs)
            assertNull("tools should be null for builtin", p.tools)
            assertNull("skills should be null for builtin", p.skills)
            assertNull("customErrorMessage should be null for builtin", p.customErrorMessage)
            assertNull("moodImitationDialogs should be null for builtin", p.moodImitationDialogs)
            assertNull("folderId should be null for builtin", p.folderId)
        }
    }

    @Test
    fun `persona data class supports all AstrBot fields`() {
        val p = Persona(
            id = "test",
            name = "测试",
            systemPrompt = "你好",
            beginDialogs = listOf("hi", "hello"),
            tools = listOf("tool1"),
            skills = emptyList(),
            customErrorMessage = "出错了",
            moodImitationDialogs = listOf("user mood", "assistant mood"),
            folderId = "folder1",
            sortOrder = 1,
            isBuiltin = false
        )
        assertEquals("test", p.id)
        assertEquals(listOf("hi", "hello"), p.beginDialogs)
        assertEquals(listOf("tool1"), p.tools)
        assertEquals(emptyList<String>(), p.skills)
        assertEquals("出错了", p.customErrorMessage)
        assertEquals(listOf("user mood", "assistant mood"), p.moodImitationDialogs)
        assertEquals("folder1", p.folderId)
        assertEquals(1, p.sortOrder)
    }

    @Test
    fun `default persona uses fallback values`() {
        val p = Persona(id = "x", name = "x", systemPrompt = "prompt")
        assertNull(p.beginDialogs)
        assertNull(p.tools)
        assertNull(p.skills)
        assertNull(p.customErrorMessage)
        assertNull(p.moodImitationDialogs)
        assertNull(p.folderId)
        assertEquals(0, p.sortOrder)
        assertEquals(false, p.isBuiltin)
    }
}
