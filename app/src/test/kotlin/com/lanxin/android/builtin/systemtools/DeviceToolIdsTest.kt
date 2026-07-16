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
import com.lanxin.android.builtin.systemtools.domain.AlarmClockGateway
import com.lanxin.android.builtin.systemtools.domain.AlarmClockResult
import com.lanxin.android.builtin.systemtools.domain.DeviceToolIds
import com.lanxin.android.builtin.systemtools.domain.IntentLaunchResult
import com.lanxin.android.builtin.systemtools.domain.IntentLaunchSpec
import com.lanxin.android.builtin.systemtools.domain.NotesIoResult
import com.lanxin.android.builtin.systemtools.domain.NotesSafGateway
import com.lanxin.android.builtin.systemtools.domain.SetAlarmClockRequest
import com.lanxin.android.builtin.systemtools.domain.SystemToolsConfig
import com.lanxin.android.builtin.systemtools.domain.SystemToolsIntentLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceToolIdsTest {

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
            description = spec.description
        )
    }

    private class NoopSaf : NotesSafGateway {
        override fun writeText(uriString: String, text: String, mimeType: String) =
            NotesIoResult.Ok("ok", bytes = text.length, uri = uriString)

        override fun readText(uriString: String) = NotesIoResult.Ok("{}", bytes = 2, uri = uriString)

        override fun shareText(text: String, mimeType: String, chooserTitle: String) =
            NotesIoResult.Ok("shared", bytes = text.length)
    }

    @Test
    fun `stable tool names include alarm calendar notes`() {
        assertTrue(DeviceToolIds.ALL.contains("alarm_set"))
        assertTrue(DeviceToolIds.ALL.contains("calendar_list_upcoming"))
        assertTrue(DeviceToolIds.ALL.contains("note_export"))
        assertTrue(DeviceToolIds.ALL.contains("note_import"))
        assertTrue(DeviceToolIds.M1_STUB_READY.contains("alarm_set"))
        assertTrue(DeviceToolIds.M1_STUB_READY.contains("note_delete"))
        assertEquals(7, DeviceToolIds.NOTES_READY.size)
    }

    @Test
    fun `registry exposes phase73 tools`() {
        val gateway = StubCalendarGateway()
        val notes = StubNotesStore()
        val launcher = OkLauncher()
        val saf = NoopSaf()
        val registry = DeviceToolRegistry(
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
            noteImport = NoteImportDeviceTool(notes, saf)
        )
        assertEquals(DeviceToolIds.M1_STUB_READY, registry.names())
        assertTrue(registry.names().containsAll(DeviceToolIds.NOTES_READY))
    }

    @Test
    fun `default config is all off with confirm on write`() {
        val c = SystemToolsConfig()
        assertTrue(!c.masterEnabled)
        assertTrue(!c.calendarEnabled)
        assertTrue(!c.alarmEnabled)
        assertTrue(!c.notesEnabled)
        assertTrue(!c.userFileEnabled)
        assertTrue(c.requireConfirmOnWrite)
    }
}
