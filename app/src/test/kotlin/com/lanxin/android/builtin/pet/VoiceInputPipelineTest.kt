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

package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.PetChatResponder
import com.lanxin.android.builtin.pet.domain.VoiceInputPipeline
import com.lanxin.android.builtin.systemtools.data.AlarmSetDeviceTool
import com.lanxin.android.builtin.systemtools.data.AlarmShowDeviceTool
import com.lanxin.android.builtin.systemtools.data.CalendarCreateEventDeviceTool
import com.lanxin.android.builtin.systemtools.data.CalendarListUpcomingDeviceTool
import com.lanxin.android.builtin.systemtools.data.DeviceToolRegistry
import com.lanxin.android.builtin.systemtools.data.NoteAppendDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteCreateDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteDeleteDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteExportDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteImportDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteListDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteUpdateDeviceTool
import com.lanxin.android.builtin.systemtools.data.StubCalendarGateway
import com.lanxin.android.builtin.systemtools.data.StubNotesStore
import com.lanxin.android.builtin.systemtools.data.files.FileDeleteDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.FileListDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.FilePickDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.FileReadTextDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.FileShareDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.FileWriteDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.InMemoryUserFileCatalog
import com.lanxin.android.builtin.systemtools.domain.AlarmClockGateway
import com.lanxin.android.builtin.systemtools.domain.AlarmClockResult
import com.lanxin.android.builtin.systemtools.domain.DeviceToolBridge
import com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome
import com.lanxin.android.builtin.systemtools.domain.IntentLaunchResult
import com.lanxin.android.builtin.systemtools.domain.IntentLaunchSpec
import com.lanxin.android.builtin.systemtools.domain.NotesIoResult
import com.lanxin.android.builtin.systemtools.domain.NotesSafGateway
import com.lanxin.android.builtin.systemtools.domain.SetAlarmClockRequest
import com.lanxin.android.builtin.systemtools.domain.SystemToolsConfig
import com.lanxin.android.builtin.systemtools.domain.SystemToolsIntentLauncher
import com.lanxin.android.builtin.systemtools.domain.UserFileEntry
import com.lanxin.android.builtin.systemtools.domain.UserFileIoGateway
import com.lanxin.android.builtin.systemtools.domain.UserFileIoResult
import com.lanxin.android.builtin.systemtools.domain.UserFileProbe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VoiceInputPipeline 单测：验证输入链（tool→LLM）与监控字段。
 */
class VoiceInputPipelineTest {

    @Test
    fun `process returns reply from responder`() = runBlocking {
        val pipe = VoiceInputPipeline(
            responder = StubResponder("你好呀"),
            deviceToolBridge = bridge(withTools = true)
        )
        val r = pipe.process("你好")
        assertEquals("你好呀", r.replyText)
        assertNull(r.toolName)
        assertNull(r.toolOutcome)
        assertNull(r.llmError)
        assertNull(r.toolError)
        assertTrue(r.llmDurMs >= 0)
    }

    @Test
    fun `process with empty text returns empty reply`() = runBlocking {
        val pipe = VoiceInputPipeline(
            responder = StubResponder("不应到达"),
            deviceToolBridge = bridge()
        )
        val r = pipe.process("   ")
        assertEquals("", r.replyText)
    }

    @Test
    fun `tool round triggers toolName and outcome`() = runBlocking {
        val pipe = VoiceInputPipeline(
            responder = StubResponder("打开中"),
            deviceToolBridge = bridge(withTools = true)
        )
        val r = pipe.process("打开闹钟列表")
        assertEquals("com.lanxin.builtin.alarm.show", r.toolName)
        assertTrue(r.toolOutcome is DeviceToolOutcome.Ok)
        assertTrue(r.toolDurMs >= 0)
        assertNull(r.toolError)
    }

    @Test
    fun `responder failure returns empty reply with llmError`() = runBlocking {
        val pipe = VoiceInputPipeline(
            responder = FailingResponder(),
            deviceToolBridge = bridge()
        )
        val r = pipe.process("测试")
        assertEquals("", r.replyText)
        assertEquals("empty_reply", r.llmError)
        assertTrue(r.llmDurMs >= 0)
    }

    @Test
    fun `tool bridge failure falls back to chat-only`() = runBlocking {
        val pipe = VoiceInputPipeline(
            responder = StubResponder("免工具闲聊"),
            deviceToolBridge = bridge(withTools = false)
        )
        // 无工具注册，bridge 不崩，返回纯聊
        val r = pipe.process("随便聊聊")
        assertEquals("免工具闲聊", r.replyText)
        assertNull(r.toolName)
        assertNull(r.toolError)
    }

    // ── 辅助 ──

    private class StubResponder(val reply: String) : PetChatResponder {
        override suspend fun respond(text: String): String = reply
    }

    private class FailingResponder : PetChatResponder {
        override suspend fun respond(text: String): String {
            error("simulated_respond_fail")
        }
    }

    private fun bridge(withTools: Boolean = false): DeviceToolBridge {
        val registry = DeviceToolRegistry(
            alarmShow = if (withTools) AlarmShowDeviceTool(object : AlarmClockGateway {
                override suspend fun list(): List<AlarmClockResult> = emptyList()
                override suspend fun set(request: SetAlarmClockRequest): AlarmClockResult =
                    AlarmClockResult.Ok
                override suspend fun delete(id: Long): Boolean = true
            }) else null,
            alarmSet = if (withTools) AlarmSetDeviceTool(object : AlarmClockGateway {
                override suspend fun list(): List<AlarmClockResult> = emptyList()
                override suspend fun set(request: SetAlarmClockRequest): AlarmClockResult =
                    AlarmClockResult.Ok
                override suspend fun delete(id: Long): Boolean = true
            }) else null,
            calendarCreate = null, calendarList = null,
            noteCreate = null, noteAppend = null, noteList = null,
            noteUpdate = null, noteDelete = null, noteExport = null, noteImport = null,
            filePick = null, fileList = null, fileReadText = null,
            fileWrite = null, fileShare = null, fileDelete = null
        )
        return DeviceToolBridge.forTest(
            registry = registry,
            configProvider = { SystemToolsConfig(masterEnabled = true, alarmEnabled = withTools) }
        )
    }
}
