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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 人格设定插件：注册 MCP 工具 persona_list / persona_switch / persona_current。
 */
@Singleton
class PersonaPlugin @Inject constructor(
    private val personaRepository: PersonaRepository
) : LanXinPlugin {

    override val id = "lanxin.persona"
    override val name = "人格设定"
    override val version = "1.0.0"
    override val description = "管理 AI 人格预设，切换后注入 system prompt"

    override suspend fun onLoad(context: PluginContext) {
        personaRepository.ensureSeeded()

        context.registerTool(
            ToolDef(
                name = "persona_list",
                description = "列出所有可用人格（预设 + 自定义），标注当前选中项",
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
                                    add(
                                        buildJsonObject {
                                            put("id", p.id)
                                            put("name", p.name)
                                            put("system_prompt", p.systemPrompt)
                                            put("is_builtin", p.isBuiltin)
                                            put("is_current", p.id == current.id)
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "persona_switch",
                description = "切换当前人格，后续对话将使用该人格的 system prompt",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "persona_id",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "目标人格 ID（可用 persona_list 查询）")
                                }
                            )
                        }
                    )
                    put(
                        "required",
                        buildJsonArray {
                            add(JsonPrimitive("persona_id"))
                        }
                    )
                },
                handler = { args ->
                    val personaId = args["persona_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (personaId.isBlank()) {
                        return@ToolDef buildJsonObject {
                            put("error", "persona_id 不能为空")
                        }
                    }
                    val ok = personaRepository.switchPersona(personaId)
                    if (!ok) {
                        return@ToolDef buildJsonObject {
                            put("error", "人格不存在: $personaId")
                        }
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

        context.registerTool(
            ToolDef(
                name = "persona_current",
                description = "获取当前选中的人格信息与 system prompt",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject { })
                },
                handler = {
                    val current = personaRepository.getCurrent()
                    buildJsonObject {
                        put("id", current.id)
                        put("name", current.name)
                        put("system_prompt", current.systemPrompt)
                        put("is_builtin", current.isBuiltin)
                    }
                }
            )
        )
    }
}
