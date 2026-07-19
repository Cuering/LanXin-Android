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
 * 导游 → 导航轻互跳（纯逻辑）。
 *
 * 不复制 open_navigation 实现；仅识别「想去/导航」意图并生成提示文案。
 */
object GuideNavHandoff {

    private val NAV_INTENT = listOf(
        "导航", "带我去", "怎么走", "路线", "带路",
        "去这里", "到这里", "走过去", "送我去", "开导航",
        "navigate", "directions", "how to get"
    )

    /** 用户问题是否像「要去某地」。 */
    fun wantsNavigation(userText: String): Boolean {
        val t = userText.trim().lowercase()
        if (t.isEmpty()) return false
        return NAV_INTENT.any { key ->
            if (key.any { it.code > 127 }) {
                userText.contains(key)
            } else {
                t.contains(key)
            }
        }
    }

    /**
     * 若需要互跳，返回可追加到回复末尾的提示；否则 null。
     * [placeHint] 可选地点名，便于用户复制到导航对话。
     */
    fun handoffHint(userText: String, placeHint: String? = null): String? {
        if (!wantsNavigation(userText)) return null
        val place = placeHint?.trim().orEmpty()
        return if (place.isNotEmpty()) {
            "${GuideConfig.NAV_HANDOFF_PREFIX} 目的地可参考：$place"
        } else {
            GuideConfig.NAV_HANDOFF_PREFIX
        }
    }

    /** 把互跳提示接到讲解结果后（去重）。 */
    fun appendIfNeeded(reply: String, userText: String, placeHint: String? = null): String {
        val hint = handoffHint(userText, placeHint) ?: return reply
        if (reply.contains("open_navigation") || reply.contains("导航到这里")) {
            return reply
        }
        return reply.trimEnd() + "\n\n" + hint
    }
}
