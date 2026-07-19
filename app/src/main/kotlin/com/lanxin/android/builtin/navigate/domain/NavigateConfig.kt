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

package com.lanxin.android.builtin.navigate.domain

/**
 * 导航 Navigate（V1）工具名与默认参数。
 *
 * 职责：附近 POI + 外链导航 + 酒店价联网摘要。独立于导游 Guide，不揉成 ScenicGuide。
 * 不做：自研 turn-by-turn、后台轨迹、无 consent 摄像头。
 */
object NavigateConfig {
    const val NEARBY_POI_TOOL = "nearby_poi"
    const val OPEN_NAVIGATION_TOOL = "open_navigation"
    const val HOTEL_PRICE_TOOL = "hotel_price_lookup"

    /** 默认搜索半径（米） */
    const val DEFAULT_RADIUS_M = 800

    /** 半径上限（米） */
    const val MAX_RADIUS_M = 3000

    /** 默认返回条数 */
    const val DEFAULT_LIMIT = 5

    /** 条数上限 */
    const val MAX_LIMIT = 15

    val ALL_TOOL_NAMES: Set<String> = setOf(
        NEARBY_POI_TOOL,
        OPEN_NAVIGATION_TOOL,
        HOTEL_PRICE_TOOL
    )
}
