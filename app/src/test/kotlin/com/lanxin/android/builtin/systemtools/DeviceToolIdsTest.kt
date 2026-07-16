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
import com.lanxin.android.builtin.systemtools.domain.DeviceToolIds
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

    @Test
    fun `stable tool names include alarm calendar notes files`() {
        assertTrue(DeviceToolIds.ALL.contains("alarm_set"))
        assertTrue(DeviceToolIds.ALL.contains("calendar_list_upcoming"))
        assertTrue(DeviceToolIds.ALL.contains("note_export"))
        assertTrue(DeviceToolIds.ALL.contains("note_import"))
        assertTrue(DeviceToolIds.ALL.contains("file_pick"))
        assertTrue(DeviceToolIds.ALL.contains("file_delete"))
        assertTrue(DeviceToolIds.M1_STUB_READY.contains("alarm_set"))
        assertTrue(DeviceToolIds.M1_STUB_READY.contains("note_delete"))
        assertTrue(DeviceToolIds.M1_STUB_READY.contains("file_list"))
        assertEquals(7, DeviceToolIds.NOTES_READY.size)
        assertEquals(6, DeviceToolIds.FILES_READY.size)
    }

    @Test
    fun `registry exposes phase74 tools`() {
        val gateway = StubCalendarGateway()
        val notes = StubNotesStore()
        val launcher = OkLauncher()
        val saf = NoopSaf()
        val catalog = InMemoryUserFileCatalog()
        val fileIo = NoopFileIo()
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
            noteImport = NoteImportDeviceTool(notes, saf),
            filePick = FilePickDeviceTool(catalog, fileIo),
            fileList = FileListDeviceTool(catalog, fileIo),
            fileReadText = FileReadTextDeviceTool(catalog, fileIo),
            fileWrite = FileWriteDeviceTool(catalog, fileIo),
            fileShare = FileShareDeviceTool(catalog, fileIo),
            fileDelete = FileDeleteDeviceTool(catalog, fileIo)
        )
        assertEquals(DeviceToolIds.M1_STUB_READY, registry.names())
        assertTrue(registry.names().containsAll(DeviceToolIds.NOTES_READY))
        assertTrue(registry.names().containsAll(DeviceToolIds.FILES_READY))
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
