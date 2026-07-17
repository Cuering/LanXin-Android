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

package com.lanxin.android.builtin.platform.domain

/**
 * 联网搜索（web_search）配置。
 *
 * 默认 **关闭**，避免未授权外发查询；开启后 Agent 才可见/可调 [TOOL_NAME]。
 * 不改 ChatRouter / needsTools 语义：首轮仍 preferLocal；仅 tool_call 循环才强制云端。
 */
data class WebSearchConfig(
    /** 总开关；关则不向模型暴露工具，调用亦拒绝 */
    val enabled: Boolean = false,
    /** 默认返回条数（1..20） */
    val defaultLimit: Int = DEFAULT_LIMIT,
    /** DuckDuckGo 区域码，如 wt-wt / cn-zh / us-en */
    val region: String = DEFAULT_REGION
) {
    fun clampedLimit(): Int = defaultLimit.coerceIn(MIN_LIMIT, MAX_LIMIT)

    fun normalizedRegion(): String =
        region.trim().ifBlank { DEFAULT_REGION }

    companion object {
        const val TOOL_NAME = "web_search"
        const val DEFAULT_LIMIT = 8
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 20
        const val DEFAULT_REGION = "wt-wt"
    }
}
