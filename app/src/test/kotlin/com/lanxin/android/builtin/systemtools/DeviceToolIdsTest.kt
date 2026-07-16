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
import com.lanxin.android.builtin.systemtools.data.NoteListDeviceTool
import com.lanxin.android.builtin.systemtools.data.StubCalendarGateway
import com.lanxin.android.builtin.systemtools.data.StubNotesStore
import com.lanxin.android.builtin.systemtools.domain.DeviceToolIds
import com.lanxin.android.builtin.systemtools.domain.SystemToolsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceToolIdsTest {

    @Test
    fun `stable tool names include alarm and calendar`() {
        assertTrue(DeviceToolIds.ALL.contains("alarm_set"))
        assertTrue(DeviceToolIds.ALL.contains("calendar_list_upcoming"))
        assertTrue(DeviceToolIds.M1_STUB_READY.contains("alarm_set"))
        assertTrue(DeviceToolIds.M1_STUB_READY.contains("calendar_list_upcoming"))
    }

    @Test
    fun `registry exposes m1 tools`() {
        val gateway = StubCalendarGateway()
        val notes = StubNotesStore()
        val registry = DeviceToolRegistry(
            alarmSet = AlarmSetDeviceTool(),
            alarmShow = AlarmShowDeviceTool(),
            calendarList = CalendarListUpcomingDeviceTool(gateway),
            calendarCreate = CalendarCreateEventDeviceTool(gateway),
            noteCreate = NoteCreateDeviceTool(notes),
            noteList = NoteListDeviceTool(notes),
            noteAppend = NoteAppendDeviceTool(notes)
        )
        assertEquals(DeviceToolIds.M1_STUB_READY, registry.names())
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
