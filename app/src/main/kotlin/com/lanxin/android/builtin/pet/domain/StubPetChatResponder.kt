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
 * M1 stub 桌宠回复：短句、妹妹口吻结构，**不**复制任何商业角色版权文案。
 *
 * 后续可换 ChatRouter / 云端 API，保持 [PetChatResponder] 边界。
 */
@Singleton
class StubPetChatResponder @Inject constructor() : PetChatResponder {

    override suspend fun respond(userText: String): String {
        val t = userText.trim()
        if (t.isEmpty()) return "嗯？我在听。"
        // 短回复：桌宠语音场景
        val snippet = if (t.length <= 24) t else t.take(24) + "…"
        return "嗯嗯，听到了：「$snippet」。我在呢～"
    }
}
