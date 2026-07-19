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
 * 导游 Guide V1 — 与导航 Navigate **独立**。
 *
 * 职责：景点/展品讲解、看世界识别讲解、历史文化与看点；可选位置增强。
 * 看世界抓帧归属本模块侧（复用 companion vision），导航不强制开相机。
 *
 * 不实现自研 turn-by-turn；「去这里」仅提示/互跳 [com.lanxin.android.builtin.navigate] 的 open_navigation。
 */
object GuideConfig {
    /** 模块 id（文档 / 埋点） */
    const val FEATURE_ID = "guide"

    /** 复用全屏陪伴「看世界」 */
    const val VISION_FEATURE = "companion_vision_explain"

    /** 可选：附近景点介绍（依赖 get_location + web_search，非导航 POI） */
    const val NEARBY_SIGHTS_HINT = "nearby_sights_explain"

    /** 对话侧可选 tool 名（V1 主入口仍是陪伴「看世界」） */
    const val EXPLAIN_SIGHT_TOOL = "explain_sight"

    /** 导航互跳目标 tool（不复制实现） */
    const val NAV_HANDOFF_TOOL = "open_navigation"

    /** 位置增强：坐标精度提示（约 100m 量级展示） */
    const val LOCATION_HINT_PRECISION_M = 100

    /** 互跳提示文案前缀 */
    const val NAV_HANDOFF_PREFIX = "若要过去，可以说「导航到这里」或让我调起 open_navigation。"
}
