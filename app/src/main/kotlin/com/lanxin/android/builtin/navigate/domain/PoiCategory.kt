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
 * 附近 POI 类别 → OpenStreetMap Overpass 过滤片段。
 *
 * 仅覆盖 V1 高频：洗手间 / 出口 / 电梯 / 餐饮 / 酒店 等。
 */
enum class PoiCategory(
    val id: String,
    val displayName: String,
    /** Overpass 内 ( ... ) 子句列表，已含 around 占位符 {{around}} */
    val overpassClauses: List<String>
) {
    RESTROOM(
        id = "restroom",
        displayName = "洗手间",
        overpassClauses = listOf(
            """node["amenity"="toilets"]({{around}});""",
            """way["amenity"="toilets"]({{around}});""",
            """node["toilets"="yes"]({{around}});"""
        )
    ),
    EXIT(
        id = "exit",
        displayName = "出口",
        overpassClauses = listOf(
            """node["railway"="subway_entrance"]({{around}});""",
            """node["entrance"="exit"]({{around}});""",
            """node["entrance"="main"]({{around}});""",
            """node["highway"="motorway_junction"]({{around}});""",
            """node["barrier"="gate"]({{around}});"""
        )
    ),
    ELEVATOR(
        id = "elevator",
        displayName = "电梯",
        overpassClauses = listOf(
            """node["highway"="elevator"]({{around}});""",
            """way["highway"="elevator"]({{around}});""",
            """node["elevator"="yes"]({{around}});"""
        )
    ),
    DINING(
        id = "dining",
        displayName = "餐饮",
        overpassClauses = listOf(
            """node["amenity"~"restaurant|cafe|fast_food|food_court|biergarten"]({{around}});""",
            """way["amenity"~"restaurant|cafe|fast_food|food_court"]({{around}});"""
        )
    ),
    HOTEL(
        id = "hotel",
        displayName = "酒店",
        overpassClauses = listOf(
            """node["tourism"~"hotel|guest_house|hostel|motel|apartment"]({{around}});""",
            """way["tourism"~"hotel|guest_house|hostel|motel|apartment"]({{around}});"""
        )
    ),
    ATM(
        id = "atm",
        displayName = "ATM",
        overpassClauses = listOf(
            """node["amenity"="atm"]({{around}});""",
            """node["amenity"="bank"]({{around}});"""
        )
    ),
    PHARMACY(
        id = "pharmacy",
        displayName = "药店",
        overpassClauses = listOf(
            """node["amenity"="pharmacy"]({{around}});""",
            """way["amenity"="pharmacy"]({{around}});"""
        )
    ),
    PARKING(
        id = "parking",
        displayName = "停车场",
        overpassClauses = listOf(
            """node["amenity"="parking"]({{around}});""",
            """way["amenity"="parking"]({{around}});"""
        )
    );

    companion object {
        /**
         * 解析用户/Agent 传入的类别字符串（中英别名）。
         */
        fun parse(raw: String?): PoiCategory? {
            if (raw.isNullOrBlank()) return null
            val key = raw.trim().lowercase()
                .replace(' ', '_')
                .replace('-', '_')
            return when (key) {
                "restroom", "toilet", "toilets", "wc", "bathroom",
                "洗手间", "厕所", "卫生间", "公厕" -> RESTROOM
                "exit", "entrance", "way_out", "出口", "出入口", "大门" -> EXIT
                "elevator", "lift", "电梯", "升降梯" -> ELEVATOR
                "dining", "restaurant", "food", "cafe", "eat",
                "餐饮", "餐厅", "饭店", "吃饭", "美食" -> DINING
                "hotel", "lodging", "stay", "酒店", "宾馆", "旅馆", "住宿" -> HOTEL
                "atm", "bank", "取款机", "银行" -> ATM
                "pharmacy", "drugstore", "药店", "药房" -> PHARMACY
                "parking", "carpark", "停车场", "停车" -> PARKING
                else -> entries.firstOrNull { it.id == key }
            }
        }

        fun knownIds(): List<String> = entries.map { it.id }
    }
}
