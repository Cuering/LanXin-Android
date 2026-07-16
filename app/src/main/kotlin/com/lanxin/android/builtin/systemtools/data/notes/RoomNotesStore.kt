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

package com.lanxin.android.builtin.systemtools.data.notes

import com.lanxin.android.builtin.systemtools.domain.NoteEntry
import com.lanxin.android.builtin.systemtools.domain.NotesStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room 持久化笔记存储（应用私有 DB）。
 */
@Singleton
class RoomNotesStore @Inject constructor(
    private val dao: NoteDao
) : NotesStore {

    override suspend fun list(limit: Int): List<NoteEntry> {
        val n = limit.coerceIn(1, 500)
        return dao.list(n).map { it.toDomain() }
    }

    override suspend fun get(id: String): NoteEntry? = dao.getById(id)?.toDomain()

    override suspend fun create(title: String, body: String): NoteEntry {
        require(title.isNotBlank() || body.isNotBlank()) { "title 或 body 至少填一项" }
        val now = System.currentTimeMillis()
        val entry = NoteEntry(
            id = "note-${UUID.randomUUID()}",
            title = title.ifBlank { "未命名" }.trim(),
            body = body,
            updatedAtEpochMs = now
        )
        dao.upsert(entry.toEntity(createdAtEpochMs = now))
        return entry
    }

    override suspend fun append(id: String, text: String): NoteEntry {
        require(text.isNotBlank()) { "append text 不能为空" }
        val existing = dao.getById(id) ?: throw IllegalArgumentException("笔记不存在: $id")
        val newBody = if (existing.body.isEmpty()) text else existing.body + "\n" + text
        val updated = existing.copy(
            body = newBody,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        dao.upsert(updated)
        return updated.toDomain()
    }

    override suspend fun update(id: String, title: String?, body: String?): NoteEntry {
        val existing = dao.getById(id) ?: throw IllegalArgumentException("笔记不存在: $id")
        if (title == null && body == null) {
            throw IllegalArgumentException("title 或 body 至少提供一项")
        }
        val updated = existing.copy(
            title = title?.ifBlank { existing.title }?.trim() ?: existing.title,
            body = body ?: existing.body,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        if (updated.title.isBlank() && updated.body.isBlank()) {
            throw IllegalArgumentException("更新后标题与正文不能都为空")
        }
        dao.upsert(updated)
        return updated.toDomain()
    }

    override suspend fun delete(id: String): Boolean = dao.delete(id) > 0

    override suspend fun count(): Int = dao.count()

    override suspend fun clearAll() {
        dao.clearAll()
    }

    override suspend fun upsertAll(notes: List<NoteEntry>): Int {
        if (notes.isEmpty()) return 0
        val entities = notes.map { note ->
            val existing = dao.getById(note.id)
            note.toEntity(createdAtEpochMs = existing?.createdAtEpochMs ?: note.updatedAtEpochMs)
        }
        dao.upsertAll(entities)
        return entities.size
    }
}
