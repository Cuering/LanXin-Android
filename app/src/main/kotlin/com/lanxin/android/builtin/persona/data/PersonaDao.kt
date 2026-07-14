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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas ORDER BY sort_order ASC, is_builtin DESC, updated_at DESC")
    fun getAllPersonas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas ORDER BY sort_order ASC, is_builtin DESC, updated_at DESC")
    suspend fun getAllPersonasOnce(): List<PersonaEntity>

    @Query("SELECT * FROM personas WHERE id = :id LIMIT 1")
    suspend fun getPersonaById(id: String): PersonaEntity?

    @Query("SELECT * FROM personas WHERE folder_id = :folderId ORDER BY sort_order ASC")
    suspend fun getPersonasByFolder(folderId: String?): List<PersonaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(persona: PersonaEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(persona: PersonaEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(personas: List<PersonaEntity>)

    @Update
    suspend fun update(persona: PersonaEntity)

    @Query("DELETE FROM personas WHERE id = :id AND is_builtin = 0")
    suspend fun deleteById(id: String): Int

    @Query("UPDATE personas SET folder_id = NULL WHERE folder_id = :folderId")
    suspend fun movePersonasOutOfFolder(folderId: String)

    @Query("SELECT COUNT(*) FROM personas")
    suspend fun count(): Int
}
