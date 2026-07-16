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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lanxin.android.builtin.systemtools.domain.NoteEntry

@Entity(
    tableName = "system_notes",
    indices = [
        Index(value = ["updated_at_epoch_ms"])
    ]
)
data class NoteEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,

    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long = updatedAtEpochMs
)

fun NoteEntity.toDomain(): NoteEntry = NoteEntry(
    id = id,
    title = title,
    body = body,
    updatedAtEpochMs = updatedAtEpochMs
)

fun NoteEntry.toEntity(createdAtEpochMs: Long = updatedAtEpochMs): NoteEntity = NoteEntity(
    id = id,
    title = title,
    body = body,
    updatedAtEpochMs = updatedAtEpochMs,
    createdAtEpochMs = createdAtEpochMs
)
