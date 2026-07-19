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
 * 酒店价位文本粗提（纯函数）。
 */
object HotelPriceHints {

    /**
     * 从文本中粗提价格片段（¥ / 元 / ￥ / RMB / 起）。
     */
    fun extract(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val patterns = listOf(
            Regex("""[¥￥]\s*\d{2,6}(?:\.\d{1,2})?\s*(?:起|晚|\/晚|/晚)?"""),
            Regex("""\d{2,6}(?:\.\d{1,2})?\s*元\s*(?:起|\/晚|/晚|每晚)?"""),
            Regex("""RMB\s*\d{2,6}(?:\.\d{1,2})?""", RegexOption.IGNORE_CASE),
            Regex("""\d{2,6}(?:\.\d{1,2})?\s*(?:CNY|USD)\b""", RegexOption.IGNORE_CASE)
        )
        val found = mutableListOf<String>()
        for (p in patterns) {
            p.findAll(text).forEach { found += it.value.trim() }
        }
        return found
    }
}
