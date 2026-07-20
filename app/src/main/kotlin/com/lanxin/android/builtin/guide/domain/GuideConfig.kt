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

package com.lanxin.android.builtin.guide.domain

/**
 * 导游 Guide 插件配置常量。
 *
 * 形态：独立 [com.lanxin.android.builtin.guide.GuidePlugin]（编译期插件，挂 PluginManager）。
 * **默认关闭**（[DEFAULT_ENABLED]=false）：未开时不注册 explain_sight、不主动要相机、
 * 全屏陪伴「看世界」入口门闸关闭。
 *
 * 与导航 Navigate 拆开，不揉成 ScenicGuide。
 */
object GuideConfig {
    /** 插件 id（PluginManager / plugin-state） */
    const val PLUGIN_ID = "lanxin.guide"

    /** 导游功能 id（文档 / 门闸 / 日志） */
    const val FEATURE_ID = "guide"

    /** 复用全屏陪伴「看世界」 */
    const val VISION_FEATURE = "companion_vision_explain"

    /** 可选：附近景点介绍（依赖 get_location + 讲解，非导航 POI 列表） */
    const val NEARBY_SIGHTS_HINT = "nearby_sights_explain"

    /** 对话工具：景点/话题讲解辅助（位置 + 可选 web_search，不抓帧） */
    const val EXPLAIN_SIGHT_TOOL = "explain_sight"

    /** 导航互跳提示里引用的已有工具名（实现在 builtin/navigate） */
    const val NAV_HANDOFF_TOOL = "open_navigation"

    /** 产品默认：关（需插件管理或智能能力设置页开启） */
    const val DEFAULT_ENABLED = false

    /** DataStore 键：智能能力页「导游」开关镜像 */
    const val PREF_KEY_ENABLED = "guide_plugin_enabled"

    /** 位置增强：坐标精度提示（约 100m 量级展示） */
    const val LOCATION_HINT_PRECISION_M = 100

    /** 互跳提示文案前缀 */
    const val NAV_HANDOFF_PREFIX = "若要过去，可以说「导航到这里」或让我调起 open_navigation。"

    val ALL_TOOL_NAMES: Set<String> = setOf(EXPLAIN_SIGHT_TOOL)
}
