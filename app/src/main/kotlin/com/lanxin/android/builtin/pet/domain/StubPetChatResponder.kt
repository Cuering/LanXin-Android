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
import kotlin.math.absoluteValue

/**
 * M1 stub 桌宠回复：短句 + 隐藏 `[[mood=…]]`，**不**复制任何商业角色版权文案。
 *
 * 后续可换 ChatRouter / 云端 API，保持 [PetChatResponder] 边界。
 * 标签供 [MoodTagMapper] 驱动表情；展示 / TTS 路径会 strip。
 *
 * 注意：本地脑未就绪时会落到这里。**禁止**简单回声用户原话，避免“重复同样的话”。
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
        val line = pickLine(mood, t)
        return "[[mood=$mood]]\n$line"
    }

    private fun pickLine(mood: String, userText: String): String {
        val pool = when (mood) {
            "music" -> listOf(
                "想听歌呀？我在呢，稍等我帮你想想。",
                "好呀，音乐的事交给我～",
                "嗯，放点什么比较好呢？"
            )
            "sorry" -> listOf(
                "没事的，我在这儿陪你。",
                "别难过，说给我听也好。",
                "抱抱你。慢慢来就好。"
            )
            "think" -> listOf(
                "让我想一下…",
                "嗯，稍等我理一理。",
                "好，我认真想想。"
            )
            "joy" -> listOf(
                "嘿嘿，听你这么说我也好开心～",
                "哇，真好！",
                "耶，一起开心！"
            )
            "smile" -> listOf(
                "你好呀～我在呢。",
                "嗨，今天也要好好的哦。",
                "嗯嗯，看到你啦。"
            )
            else -> listOf(
                "嗯嗯，我在听。",
                "好呀，继续说～",
                "收到啦，我陪着你。",
                "了解～还有什么想聊的吗？",
                "我在呢，慢慢说就好。"
            )
        }
        val idx = (userText.hashCode().absoluteValue) % pool.size
        return pool[idx]
    }
}
