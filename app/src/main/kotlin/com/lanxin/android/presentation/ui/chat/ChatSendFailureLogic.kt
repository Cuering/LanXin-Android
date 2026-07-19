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

package com.lanxin.android.presentation.ui.chat

import com.lanxin.android.util.buildAssistantErrorContent

/**
 * 聊天发送失败的纯逻辑（可单测）。
 *
 * 设计目标：任何未预期异常都要落到**用户可见**错误，
 * 禁止静默吞掉或再次 throw 出 viewModelScope。
 */
object ChatSendFailureLogic {

    fun userVisibleMessage(t: Throwable): String {
        val msg = t.message?.trim().orEmpty()
        if (msg.isNotEmpty()) return msg
        val simple = t::class.java.simpleName?.trim().orEmpty()
        return simple.ifBlank { "UnknownError" }
    }

    fun toastMessage(detail: String): String = "发送失败：$detail"

    fun bubbleContent(existingContent: String, error: String): String =
        buildAssistantErrorContent(existingContent, error)

    /**
     * 复位 loading：指定平台 idle；索引越界时整表 idle。
     */
    fun nextLoadingStates(
        current: List<ChatViewModel.LoadingState>,
        platformIndex: Int,
        platformCount: Int
    ): List<ChatViewModel.LoadingState> {
        if (current.isEmpty()) {
            val size = platformCount.coerceAtLeast(1)
            return List(size) { ChatViewModel.LoadingState.Idle }
        }
        if (platformIndex !in current.indices) {
            return List(current.size) { ChatViewModel.LoadingState.Idle }
        }
        return current.toMutableList().apply {
            this[platformIndex] = ChatViewModel.LoadingState.Idle
        }
    }

    /**
     * 是否应把异常重新抛给协程框架（仅取消）。
     * 其它异常必须吃掉并 surface。
     */
    fun shouldRethrow(t: Throwable): Boolean =
        t is kotlinx.coroutines.CancellationException
}
