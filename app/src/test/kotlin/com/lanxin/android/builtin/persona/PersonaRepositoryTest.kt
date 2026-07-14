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

package com.lanxin.android.builtin.persona

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.lanxin.android.builtin.persona.data.PersonaDao
import com.lanxin.android.builtin.persona.data.PersonaEntity
import com.lanxin.android.builtin.persona.domain.BuiltinPersonas
import com.lanxin.android.builtin.persona.domain.PersonaRepository
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersonaRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dao: FakePersonaDao
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: PersonaRepository

    @Before
    fun setup() {
        dao = FakePersonaDao()
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "persona_test.preferences_pb") }
        )
        repository = PersonaRepository(dao, dataStore)
    }

    @Test
    fun `ensureSeeded inserts builtin personas`() = runBlocking {
        repository.ensureSeeded()
        val all = repository.getAllOnce()
        assertEquals(3, all.size)
        assertTrue(all.any { it.id == BuiltinPersonas.DEFAULT_ID && it.isBuiltin })
        assertTrue(all.any { it.id == BuiltinPersonas.CUTE_ID })
        assertTrue(all.any { it.id == BuiltinPersonas.PROFESSIONAL_ID })
    }

    @Test
    fun `switchPersona updates current id`() = runBlocking {
        repository.ensureSeeded()
        val ok = repository.switchPersona(BuiltinPersonas.CUTE_ID)
        assertTrue(ok)
        val current = repository.getCurrent()
        assertEquals(BuiltinPersonas.CUTE_ID, current.id)
        assertEquals("可爱风格", current.name)
    }

    @Test
    fun `switchPersona fails for unknown id`() = runBlocking {
        repository.ensureSeeded()
        val ok = repository.switchPersona("not-exist")
        assertFalse(ok)
        assertEquals(BuiltinPersonas.DEFAULT_ID, repository.getCurrent().id)
    }

    @Test
    fun `create and update custom persona`() = runBlocking {
        repository.ensureSeeded()
        val created = repository.createPersona("自定义", "你是测试人格")
        assertFalse(created.isBuiltin)
        assertNotNull(created.id)

        val updated = repository.updatePersona(created.id, "自定义2", "新的 prompt")
        assertTrue(updated)
        val loaded = repository.getById(created.id)
        assertEquals("自定义2", loaded?.name)
        assertEquals("新的 prompt", loaded?.systemPrompt)
    }

    @Test
    fun `delete custom persona resets selection when needed`() = runBlocking {
        repository.ensureSeeded()
        val created = repository.createPersona("临时", "prompt")
        assertTrue(repository.switchPersona(created.id))
        assertEquals(created.id, repository.getCurrent().id)

        assertTrue(repository.deletePersona(created.id))
        assertEquals(BuiltinPersonas.DEFAULT_ID, repository.getCurrent().id)
    }

    @Test
    fun `cannot delete builtin persona`() = runBlocking {
        repository.ensureSeeded()
        assertFalse(repository.deletePersona(BuiltinPersonas.DEFAULT_ID))
        assertNotNull(repository.getById(BuiltinPersonas.DEFAULT_ID))
    }

    @Test
    fun `getCurrentSystemPrompt returns active persona prompt`() = runBlocking {
        repository.ensureSeeded()
        repository.switchPersona(BuiltinPersonas.PROFESSIONAL_ID)
        val prompt = repository.getCurrentSystemPrompt()
        assertTrue(prompt.contains("专业"))
    }

    private class FakePersonaDao : PersonaDao {
        private val store = MutableStateFlow<Map<String, PersonaEntity>>(emptyMap())

        override fun getAllPersonas(): Flow<List<PersonaEntity>> =
            store.map { map ->
                map.values.sortedWith(
                    compareByDescending<PersonaEntity> { it.isBuiltin }
                        .thenByDescending { it.updatedAt }
                )
            }

        override suspend fun getAllPersonasOnce(): List<PersonaEntity> =
            getAllPersonas().first()

        override suspend fun getPersonaById(id: String): PersonaEntity? = store.value[id]

        override suspend fun upsert(persona: PersonaEntity) {
            store.update { it + (persona.id to persona) }
        }

        override suspend fun insertIgnore(persona: PersonaEntity): Long {
            if (store.value.containsKey(persona.id)) return -1L
            store.update { it + (persona.id to persona) }
            return 1L
        }

        override suspend fun insertAllIgnore(personas: List<PersonaEntity>) {
            personas.forEach { insertIgnore(it) }
        }

        override suspend fun update(persona: PersonaEntity) {
            if (store.value.containsKey(persona.id)) {
                store.update { it + (persona.id to persona) }
            }
        }

        override suspend fun deleteById(id: String): Int {
            val existing = store.value[id] ?: return 0
            if (existing.isBuiltin) return 0
            store.update { it - id }
            return 1
        }

        override suspend fun count(): Int = store.value.size
    }
}
