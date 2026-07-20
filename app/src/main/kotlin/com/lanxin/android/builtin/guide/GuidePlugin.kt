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

package com.lanxin.android.builtin.guide

import com.lanxin.android.builtin.capabilities.domain.LocationGate
import com.lanxin.android.builtin.capabilities.domain.LocationSettings
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesSettings
import com.lanxin.android.builtin.capabilities.tools.LocationTool
import com.lanxin.android.builtin.guide.domain.GuideConfig
import com.lanxin.android.builtin.guide.domain.GuideGate
import com.lanxin.android.builtin.guide.domain.GuideLocationContext
import com.lanxin.android.builtin.guide.domain.GuideNavHandoff
import com.lanxin.android.builtin.guide.domain.GuidePromptBuilder
import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.ToolDef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 导游 Guide 编译期插件（id = [GuideConfig.PLUGIN_ID]）。
 *
 * **默认 OFF**：未开不注册 explain_sight；Companion「看世界」入口由 GuideGate 门闸。
 * 与导航 Navigate 拆开，不揉成 ScenicGuide。
 */
@Singleton
class GuidePlugin @Inject constructor(
    private val smartCapabilitiesSettings: SmartCapabilitiesSettings,
    private val locationSettings: LocationSettings,
    private val locationTool: LocationTool
) : LanXinPlugin {

    override val id = GuideConfig.PLUGIN_ID
    override val name = "导游"
    override val version = "1.0.0"
    override val description =
        "看世界讲解 / 位置增强 / 导航互跳提示（默认关；设置 → 智能能力 → 导游 开启；与导航拆开）"

    override suspend fun onLoad(context: PluginContext) {
        context.registerTool(
            ToolDef(
                name = GuideConfig.EXPLAIN_SIGHT_TOOL,
                description =
                "景点/展品/话题文本讲解辅助：可选位置上下文；不抓帧（视觉讲解走陪伴「看世界」）；默认关需先开导游插件",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("topic", stringProp("讲解主题或用户问题"))
                            put("place_hint", stringProp("可选地点名，用于导航互跳提示"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("topic")) })
                },
                handler = { args ->
                    runCatching {
                        val smart = runCatching {
                            smartCapabilitiesSettings.getConfig()
                        }.getOrDefault(
                            com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesConfig()
                        )
                        GuideGate.denyExplainIfDisabled(
                            pluginEnabled = smart.guideEnabled,
                            masterEnabled = smart.masterEnabled
                        )?.let { return@runCatching it }

                        val topic = args.string("topic") ?: error("topic 必填")
                        val placeHint = args.string("place_hint")
                        val locationSnippet = resolveLocationSnippet(smart)
                        val prompt = GuidePromptBuilder.buildExplainQuestion(
                            userQuestion = topic,
                            locationSnippet = locationSnippet
                        )
                        val body = buildString {
                            append("已组装导游讲解上下文（请结合多模态/文本模型作答）：\n")
                            append(prompt)
                        }
                        val withHandoff = GuideNavHandoff.appendIfNeeded(body, topic, placeHint)
                        buildJsonObject {
                            put("ok", true)
                            put("feature", GuideConfig.FEATURE_ID)
                            put("prompt", prompt)
                            put("message", withHandoff)
                            put("disclaimer", "讲解供参考，请以现场说明与权威来源为准。")
                        }
                    }.toToolResult()
                }
            )
        )
    }

    private suspend fun resolveLocationSnippet(
        smart: com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesConfig
    ): String {
        return runCatching {
            val locCfg = locationSettings.getConfig()
            val locOpen = LocationGate.isPrefsOpen(smart, locCfg)
            if (!GuideGate.canAugmentWithLocation(smart.guideEnabled, smart.masterEnabled, locOpen)) {
                return@runCatching ""
            }
            if (!locationTool.hasPermission()) return@runCatching ""
            val json = locationTool.readOnce()
            val ok = json["ok"]?.jsonPrimitive?.booleanOrNull == true ||
                json["ok"]?.jsonPrimitive?.contentOrNull == "true"
            if (!ok) return@runCatching ""
            val lat = json["latitude"]?.jsonPrimitive?.doubleOrNull
            val lon = json["longitude"]?.jsonPrimitive?.doubleOrNull
            val acc = json["accuracy_m"]?.jsonPrimitive?.doubleOrNull
            val provider = json["provider"]?.jsonPrimitive?.contentOrNull
            val fix = GuideLocationContext.fromMap(lat, lon, acc, provider)
            GuideLocationContext.snippetOrEmpty(fix)
        }.getOrDefault("")
    }

    private fun Result<JsonObject>.toToolResult(): JsonObject =
        fold(
            onSuccess = { it },
            onFailure = { e ->
                buildJsonObject {
                    put("ok", false)
                    put("error", e.message ?: e.toString())
                }
            }
        )

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun stringProp(desc: String) = buildJsonObject {
        put("type", "string")
        put("description", desc)
    }
}
