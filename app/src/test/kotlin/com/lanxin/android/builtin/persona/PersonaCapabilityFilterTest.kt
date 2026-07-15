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

import com.lanxin.android.builtin.persona.domain.PersonaCapabilityFilter
import com.lanxin.android.plugin.ToolDef
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaCapabilityFilterTest {

    private val tools = listOf(
        tool("memory_recall"),
        tool("memory_store"),
        tool("skill_list"),
        tool("skill_load"),
        tool("material-learning-summary"),
        tool("persona_list")
    )
    private val knownSkills = setOf("material-learning-summary")

    @Test
    fun `null allowlists keep all tools`() {
        val filtered = PersonaCapabilityFilter.filterTools(
            tools = tools,
            allowedTools = null,
            allowedSkills = null,
            knownSkillNames = knownSkills
        )
        assertEquals(tools.map { it.name }, filtered.map { it.name })
    }

    @Test
    fun `empty tools disables non-skill tools`() {
        val filtered = PersonaCapabilityFilter.filterTools(
            tools = tools,
            allowedTools = emptyList(),
            allowedSkills = null,
            knownSkillNames = knownSkills
        )
        val names = filtered.map { it.name }
        assertFalse(names.contains("memory_recall"))
        assertFalse(names.contains("persona_list"))
        assertTrue(names.contains("skill_list"))
        assertTrue(names.contains("material-learning-summary"))
    }

    @Test
    fun `empty skills disables skill tools`() {
        val filtered = PersonaCapabilityFilter.filterTools(
            tools = tools,
            allowedTools = null,
            allowedSkills = emptyList(),
            knownSkillNames = knownSkills
        )
        val names = filtered.map { it.name }
        assertTrue(names.contains("memory_recall"))
        assertFalse(names.contains("skill_list"))
        assertFalse(names.contains("material-learning-summary"))
    }

    @Test
    fun `allowlist restricts both categories`() {
        val filtered = PersonaCapabilityFilter.filterTools(
            tools = tools,
            allowedTools = listOf("memory_recall"),
            allowedSkills = listOf("material-learning-summary"),
            knownSkillNames = knownSkills
        )
        assertEquals(
            listOf("memory_recall", "material-learning-summary"),
            filtered.map { it.name }
        )
    }

    @Test
    fun `filterToolNames matches names only`() {
        val names = PersonaCapabilityFilter.filterToolNames(
            allToolNames = listOf("a", "b", "skill_list"),
            allowedTools = listOf("a"),
            allowedSkills = emptyList(),
            knownSkillNames = emptySet()
        )
        assertEquals(listOf("a"), names)
    }

    private fun tool(name: String): ToolDef = ToolDef(
        name = name,
        description = name,
        parameters = buildJsonObject { },
        handler = { buildJsonObject { } }
    )
}
