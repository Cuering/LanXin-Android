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

package com.lanxin.android.builtin.persona.domain

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lanxin.android.builtin.persona.data.PersonaDao
import com.lanxin.android.builtin.persona.data.toDomain
import com.lanxin.android.builtin.persona.data.toEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 人格仓库：Room 存人格列表，DataStore 存当前选中 personaId。
 */
@Singleton
class PersonaRepository @Inject constructor(
    private val dao: PersonaDao,
    private val dataStore: DataStore<Preferences>
) {
    private val seedMutex = Mutex()

    private val currentPersonaIdKey = stringPreferencesKey("current_persona_id")

    val personas: Flow<List<Persona>> = dao.getAllPersonas().map { list ->
        list.map { it.toDomain() }
    }

    val currentPersonaId: Flow<String> = dataStore.data.map { prefs ->
        prefs[currentPersonaIdKey] ?: BuiltinPersonas.DEFAULT_ID
    }

    val currentPersona: Flow<Persona?> = kotlinx.coroutines.flow.combine(
        personas,
        currentPersonaId
    ) { list, id ->
        list.find { it.id == id } ?: list.find { it.id == BuiltinPersonas.DEFAULT_ID }
    }

    /**
     * 确保内置人格已写入 DB（幂等）。
     */
    suspend fun ensureSeeded() = seedMutex.withLock {
        dao.insertAllIgnore(BuiltinPersonas.ALL.map { it.toEntity() })
        if (dao.count() == 0) {
            BuiltinPersonas.ALL.forEach { dao.upsert(it.toEntity()) }
        }
    }

    suspend fun getAllOnce(): List<Persona> {
        ensureSeeded()
        return dao.getAllPersonasOnce().map { it.toDomain() }
    }

    suspend fun getById(id: String): Persona? {
        ensureSeeded()
        return dao.getPersonaById(id)?.toDomain()
    }

    suspend fun getCurrent(): Persona {
        ensureSeeded()
        val id = dataStore.data.map { it[currentPersonaIdKey] }.first()
            ?: BuiltinPersonas.DEFAULT_ID
        return getById(id)
            ?: getById(BuiltinPersonas.DEFAULT_ID)
            ?: BuiltinPersonas.DEFAULT
    }

    suspend fun getCurrentSystemPrompt(): String = getCurrent().systemPrompt

    suspend fun switchPersona(id: String): Boolean {
        ensureSeeded()
        val target = dao.getPersonaById(id) ?: return false
        dataStore.edit { prefs ->
            prefs[currentPersonaIdKey] = target.id
        }
        return true
    }

    suspend fun createPersona(name: String, systemPrompt: String): Persona {
        ensureSeeded()
        val now = System.currentTimeMillis()
        val persona = Persona(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            systemPrompt = systemPrompt.trim(),
            isBuiltin = false,
            createdAt = now,
            updatedAt = now
        )
        dao.upsert(persona.toEntity())
        return persona
    }

    suspend fun updatePersona(id: String, name: String, systemPrompt: String): Boolean {
        ensureSeeded()
        val existing = dao.getPersonaById(id) ?: return false
        // 内置人格允许改 prompt/name，但 isBuiltin 保持
        val updated = existing.copy(
            name = name.trim(),
            systemPrompt = systemPrompt.trim(),
            updatedAt = System.currentTimeMillis()
        )
        dao.upsert(updated)
        return true
    }

    suspend fun deletePersona(id: String): Boolean {
        ensureSeeded()
        val existing = dao.getPersonaById(id) ?: return false
        if (existing.isBuiltin) return false
        val deleted = dao.deleteById(id)
        if (deleted > 0) {
            val currentId = dataStore.data.map { it[currentPersonaIdKey] }.first()
            if (currentId == id) {
                dataStore.edit { prefs ->
                    prefs[currentPersonaIdKey] = BuiltinPersonas.DEFAULT_ID
                }
            }
        }
        return deleted > 0
    }
}
