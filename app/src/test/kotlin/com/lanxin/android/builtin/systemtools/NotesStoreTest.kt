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

import com.lanxin.android.builtin.systemtools.data.NoteAppendDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteCreateDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteDeleteDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteExportDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteImportDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteListDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteUpdateDeviceTool
import com.lanxin.android.builtin.systemtools.data.StubNotesStore
import com.lanxin.android.builtin.systemtools.domain.DeviceToolGate
import com.lanxin.android.builtin.systemtools.domain.DeviceToolIds
import com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome
import com.lanxin.android.builtin.systemtools.domain.NoteEntry
import com.lanxin.android.builtin.systemtools.domain.NotesCodec
import com.lanxin.android.builtin.systemtools.domain.NotesIoResult
import com.lanxin.android.builtin.systemtools.domain.NotesSafGateway
import com.lanxin.android.builtin.systemtools.domain.SystemToolsConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotesStoreTest {

    private lateinit var store: StubNotesStore

    @Before
    fun setUp() {
        store = StubNotesStore()
        store.resetForTest()
    }

    @Test
    fun `create list append update delete roundtrip`() = runBlocking {
        val created = store.create("购物", "牛奶")
        assertTrue(created.id.startsWith("note-"))
        assertEquals(1, store.count())

        val listed = store.list()
        assertEquals(1, listed.size)
        assertEquals("购物", listed[0].title)

        val appended = store.append(created.id, "鸡蛋")
        assertTrue(appended.body.contains("牛奶"))
        assertTrue(appended.body.contains("鸡蛋"))

        val updated = store.update(created.id, title = "周末购物", body = null)
        assertEquals("周末购物", updated.title)
        assertTrue(updated.body.contains("鸡蛋"))

        assertTrue(store.delete(created.id))
        assertEquals(0, store.count())
        assertFalse(store.delete(created.id))
    }

    @Test
    fun `create rejects empty title and body`() = runBlocking {
        try {
            store.create("", "")
            assertTrue("should throw", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("至少"))
        }
    }

    @Test
    fun `upsertAll merge and clearAll`() = runBlocking {
        store.create("a", "1")
        val imported = listOf(
            NoteEntry("note-fixed-1", "导入1", "body1", 100L),
            NoteEntry("note-fixed-2", "导入2", "body2", 200L)
        )
        assertEquals(2, store.upsertAll(imported))
        assertEquals(3, store.count())
        store.clearAll()
        assertEquals(0, store.count())
        assertEquals(2, store.upsertAll(imported))
        assertEquals(2, store.count())
    }

    @Test
    fun `codec json roundtrip with unicode and escapes`() {
        val notes = listOf(
            NoteEntry("note-1", "标题\"引号", "第一行\n第二行\t制表", 42L),
            NoteEntry("note-2", "未命名", "", 99L)
        )
        val json = NotesCodec.toJsonBundle(notes, exportedAtEpochMs = 123456L)
        assertTrue(json.contains("\"kind\": \"lanxin.notes\""))
        assertTrue(json.contains("schema_version"))
        val parsed = NotesCodec.parseJsonBundle(json)
        assertEquals(2, parsed.size)
        assertEquals("note-1", parsed[0].id)
        assertEquals("标题\"引号", parsed[0].title)
        assertEquals("第一行\n第二行\t制表", parsed[0].body)
        assertEquals(42L, parsed[0].updatedAtEpochMs)
    }

    @Test
    fun `codec markdown contains titles`() {
        val md = NotesCodec.toMarkdown(
            listOf(NoteEntry("n1", "灵感", "写点什么", 1L))
        )
        assertTrue(md.contains("## 灵感"))
        assertTrue(md.contains("写点什么"))
        assertTrue(md.contains("n1"))
    }

    @Test
    fun `device tools create list update delete via gate`() = runBlocking {
        val create = NoteCreateDeviceTool(store)
        val list = NoteListDeviceTool(store)
        val update = NoteUpdateDeviceTool(store)
        val delete = NoteDeleteDeviceTool(store)
        val gate = DeviceToolGate(configProvider = {
            SystemToolsConfig(
                masterEnabled = true,
                notesEnabled = true,
                requireConfirmOnWrite = true
            )
        })

        val needs = gate.invoke(create, mapOf("title" to "t", "body" to "b"), confirmed = false)
        assertTrue(needs is DeviceToolOutcome.NeedsConfirmation)

        val created = gate.invoke(create, mapOf("title" to "t", "body" to "b"), confirmed = true)
        assertTrue(created is DeviceToolOutcome.Ok)
        val id = (created as DeviceToolOutcome.Ok).data["id"] as String

        val listed = gate.invoke(list, emptyMap(), confirmed = false)
        assertTrue(listed is DeviceToolOutcome.Ok)
        assertEquals(1, (listed as DeviceToolOutcome.Ok).data["count"])

        val updated = gate.invoke(
            update,
            mapOf("id" to id, "title" to "t2"),
            confirmed = true
        )
        assertTrue(updated is DeviceToolOutcome.Ok)

        // delete 是 EXPLICIT_APPROVE，未确认拒绝
        val needDel = gate.invoke(delete, mapOf("id" to id), confirmed = false)
        assertTrue(needDel is DeviceToolOutcome.NeedsConfirmation)
        val deleted = gate.invoke(delete, mapOf("id" to id), confirmed = true)
        assertTrue(deleted is DeviceToolOutcome.Ok)
        assertEquals(0, store.count())
    }

    @Test
    fun `export share and import from json_text`() = runBlocking {
        store.create("导出", "内容A")
        val fakeSaf = object : NotesSafGateway {
            var lastShare: String? = null
            var lastWrite: Pair<String, String>? = null
            override fun writeText(uriString: String, text: String, mimeType: String): NotesIoResult {
                lastWrite = uriString to text
                return NotesIoResult.Ok("written", bytes = text.length, uri = uriString)
            }
            override fun readText(uriString: String): NotesIoResult {
                val text = lastWrite?.second ?: return NotesIoResult.Error("empty")
                return NotesIoResult.Ok(text, bytes = text.length, uri = uriString)
            }
            override fun shareText(text: String, mimeType: String, chooserTitle: String): NotesIoResult {
                lastShare = text
                return NotesIoResult.Ok("shared", bytes = text.length)
            }
        }
        val export = NoteExportDeviceTool(store, fakeSaf)
        val import = NoteImportDeviceTool(store, fakeSaf)

        val shareOut = export.invoke(
            mapOf("format" to "json", "mode" to "share"),
            confirmed = true
        )
        assertTrue(shareOut is DeviceToolOutcome.Ok)
        assertTrue(fakeSaf.lastShare!!.contains("导出"))

        val safOut = export.invoke(
            mapOf("format" to "json", "mode" to "saf", "uri" to "content://test/notes.json"),
            confirmed = true
        )
        assertTrue(safOut is DeviceToolOutcome.Ok)

        store.clearAll()
        assertEquals(0, store.count())
        val importOut = import.invoke(
            mapOf(
                "uri" to "content://test/notes.json",
                "strategy" to "merge"
            ),
            confirmed = true
        )
        assertTrue(importOut is DeviceToolOutcome.Ok)
        assertEquals(1, store.count())
        assertEquals("导出", store.list().first().title)
    }

    @Test
    fun `notes tool ids are registered set`() {
        assertTrue(DeviceToolIds.NOTES_READY.contains(DeviceToolIds.NOTE_EXPORT))
        assertTrue(DeviceToolIds.M1_STUB_READY.contains(DeviceToolIds.NOTE_DELETE))
        assertTrue(DeviceToolIds.ALL.contains(DeviceToolIds.NOTE_IMPORT))
    }
}
