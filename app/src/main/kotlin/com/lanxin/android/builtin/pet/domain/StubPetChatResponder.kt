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

package com.lanxin.android.builtin.pet.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * M1 stub 桌宠回复：短句 + 隐藏 `[[mood=…]]`，**不**复制任何商业角色版权文案。
 *
 * 后续可换 ChatRouter / 云端 API，保持 [PetChatResponder] 边界。
 * 标签供 [MoodTagMapper] 驱动表情；展示 / TTS 路径会 strip。
 */
@Singleton
class StubPetChatResponder @Inject constructor() : PetChatResponder {

    override suspend fun respond(userText: String): String {
        val t = userText.trim()
        if (t.isEmpty()) {
            return "[[mood=listen]]\n嗯？我在听。"
        }
        val lower = t.lowercase()
        val mood = when {
            listOf("音乐", "听歌", "放歌", "bgm", "唱歌").any { t.contains(it) || lower.contains(it) } ->
                "music"
            listOf("抱歉", "对不起", "难过", "伤心").any { t.contains(it) } ->
                "sorry"
            listOf("想想", "思考", "稍等").any { t.contains(it) } ->
                "think"
            listOf("哈哈", "开心", "太棒", "喜欢", "耶", "哇").any { t.contains(it) } ->
                "joy"
            listOf("你好", "哈喽", "hello", "hi").any { lower.contains(it.lowercase()) || t.contains(it) } ->
                "smile"
            else -> "speak"
        }
        val snippet = if (t.length <= 24) t else t.take(24) + "…"
        return "[[mood=$mood]]\n嗯嗯，听到了：「$snippet」。我在呢～"
    }
}
