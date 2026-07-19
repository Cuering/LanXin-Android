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

import com.lanxin.android.plugins.chat.data.entity.MessageV2
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 覆盖聊天静默闪退防护：失败必须用户可见、不 rethrow 普通异常。
 */
class ChatSendFailureLogicTest {

    @Test
    fun `userVisibleMessage prefers throwable message`() {
        assertEquals(
            "boom",
            ChatSendFailureLogic.userVisibleMessage(IllegalStateException("boom"))
        )
    }

    @Test
    fun `userVisibleMessage falls back to class name when message blank`() {
        assertEquals(
            "NullPointerException",
            ChatSendFailureLogic.userVisibleMessage(NullPointerException())
        )
        assertEquals(
            "NullPointerException",
            ChatSendFailureLogic.userVisibleMessage(NullPointerException("   "))
        )
    }

    @Test
    fun `toastMessage prefixes Chinese send-failed label`() {
        assertEquals("发送失败：网络超时", ChatSendFailureLogic.toastMessage("网络超时"))
    }

    @Test
    fun `bubbleContent uses Error prefix when empty`() {
        assertEquals(
            "Error: tool NPE",
            ChatSendFailureLogic.bubbleContent("", "tool NPE")
        )
    }

    @Test
    fun `bubbleContent appends Response stopped when partial content exists`() {
        val content = ChatSendFailureLogic.bubbleContent("partial", "timeout")
        assertTrue(content.startsWith("partial"))
        assertTrue(content.contains("[Response stopped: timeout]"))
    }

    @Test
    fun `nextLoadingStates marks target platform idle`() {
        val states = listOf(
            ChatViewModel.LoadingState.Loading,
            ChatViewModel.LoadingState.Loading
        )
        val next = ChatSendFailureLogic.nextLoadingStates(
            current = states,
            platformIndex = 1,
            platformCount = 2
        )
        assertEquals(ChatViewModel.LoadingState.Loading, next[0])
        assertEquals(ChatViewModel.LoadingState.Idle, next[1])
    }

    @Test
    fun `nextLoadingStates resets all when platform index out of bounds`() {
        val states = listOf(ChatViewModel.LoadingState.Loading)
        val next = ChatSendFailureLogic.nextLoadingStates(
            current = states,
            platformIndex = 5,
            platformCount = 1
        )
        assertEquals(1, next.size)
        assertEquals(ChatViewModel.LoadingState.Idle, next[0])
    }

    @Test
    fun `nextLoadingStates creates idle list when current empty`() {
        val next = ChatSendFailureLogic.nextLoadingStates(
            current = emptyList(),
            platformIndex = 0,
            platformCount = 2
        )
        assertEquals(2, next.size)
        assertTrue(next.all { it == ChatViewModel.LoadingState.Idle })
    }

    @Test
    fun `shouldRethrow only for cancellation`() {
        assertTrue(ChatSendFailureLogic.shouldRethrow(CancellationException("gone")))
        assertFalse(ChatSendFailureLogic.shouldRethrow(IllegalStateException("no")))
        assertFalse(ChatSendFailureLogic.shouldRethrow(NullPointerException("npe")))
        assertFalse(ChatSendFailureLogic.shouldRethrow(RuntimeException("boom")))
    }

    @Test
    fun `updateAssistantSlot surfaces error content without throwing`() {
        val grouped = ChatViewModel.GroupedMessages(
            userMessages = listOf(MessageV2(chatId = 1, content = "hi", platformType = null)),
            assistantMessages = listOf(
                listOf(MessageV2(chatId = 1, content = "", platformType = "p1"))
            )
        )
        val updated = updateAssistantSlot(
            groupedMessages = grouped,
            turnIndex = 0,
            platformIndex = 0
        ) { current ->
            current.copy(content = ChatSendFailureLogic.bubbleContent(current.content, "NPE in tools"))
        }
        assertEquals("Error: NPE in tools", updated.assistantMessages[0][0].content)
    }

    @Test
    fun `updateAssistantSlot ignores bad indices instead of crashing`() {
        val grouped = ChatViewModel.GroupedMessages(
            userMessages = listOf(MessageV2(chatId = 1, content = "hi", platformType = null)),
            assistantMessages = listOf(
                listOf(MessageV2(chatId = 1, content = "ok", platformType = "p1"))
            )
        )
        val same = updateAssistantSlot(
            groupedMessages = grouped,
            turnIndex = 9,
            platformIndex = 0
        ) { it.copy(content = "nope") }
        assertEquals("ok", same.assistantMessages[0][0].content)

        val same2 = updateAssistantSlot(
            groupedMessages = grouped,
            turnIndex = 0,
            platformIndex = 3
        ) { it.copy(content = "nope") }
        assertEquals("ok", same2.assistantMessages[0][0].content)
    }
}
