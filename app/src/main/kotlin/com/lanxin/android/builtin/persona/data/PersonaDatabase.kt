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

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PersonaEntity::class], version = 3, exportSchema = false)
@TypeConverters(PersonaTypeConverter::class)
abstract class PersonaDatabase : RoomDatabase() {
    abstract fun personaDao(): PersonaDao

    companion object {
        @Volatile
        private var INSTANCE: PersonaDatabase? = null

        /**
         * Migration 1→2: 添加 AstrBot 对齐字段。
         */
        val MIGRATION_1_2 = Migration(1, 2) { db ->
            db.execSQL("ALTER TABLE personas ADD COLUMN begin_dialogs TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE personas ADD COLUMN tools TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE personas ADD COLUMN skills TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE personas ADD COLUMN custom_error_message TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE personas ADD COLUMN folder_id TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE personas ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
        }

        /**
         * Migration 2→3: 添加 mood_imitation_dialogs（情绪风格示例对话）。
         */
        val MIGRATION_2_3 = Migration(2, 3) { db ->
            db.execSQL("ALTER TABLE personas ADD COLUMN mood_imitation_dialogs TEXT DEFAULT NULL")
        }

        fun getInstance(context: Context): PersonaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PersonaDatabase::class.java,
                    "lanxin_persona.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
