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
 * 组装导游讲解用 user 问题（可含位置 snippet）。
 *
 * 多模态仍由 VisionExplainClient 负责；本类只拼文本上下文。
 */
object GuidePromptBuilder {

    private const val ROLE_HINT =
        "你是兰心导游：结合画面讲解景点/展品/环境，语气亲切；不确定时说明供参考，不编造精确票价与开放时间。"

    /**
     * @param userQuestion 用户原话
     * @param locationSnippet [GuideLocationContext.toPromptSnippet] 或空
     * @param includeRoleHint 是否前置角色提示（默认 true）
     */
    fun buildExplainQuestion(
        userQuestion: String,
        locationSnippet: String = "",
        includeRoleHint: Boolean = true
    ): String {
        val q = userQuestion.trim().ifBlank { "请描述你现在看到的画面，并简要讲解。" }
        val parts = buildList {
            if (includeRoleHint) add(ROLE_HINT)
            if (locationSnippet.isNotBlank()) add("【位置上下文】$locationSnippet")
            add("【用户问题】$q")
        }
        return parts.joinToString("\n")
    }
}
