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

package com.lanxin.android.builtin.systemtools

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
import com.lanxin.android.builtin.systemtools.domain.DeviceToolIds
import com.lanxin.android.builtin.systemtools.domain.DeviceToolIntentResolver
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7.5：意图 → 选工具 → Gate → stub 结果回传。
 */
class DeviceToolBridgeTest {

    private class OkAlarmClock : AlarmClockGateway {
        override fun canScheduleExactAlarms() = true
        override fun setAlarmClock(request: SetAlarmClockRequest) = AlarmClockResult.Ok(
            triggerAtEpochMs = request.triggerAtEpochMs,
            requestCode = 1,
            message = request.message
        )
    }

    private class OkLauncher : SystemToolsIntentLauncher {
        override fun launch(spec: IntentLaunchSpec) = IntentLaunchResult.Ok(
            action = spec.action,
            launched = true,
            description = spec.description.ifBlank { "launched ${spec.action}" }
        )
    }

    private class NoopSaf : NotesSafGateway {
        override fun writeText(uriString: String, text: String, mimeType: String) =
            NotesIoResult.Ok("ok", bytes = text.length, uri = uriString)

        override fun readText(uriString: String) = NotesIoResult.Ok("{}", bytes = 2, uri = uriString)

        override fun shareText(text: String, mimeType: String, chooserTitle: String) =
            NotesIoResult.Ok("shared", bytes = text.length)
    }

    private class NoopFileIo : UserFileIoGateway {
        override fun readText(uriOrPath: String, maxChars: Int) =
            UserFileIoResult.Ok("", uri = uriOrPath)

        override fun writeText(uriOrPath: String, text: String, mimeType: String) =
            UserFileIoResult.Ok("ok", bytes = text.length, uri = uriOrPath)

        override fun copyToAppPrivate(uriString: String, preferredName: String?) =
            UserFileIoResult.Ok("ok", uri = "/x", name = preferredName ?: "x")

        override fun writeAppPrivateText(name: String, text: String, mimeType: String) =
            UserFileIoResult.Ok("ok", bytes = text.length, uri = "/x/$name", name = name)

        override fun listAppPrivateFiles(): List<UserFileEntry> = emptyList()

        override fun deleteAppPrivate(pathOrName: String) =
            UserFileIoResult.Ok("deleted", uri = pathOrName)

        override fun shareUri(uriOrPath: String, mimeType: String?, chooserTitle: String) =
            UserFileIoResult.Ok("shared", uri = uriOrPath)

        override fun shareText(text: String, mimeType: String, chooserTitle: String) =
            UserFileIoResult.Ok("shared", bytes = text.length)

        override fun probe(uriString: String): UserFileProbe? = null
    }

    private fun buildRegistry(): DeviceToolRegistry {
        val gateway = StubCalendarGateway()
        val notes = StubNotesStore()
        val launcher = OkLauncher()
        val saf = NoopSaf()
        val catalog = InMemoryUserFileCatalog()
        val fileIo = NoopFileIo()
        return DeviceToolRegistry(
            alarmSet = AlarmSetDeviceTool(OkAlarmClock(), launcher),
            alarmShow = AlarmShowDeviceTool(launcher),
            calendarList = CalendarListUpcomingDeviceTool(gateway),
            calendarCreate = CalendarCreateEventDeviceTool(gateway, gateway),
            noteCreate = NoteCreateDeviceTool(notes),
            noteList = NoteListDeviceTool(notes),
            noteAppend = NoteAppendDeviceTool(notes),
            noteUpdate = NoteUpdateDeviceTool(notes),
            noteDelete = NoteDeleteDeviceTool(notes),
            noteExport = NoteExportDeviceTool(notes, saf),
            noteImport = NoteImportDeviceTool(notes, saf),
            filePick = FilePickDeviceTool(catalog, fileIo),
            fileList = FileListDeviceTool(catalog, fileIo),
            fileReadText = FileReadTextDeviceTool(catalog, fileIo),
            fileWrite = FileWriteDeviceTool(catalog, fileIo),
            fileShare = FileShareDeviceTool(catalog, fileIo),
            fileDelete = FileDeleteDeviceTool(catalog, fileIo)
        )
    }

    @Test
    fun `resolver maps open alarm list`() {
        val r = DeviceToolIntentResolver()
        val plan = r.resolve("帮我打开闹钟列表")
        assertNotNull(plan)
        assertEquals(DeviceToolIds.ALARM_SHOW, plan!!.toolName)
    }

    @Test
    fun `resolver maps set alarm with time`() {
        val r = DeviceToolIntentResolver()
        val plan = r.resolve("设个闹钟 7:30")
        assertNotNull(plan)
        assertEquals(DeviceToolIds.ALARM_SET, plan!!.toolName)
        assertEquals(7, plan.args["hour"])
        assertEquals(30, plan.args["minutes"])
    }

    @Test
    fun `chatty text resolves to null`() {
        val r = DeviceToolIntentResolver()
        assertNull(r.resolve("兰心，你好呀"))
    }

    @Test
    fun `e2e intent to gate to stub ok when enabled and confirmed`() = runBlocking {
        val bridge = DeviceToolBridge.forTest(
            registry = buildRegistry(),
            configProvider = {
                SystemToolsConfig(
                    masterEnabled = true,
                    alarmEnabled = true,
                    requireConfirmOnWrite = true
                )
            }
        )
        val inv = bridge.resolveAndInvoke("设个闹钟 8:00", confirmed = true)
        assertNotNull(inv)
        assertEquals(DeviceToolIds.ALARM_SET, inv!!.plan.toolName)
        assertTrue(inv.outcome is DeviceToolOutcome.Ok)
        val summary = bridge.summarize(inv.outcome, inv.plan.toolName)
        assertTrue(summary.contains(DeviceToolIds.ALARM_SET) || summary.isNotBlank())
    }

    @Test
    fun `e2e master off denies`() = runBlocking {
        val bridge = DeviceToolBridge.forTest(
            registry = buildRegistry(),
            configProvider = {
                SystemToolsConfig(masterEnabled = false, alarmEnabled = true)
            }
        )
        val inv = bridge.resolveAndInvoke("打开闹钟列表", confirmed = false)
        assertNotNull(inv)
        assertEquals(DeviceToolIds.ALARM_SHOW, inv!!.plan.toolName)
        assertTrue(inv.outcome is DeviceToolOutcome.Denied)
        assertEquals("master_disabled", (inv.outcome as DeviceToolOutcome.Denied).code)
    }

    @Test
    fun `e2e write needs confirmation by default`() = runBlocking {
        val bridge = DeviceToolBridge.forTest(
            registry = buildRegistry(),
            configProvider = {
                SystemToolsConfig(
                    masterEnabled = true,
                    alarmEnabled = true,
                    requireConfirmOnWrite = true
                )
            }
        )
        val inv = bridge.resolveAndInvoke("设个闹钟 9点", confirmed = false)
        assertNotNull(inv)
        assertTrue(inv!!.outcome is DeviceToolOutcome.NeedsConfirmation)
    }

    @Test
    fun `alarm_show read path no confirm`() = runBlocking {
        val bridge = DeviceToolBridge.forTest(
            registry = buildRegistry(),
            configProvider = {
                SystemToolsConfig(masterEnabled = true, alarmEnabled = true)
            }
        )
        val inv = bridge.resolveAndInvoke("打开闹钟列表")
        assertNotNull(inv)
        assertTrue(inv!!.outcome is DeviceToolOutcome.Ok)
    }

    @Test
    fun `unknown tool invoke errors`() = runBlocking {
        val bridge = DeviceToolBridge.forTest(
            registry = buildRegistry(),
            configProvider = { SystemToolsConfig(masterEnabled = true, alarmEnabled = true) }
        )
        val out = bridge.invoke("not_a_tool", emptyMap())
        assertTrue(out is DeviceToolOutcome.Error)
        assertEquals("unknown_tool", (out as DeviceToolOutcome.Error).code)
    }

    @Test
    fun `chatTurn execute false only discovers intent`() = runBlocking {
        val bridge = DeviceToolBridge.forTest(
            registry = buildRegistry(),
            configProvider = {
                SystemToolsConfig(masterEnabled = true, alarmEnabled = true)
            }
        )
        val turn = bridge.chatTurn("打开闹钟列表", execute = false)
        assertTrue(turn.needsTools)
        assertEquals(DeviceToolIds.ALARM_SHOW, turn.plan!!.toolName)
        assertNull(turn.outcome)
        assertNull(turn.summary)
        assertEquals(
            com.lanxin.android.builtin.systemtools.domain.DeviceToolChannel.CHAT,
            turn.channel
        )
    }

    @Test
    fun `voiceTurn executes and summarizes`() = runBlocking {
        val bridge = DeviceToolBridge.forTest(
            registry = buildRegistry(),
            configProvider = {
                SystemToolsConfig(masterEnabled = true, alarmEnabled = true)
            }
        )
        val turn = bridge.voiceTurn("打开闹钟列表", confirmed = false)
        assertTrue(turn.needsTools)
        assertEquals(
            com.lanxin.android.builtin.systemtools.domain.DeviceToolChannel.VOICE,
            turn.channel
        )
        assertTrue(turn.outcome is DeviceToolOutcome.Ok)
        assertNotNull(turn.summary)
        assertNotNull(turn.toInvocationOrNull())
    }

    @Test
    fun `chatTurn chatty text no tools`() = runBlocking {
        val bridge = DeviceToolBridge.forTest(
            registry = buildRegistry(),
            configProvider = { SystemToolsConfig(masterEnabled = true) }
        )
        val turn = bridge.chatTurn("兰心，你好呀")
        assertTrue(!turn.needsTools)
        assertNull(turn.plan)
        assertNull(turn.outcome)
    }

    @Test
    fun `detectsToolIntent mirrors resolve`() {
        val bridge = DeviceToolBridge.forTest(
            registry = buildRegistry(),
            configProvider = { SystemToolsConfig() }
        )
        assertTrue(bridge.detectsToolIntent("设个闹钟 8:00"))
        assertTrue(!bridge.detectsToolIntent("今天天气怎么样"))
    }
}
