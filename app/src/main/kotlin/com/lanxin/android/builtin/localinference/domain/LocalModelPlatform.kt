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

package com.lanxin.android.builtin.localinference.domain

import com.lanxin.android.data.model.ClientType
import com.lanxin.android.plugins.chat.data.entity.PlatformV2

/**
 * 会话级「本地模型」虚拟平台。
 *
 * - 不入库 [PlatformV2] 表；仅以稳定哨兵 uid 出现在会话 [enabledPlatform] 列表中
 * - 新建会话对话框：就绪可选、未就绪灰显
 * - Chat 路由：该 uid 触发 [ChatRouteContext.forceLocal]
 */
object LocalModelPlatform {
    /** 稳定哨兵 uid（勿与 UUID 平台冲突）。 */
    const val UID = "__local_model__"

    const val DISPLAY_NAME = "本地模型"

    /** 未就绪时的副文案。 */
    const val NOT_READY_HINT = "未就绪 · 请到「设置 → 智能能力 → 本地推理」启用并加载"

    /** 就绪时的副文案。 */
    const val READY_HINT = "端侧推理 · 本会话强制本地"

    fun isLocalUid(uid: String?): Boolean = uid == UID

    /**
     * 合成平台行：仅供 UI / completeChat 槽位；不写 Room platform 表。
     * compatibleType 取 OPENAI 占位（本地路径不走云端 client）。
     */
    fun asPlatformV2(
        name: String = DISPLAY_NAME,
        model: String = "local-llm"
    ): PlatformV2 = PlatformV2(
        id = -1,
        uid = UID,
        name = name,
        compatibleType = ClientType.OPENAI,
        enabled = true,
        apiUrl = "local://inference",
        token = null,
        model = model
    )
}
