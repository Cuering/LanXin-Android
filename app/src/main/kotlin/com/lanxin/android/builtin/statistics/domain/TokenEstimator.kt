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

package com.lanxin.android.builtin.statistics.domain

/**
 * 本地 token 估算器（对齐 AstrBot：真实 usage 为 0 时使用估算）。
 *
 * 规则：
 * - CJK 字符约 1 token / 字
 * - 其它字符约 1 token / 4 字符
 * - 最少 1（非空文本）
 */
object TokenEstimator {

    fun estimate(text: String?): Int {
        if (text.isNullOrEmpty()) return 0
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (ch.isCjk()) cjk++ else other++
        }
        val tokens = cjk + (other + 3) / 4
        return tokens.coerceAtLeast(1)
    }

    fun estimateMany(texts: Iterable<String?>): Int =
        texts.sumOf { estimate(it) }

    private fun Char.isCjk(): Boolean {
        val code = this.code
        return code in 0x4E00..0x9FFF || // CJK Unified
            code in 0x3400..0x4DBF || // CJK Extension A
            code in 0xF900..0xFAFF || // CJK Compatibility
            code in 0x3000..0x303F || // CJK punctuation
            code in 0xFF00..0xFFEF // fullwidth
    }
}
