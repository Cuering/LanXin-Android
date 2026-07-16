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

package com.lanxin.android.builtin.systemtools.data

import com.lanxin.android.builtin.systemtools.domain.NoteEntry
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内置轻量笔记 stub（内存）。
 * M2：Room 私有 DB + 可选 SAF 导出。
 */
@Singleton
class StubNotesStore @Inject constructor() {

    private val notes = CopyOnWriteArrayList<NoteEntry>()

    fun list(limit: Int = 50): List<NoteEntry> {
        val n = limit.coerceIn(1, 500)
        return notes.sortedByDescending { it.updatedAtEpochMs }.take(n)
    }

    fun create(title: String, body: String): NoteEntry {
        require(title.isNotBlank() || body.isNotBlank()) { "title 或 body 至少填一项" }
        val now = System.currentTimeMillis()
        val entry = NoteEntry(
            id = "note-${UUID.randomUUID()}",
            title = title.ifBlank { "未命名" }.trim(),
            body = body,
            updatedAtEpochMs = now
        )
        notes.add(entry)
        return entry
    }

    fun append(id: String, text: String): NoteEntry {
        require(text.isNotBlank()) { "append text 不能为空" }
        val idx = notes.indexOfFirst { it.id == id }
        require(idx >= 0) { "笔记不存在: $id" }
        val old = notes[idx]
        val updated = old.copy(
            body = if (old.body.isEmpty()) text else old.body + "\n" + text,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        notes[idx] = updated
        return updated
    }

    fun resetForTest() {
        notes.clear()
    }
}
