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
 * 导游位置上下文：把 last known 粗坐标格式化为讲解 prompt 片段。
 *
 * 不发起持续定位；坐标仅作「附近可能是…」提示，不宣称厘米级精度。
 */
object GuideLocationContext {

    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val accuracyM: Double? = null,
        val provider: String? = null
    )

    /**
     * 从 LocationTool.readOnce() 风格字段构造；缺 lat/lng 返回 null。
     */
    fun fromMap(
        latitude: Double?,
        longitude: Double?,
        accuracyM: Double? = null,
        provider: String? = null
    ): Fix? {
        if (latitude == null || longitude == null) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return Fix(latitude, longitude, accuracyM, provider)
    }

    /**
     * 生成注入多模态/文本讲解的中文 snippet。
     * 例：用户约在 39.90°N, 116.40°E（精度约 50m，last known）附近。
     */
    fun toPromptSnippet(fix: Fix): String {
        val lat = "%.4f".format(fix.latitude)
        val lon = "%.4f".format(fix.longitude)
        val acc = fix.accuracyM?.let { "，精度约 ${it.toInt().coerceAtLeast(1)}m" }.orEmpty()
        val note = "（last known，非持续定位）"
        return "用户约在 $lat°N, $lon°E$acc$note 附近。若画面与坐标相关，可结合常识推断可能景点/展区，并标明「供参考」。"
    }

    /** 空/无效时不注入。 */
    fun snippetOrEmpty(fix: Fix?): String =
        if (fix == null) "" else toPromptSnippet(fix)
}
