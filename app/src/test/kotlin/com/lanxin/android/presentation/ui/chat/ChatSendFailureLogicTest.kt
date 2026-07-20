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

import com.lanxin.android.builtin.capabilities.domain.LocationConfig
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesConfig
import com.lanxin.android.builtin.navigate.domain.NavigateConfig
import com.lanxin.android.builtin.platform.domain.DeviceSensingConfig
import com.lanxin.android.builtin.platform.domain.WebSearchConfig
import com.lanxin.android.plugin.ToolDef
import com.lanxin.android.plugins.chat.data.entity.MessageV2
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 发送静默闪退 v2：失败必须用户可见、门闸/引用/槽位不得硬崩。
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
    fun `userVisibleMessage falls back when blank`() {
        assertEquals(
            "NullPointerException",
            ChatSendFailureLogic.userVisibleMessage(NullPointerException())
        )
        assertEquals(
            "发送失败，请重试",
            ChatSendFailureLogic.userVisibleMessage(RuntimeException("   "))
        )
    }

    @Test
    fun `userVisibleMessage takes first non-blank line and truncates`() {
        assertEquals(
            "boom",
            ChatSendFailureLogic.userVisibleMessage(IllegalStateException("boom\nstack"))
        )
        val long = "x".repeat(300)
        val msg = ChatSendFailureLogic.userVisibleMessage(RuntimeException(long))
        assertTrue(msg.endsWith("…"))
        assertTrue(msg.length <= 180)
    }

    @Test
    fun `toastMessage prefixes Chinese send-failed label`() {
        assertEquals("发送失败：网络超时", ChatSendFailureLogic.toastMessage("网络超时"))
        assertEquals("发送失败，请重试", ChatSendFailureLogic.toastMessage(""))
        assertEquals("发送失败：已有前缀", ChatSendFailureLogic.toastMessage("发送失败：已有前缀"))
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
    fun `nextLoadingStates pads when platformCount larger than current`() {
        val next = ChatSendFailureLogic.nextLoadingStates(
            current = listOf(ChatViewModel.LoadingState.Loading),
            platformIndex = 0,
            platformCount = 3
        )
        assertEquals(3, next.size)
        assertEquals(ChatViewModel.LoadingState.Idle, next[0])
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

    @Test
    fun `safeRefsFromUnifiedKeys returns empty on size mismatch instead of require crash`() {
        val bad = ChatSendPathGuard.safeRefsFromUnifiedKeys(
            keys = listOf("memory:1", "knowledge:k:1"),
            texts = listOf("a"),
            subtitles = listOf("s1", "s2")
        )
        assertTrue(bad.sizeMismatch)
        assertTrue(bad.refs.isEmpty())

        val ok = ChatSendPathGuard.safeRefsFromUnifiedKeys(
            keys = listOf("memory:1"),
            texts = listOf("hello world"),
            subtitles = listOf("记忆")
        )
        assertFalse(ok.sizeMismatch)
        assertEquals(1, ok.refs.size)
        assertEquals(ChatRefType.MEMORY, ok.refs[0].type)
    }

    @Test
    fun `canWriteAssistantSlot rejects out of range without throw`() {
        val rows = listOf(listOf("a"), listOf("b", "c"))
        assertTrue(ChatSendPathGuard.canWriteAssistantSlot(rows, 0, 0))
        assertTrue(ChatSendPathGuard.canWriteAssistantSlot(rows, 1, 1))
        assertFalse(ChatSendPathGuard.canWriteAssistantSlot(rows, 2, 0))
        assertFalse(ChatSendPathGuard.canWriteAssistantSlot(rows, 0, 1))
        assertFalse(ChatSendPathGuard.canWriteAssistantSlot(emptyList(), 0, 0))
    }

    @Test
    fun `openAiDeltaContent never NPE on empty choices`() {
        assertNull(ChatSendPathGuard.openAiDeltaContent(0, "x"))
        assertNull(ChatSendPathGuard.openAiDeltaContent(1, null))
        assertEquals("hi", ChatSendPathGuard.openAiDeltaContent(1, "hi"))
    }

    @Test
    fun `tool filter keeps tools when gates open and persona unrestricted`() {
        val tools = listOf(
            tool("web_search"),
            tool("system_info"),
            tool("get_location"),
            tool(NavigateConfig.NEARBY_POI_TOOL),
            tool("other")
        )
        val out = ChatSendToolFilterLogic.filter(
            ChatSendToolFilterLogic.FilterInput(
                tools = tools,
                smart = SmartCapabilitiesConfig(masterEnabled = true),
                webSearch = WebSearchConfig(enabled = true),
                deviceSensing = DeviceSensingConfig(enabled = true),
                location = LocationConfig(enabled = true)
            )
        )
        assertEquals(5, out.tools.size)
        assertNull(out.allowedNames)
    }

    @Test
    fun `tool filter strips gated tools when master off`() {
        val tools = listOf(
            tool("web_search"),
            tool("system_info"),
            tool("get_location"),
            tool(NavigateConfig.NEARBY_POI_TOOL),
            tool(NavigateConfig.OPEN_NAVIGATION_TOOL),
            tool("other")
        )
        val out = ChatSendToolFilterLogic.filter(
            ChatSendToolFilterLogic.FilterInput(
                tools = tools,
                smart = SmartCapabilitiesConfig(masterEnabled = false),
                webSearch = WebSearchConfig(enabled = true),
                deviceSensing = DeviceSensingConfig(enabled = true),
                location = LocationConfig(enabled = true)
            )
        )
        val names = out.tools.map { it.name }.toSet()
        assertFalse(names.contains("web_search"))
        assertFalse(names.contains("system_info"))
        assertFalse(names.contains("get_location"))
        assertFalse(names.contains(NavigateConfig.NEARBY_POI_TOOL))
        assertFalse(names.contains(NavigateConfig.OPEN_NAVIGATION_TOOL))
        assertTrue(names.contains("other"))
    }

    @Test
    fun `tool filter applies persona allowlist`() {
        val tools = listOf(tool("web_search"), tool("other"), tool("skill_list"))
        val out = ChatSendToolFilterLogic.filter(
            ChatSendToolFilterLogic.FilterInput(
                tools = tools,
                smart = SmartCapabilitiesConfig(masterEnabled = true),
                webSearch = WebSearchConfig(enabled = true),
                allowedTools = listOf("other"),
                allowedSkills = emptyList(),
                knownSkillNames = emptySet()
            )
        )
        assertEquals(listOf("other"), out.tools.map { it.name })
        assertEquals(setOf("other"), out.allowedNames)
    }

    private fun tool(name: String): ToolDef = ToolDef(
        name = name,
        description = name,
        parameters = JsonObject(emptyMap())
    )
}
