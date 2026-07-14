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

package com.lanxin.android.builtin.persona.data

import androidx.room.TypeConverter
import org.json.JSONArray

/**
 * Room TypeConverter 用于 [PersonaEntity] 中的 List<String>? 字段。
 *
 * AstrBot 行为：
 * - null 表示「使用所有工具/技能」
 * - 空列表表示「不使用任何工具/技能」
 * - 非空列表表示「仅使用列出的工具/技能」
 */
class PersonaTypeConverter {

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return if (value == null) null
        else JSONArray(value).toString()
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return if (value == null) null
        else {
            val arr = JSONArray(value)
            (0 until arr.length()).map { arr.getString(it) }
        }
    }
}
