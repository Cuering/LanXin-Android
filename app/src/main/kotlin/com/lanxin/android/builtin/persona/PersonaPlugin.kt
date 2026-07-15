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

import com.lanxin.android.builtin.persona.domain.PersonaRepository
import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.ToolDef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 人格设定插件（对齐 AstrBot Persona 系统）。
 * 注册 MCP 工具：persona_list / persona_get / persona_set / persona_create / persona_delete
 * 兼容旧名：persona_switch / persona_current
 */
@Singleton
class PersonaPlugin @Inject constructor(
    private val personaRepository: PersonaRepository
) : LanXinPlugin {

    override val id = "lanxin.persona"
    override val name = "人格设定"
    override val version = "1.2.0"
    override val description =
        "管理 AI 人格预设，支持 beginDialogs / tools / skills 过滤 / mood_imitation_dialogs"

    override suspend fun onLoad(context: PluginContext) {
        personaRepository.ensureSeeded()

        // ── persona_list ──
        context.registerTool(
            ToolDef(
                name = "persona_list",
                description = "列出所有可用人格（预设 + 自定义），标注当前项及 beginDialogs/tools/skills/mood 等信息",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject { })
                },
                handler = {
                    val current = personaRepository.getCurrent()
                    val all = personaRepository.getAllOnce()
                    buildJsonObject {
                        put("current_id", current.id)
                        put("count", all.size)
                        put(
                            "personas",
                            buildJsonArray {
                                all.forEach { p ->
                                    add(buildPersonaJson(p, current.id))
                                }
                            }
                        )
                    }
                }
            )
        )

        // ── persona_get ──
        context.registerTool(
            ToolDef(
                name = "persona_get",
                description = "按 id 获取人格详情，包含 systemPrompt / beginDialogs / tools / skills / mood_imitation_dialogs / customErrorMessage",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "persona_id",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "人格 ID（可用 persona_list 查询）")
                                }
                            )
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("persona_id")) })
                },
                handler = { args ->
                    val personaId = args["persona_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val p = personaRepository.getById(personaId)
                    if (p == null) {
                        return@ToolDef buildJsonObject { put("error", "人格不存在: $personaId") }
                    }
                    buildPersonaJson(p, p.id)
                }
            )
        )

        // ── persona_set (别名：persona_switch) ──
        context.registerTool(
            ToolDef(
                name = "persona_set",
                description = "设置当前人格。兼容旧名 persona_switch。后续对话将使用该人格的 system prompt",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "persona_id",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "目标人格 ID")
                                }
                            )
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("persona_id")) })
                },
                handler = { args ->
                    val personaId = args["persona_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (personaId.isBlank()) {
                        return@ToolDef buildJsonObject { put("error", "persona_id 不能为空") }
                    }
                    val ok = personaRepository.switchPersona(personaId)
                    if (!ok) {
                        return@ToolDef buildJsonObject { put("error", "人格不存在: $personaId") }
                    }
                    val current = personaRepository.getCurrent()
                    buildJsonObject {
                        put("ok", true)
                        put("id", current.id)
                        put("name", current.name)
                    }
                }
            )
        )

        // ── persona_switch (兼容旧名) ──
        context.registerTool(
            ToolDef(
                name = "persona_switch",
                description = "[兼容旧名] 切换当前人格，等同 persona_set",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "persona_id",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "目标人格 ID")
                                }
                            )
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("persona_id")) })
                },
                handler = { args ->
                    val personaId = args["persona_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (personaId.isBlank()) {
                        return@ToolDef buildJsonObject { put("error", "persona_id 不能为空") }
                    }
                    val ok = personaRepository.switchPersona(personaId)
                    if (!ok) {
                        return@ToolDef buildJsonObject { put("error", "人格不存在: $personaId") }
                    }
                    val current = personaRepository.getCurrent()
                    buildJsonObject {
                        put("ok", true)
                        put("id", current.id)
                        put("name", current.name)
                    }
                }
            )
        )

        // ── persona_current ──
        context.registerTool(
            ToolDef(
                name = "persona_current",
                description = "获取当前选中的人格完整信息（含 tools/skills/mood_imitation_dialogs）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject { })
                },
                handler = {
                    val current = personaRepository.getCurrent()
                    buildPersonaJson(current, current.id)
                }
            )
        )

        // ── persona_create ──
        context.registerTool(
            ToolDef(
                name = "persona_create",
                description = "创建新的人格。system_prompt 必填，其他可选。tools/skills 为 null 表示全部可用，[] 表示禁用",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "name",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "显示名称")
                                }
                            )
                            put(
                                "system_prompt",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "系统提示词")
                                }
                            )
                            put(
                                "begin_dialogs",
                                buildJsonObject {
                                    put("type", "array")
                                    put("items", buildJsonObject { put("type", "string") })
                                    put("description", "预设对话（偶数字符串，交替 user/assistant）")
                                }
                            )
                            put(
                                "tools",
                                buildJsonObject {
                                    put("type", "array")
                                    put("items", buildJsonObject { put("type", "string") })
                                    put("description", "工具列表（null=全部，[]=禁用）")
                                }
                            )
                            put(
                                "skills",
                                buildJsonObject {
                                    put("type", "array")
                                    put("items", buildJsonObject { put("type", "string") })
                                    put("description", "技能列表（null=全部，[]=禁用）")
                                }
                            )
                            put(
                                "custom_error_message",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "自定义报错回复")
                                }
                            )
                            put(
                                "mood_imitation_dialogs",
                                buildJsonObject {
                                    put("type", "array")
                                    put("items", buildJsonObject { put("type", "string") })
                                    put("description", "情绪风格示例对话（交替 user/assistant）")
                                }
                            )
                        }
                    )
                    put(
                        "required",
                        buildJsonArray {
                            add(JsonPrimitive("name"))
                            add(JsonPrimitive("system_prompt"))
                        }
                    )
                },
                handler = { args ->
                    val name = args["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val systemPrompt = args["system_prompt"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (name.isBlank() || systemPrompt.isBlank()) {
                        return@ToolDef buildJsonObject {
                            put("error", "name 和 system_prompt 不能为空")
                        }
                    }
                    val beginDialogs = parseStringList(args["begin_dialogs"])
                    val tools = parseStringList(args["tools"])
                    val skills = parseStringList(args["skills"])
                    val customErrorMessage = args["custom_error_message"]?.jsonPrimitive?.contentOrNull
                    val moodImitationDialogs = parseStringList(args["mood_imitation_dialogs"])
                    val persona = personaRepository.createPersona(
                        name = name,
                        systemPrompt = systemPrompt,
                        beginDialogs = beginDialogs,
                        tools = tools,
                        skills = skills,
                        customErrorMessage = customErrorMessage,
                        moodImitationDialogs = moodImitationDialogs
                    )
                    buildJsonObject {
                        put("ok", true)
                        put("id", persona.id)
                        put("name", persona.name)
                    }
                }
            )
        )

        // ── persona_delete ──
        context.registerTool(
            ToolDef(
                name = "persona_delete",
                description = "删除指定人格（仅限自定义人格，内置人格不可删除）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "persona_id",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "要删除的人格 ID")
                                }
                            )
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("persona_id")) })
                },
                handler = { args ->
                    val personaId = args["persona_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (personaId.isBlank()) {
                        return@ToolDef buildJsonObject { put("error", "persona_id 不能为空") }
                    }
                    val ok = personaRepository.deletePersona(personaId)
                    if (!ok) {
                        return@ToolDef buildJsonObject { put("error", "删除失败（可能为内置人格或不存在）") }
                    }
                    buildJsonObject { put("ok", true) }
                }
            )
        )
    }

    private fun buildPersonaJson(
        p: com.lanxin.android.builtin.persona.domain.Persona,
        currentId: String
    ) = buildJsonObject {
        put("id", p.id)
        put("name", p.name)
        put("system_prompt", p.systemPrompt)
        put("is_builtin", p.isBuiltin)
        put("is_current", p.id == currentId)
        put("sort_order", p.sortOrder)
        putStringListOrNull("begin_dialogs", p.beginDialogs)
        putStringListOrNull("tools", p.tools)
        putStringListOrNull("skills", p.skills)
        putStringListOrNull("mood_imitation_dialogs", p.moodImitationDialogs)
        if (p.customErrorMessage != null) {
            put("custom_error_message", JsonPrimitive(p.customErrorMessage))
        } else {
            put("custom_error_message", JsonNull)
        }
        if (p.folderId != null) {
            put("folder_id", JsonPrimitive(p.folderId))
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putStringListOrNull(
        key: String,
        value: List<String>?
    ) {
        if (value != null) {
            put(
                key,
                buildJsonArray {
                    value.forEach { add(JsonPrimitive(it)) }
                }
            )
        } else {
            put(key, JsonNull)
        }
    }

    /** 解析 JSON 参数中的字符串列表。null/不存在的字段返回 null，空数组返回空列表。 */
    private fun parseStringList(element: JsonElement?): List<String>? {
        if (element == null || element is JsonNull) return null
        val arr = element.jsonArray
        if (arr.isEmpty()) return emptyList()
        return arr.map { it.jsonPrimitive.content }
    }
}
