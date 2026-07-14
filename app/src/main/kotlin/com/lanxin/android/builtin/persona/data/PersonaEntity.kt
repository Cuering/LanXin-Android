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

package com.lanxin.android.builtin.persona.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lanxin.android.builtin.persona.domain.Persona

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "system_prompt") val systemPrompt: String,
    @ColumnInfo(name = "begin_dialogs") val beginDialogs: List<String>? = null,
    @ColumnInfo(name = "tools") val tools: List<String>? = null,
    @ColumnInfo(name = "skills") val skills: List<String>? = null,
    @ColumnInfo(name = "custom_error_message") val customErrorMessage: String? = null,
    @ColumnInfo(name = "folder_id") val folderId: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_builtin") val isBuiltin: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

fun PersonaEntity.toDomain(): Persona = Persona(
    id = id,
    name = name,
    systemPrompt = systemPrompt,
    beginDialogs = beginDialogs,
    tools = tools,
    skills = skills,
    customErrorMessage = customErrorMessage,
    folderId = folderId,
    sortOrder = sortOrder,
    isBuiltin = isBuiltin,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Persona.toEntity(): PersonaEntity = PersonaEntity(
    id = id,
    name = name,
    systemPrompt = systemPrompt,
    beginDialogs = beginDialogs,
    tools = tools,
    skills = skills,
    customErrorMessage = customErrorMessage,
    folderId = folderId,
    sortOrder = sortOrder,
    isBuiltin = isBuiltin,
    createdAt = createdAt,
    updatedAt = updatedAt
)
