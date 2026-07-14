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
import org.junit.Assert.assertEquals
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
}
