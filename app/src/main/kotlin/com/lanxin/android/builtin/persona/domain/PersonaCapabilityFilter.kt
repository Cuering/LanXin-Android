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

package com.lanxin.android.builtin.persona.domain

import com.lanxin.android.plugin.ToolDef

/**
 * 按人格 tools/skills allowlist 过滤 MCP 工具。
 *
 * 语义（对齐 AstrBot）：
 * - `null` → 不限制（全部可用）
 * - `[]` → 禁用该类别
 * - `["a", "b"]` → 仅允许列出的名称
 *
 * skill 工具：`skill_list` / `skill_load`，以及 [knownSkillNames] 中的名称。
 * 未识别为 skill 的工具按 [allowedTools] 过滤。
 */
object PersonaCapabilityFilter {

    val META_SKILL_TOOLS: Set<String> = setOf("skill_list", "skill_load")

    /**
     * allowlist 命中判断。
     */
    fun matchesAllowlist(name: String, allowlist: List<String>?): Boolean {
        return when {
            allowlist == null -> true
            allowlist.isEmpty() -> false
            else -> name in allowlist
        }
    }

    fun isSkillTool(
        name: String,
        knownSkillNames: Set<String> = emptySet()
    ): Boolean = name in META_SKILL_TOOLS || name in knownSkillNames

    /**
     * 过滤工具名列表。
     */
    fun filterToolNames(
        allToolNames: List<String>,
        allowedTools: List<String>?,
        allowedSkills: List<String>?,
        knownSkillNames: Set<String> = emptySet()
    ): List<String> {
        if (allowedTools == null && allowedSkills == null) {
            return allToolNames
        }
        return allToolNames.filter { name ->
            if (isSkillTool(name, knownSkillNames)) {
                matchesAllowlist(name, allowedSkills)
            } else {
                matchesAllowlist(name, allowedTools)
            }
        }
    }

    /**
     * 过滤 [ToolDef] 列表。
     */
    fun filterTools(
        tools: List<ToolDef>,
        allowedTools: List<String>?,
        allowedSkills: List<String>?,
        knownSkillNames: Set<String> = emptySet()
    ): List<ToolDef> {
        if (allowedTools == null && allowedSkills == null) {
            return tools
        }
        val allowedNames = filterToolNames(
            allToolNames = tools.map { it.name },
            allowedTools = allowedTools,
            allowedSkills = allowedSkills,
            knownSkillNames = knownSkillNames
        ).toSet()
        return tools.filter { it.name in allowedNames }
    }
}
