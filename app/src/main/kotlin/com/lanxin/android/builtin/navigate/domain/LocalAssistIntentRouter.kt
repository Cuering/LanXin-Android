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
 * 用户意图粗分：导游 vs 导航（纯规则，可单测）。
 *
 * 不替代 LLM 理解；给产品/Agent 提示与后续 skill 路由用。
 * 两者可互相跳转，但设置与默认开关拆开。
 */
object LocalAssistIntentRouter {

    enum class AssistIntent {
        /** 景点/展品/看世界讲解 → Guide */
        GUIDE,
        /** 出口/厕所/酒店价/带我去 → Navigate */
        NAVIGATE,
        /** 无法判定 */
        UNKNOWN
    }

    private val guideHints = listOf(
        "这是什么", "讲讲", "讲解", "介绍一下", "历史", "典故",
        "看点", "展品", "景点", "文物", "导游", "说说这个",
        "what is this", "explain", "tell me about"
    )

    private val navigateHints = listOf(
        "出口", "洗手间", "厕所", "卫生间", "电梯", "停车",
        "酒店多少", "酒店价格", "宾馆", "附近", "带我去", "怎么走",
        "导航", "路线", "多远", "距离", "atm", "药店",
        "where is", "how far", "navigate", "directions", "restroom", "toilet"
    )

    /**
     * 简单关键词路由。同时命中时：含明确导航动作词优先 NAVIGATE，否则 GUIDE。
     */
    fun classify(userText: String): AssistIntent {
        val t = userText.trim().lowercase()
        if (t.isEmpty()) return AssistIntent.UNKNOWN

        val guideHit = guideHints.any { t.contains(it.lowercase()) }
        val navHit = navigateHints.any { t.contains(it.lowercase()) }

        val strongNav = listOf("带我去", "怎么走", "导航", "路线", "directions", "navigate", "多远", "多少钱")
            .any { t.contains(it.lowercase()) }

        return when {
            strongNav -> AssistIntent.NAVIGATE
            guideHit && !navHit -> AssistIntent.GUIDE
            navHit && !guideHit -> AssistIntent.NAVIGATE
            guideHit && navHit -> if (strongNav) AssistIntent.NAVIGATE else AssistIntent.GUIDE
            else -> AssistIntent.UNKNOWN
        }
    }
}
