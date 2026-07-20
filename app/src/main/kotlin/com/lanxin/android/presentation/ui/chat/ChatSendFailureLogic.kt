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

    private const val MAX_MESSAGE_CHARS = 180
    private const val FALLBACK = "发送失败，请重试"

    fun userVisibleMessage(t: Throwable): String {
        val raw = t.message?.trim().orEmpty()
        if (raw.isBlank()) {
            val simple = t::class.java.simpleName.trim()
            return if (simple.isNotBlank() && simple != "Exception" && simple != "RuntimeException") {
                simple
            } else {
                FALLBACK
            }
        }
        // 去掉超长堆栈式 message，避免 toast 刷屏
        val oneLine = raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (oneLine.isBlank()) return FALLBACK
        return if (oneLine.length <= MAX_MESSAGE_CHARS) {
            oneLine
        } else {
            oneLine.take(MAX_MESSAGE_CHARS - 1) + "…"
        }
    }

    fun toastMessage(detail: String): String {
        val d = detail.trim()
        return when {
            d.isBlank() -> FALLBACK
            d.startsWith("发送失败") -> d
            else -> "发送失败：$d"
        }
    }

    fun bubbleContent(existingContent: String, error: String): String =
        buildAssistantErrorContent(existingContent, error)

    /**
     * 复位 loading：指定平台 idle；索引越界 / 长度不齐时整体 idle，避免二次 IndexOutOfBounds。
     */
    fun nextLoadingStates(
        current: List<ChatViewModel.LoadingState>,
        platformIndex: Int,
        platformCount: Int
    ): List<ChatViewModel.LoadingState> {
        val size = platformCount.coerceAtLeast(current.size).coerceAtLeast(1)
        if (platformIndex !in 0 until size) {
            return List(size) { ChatViewModel.LoadingState.Idle }
        }
        val base = if (current.size == size) {
            current
        } else {
            List(size) { i -> current.getOrNull(i) ?: ChatViewModel.LoadingState.Idle }
        }
        return base.toMutableList().apply {
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
