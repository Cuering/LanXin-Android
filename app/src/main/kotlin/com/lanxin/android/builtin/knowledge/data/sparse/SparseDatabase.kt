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

package com.lanxin.android.builtin.knowledge.data.sparse

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 稀疏检索专用 Room 库（FTS content + FTS 虚拟表）。
 * 与 ObjectBox 向量库、Memory Room 库分离。
 */
@Database(
    entities = [
        SparseFtsContentEntity::class,
        SparseFtsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SparseDatabase : RoomDatabase() {
    abstract fun sparseFtsDao(): SparseFtsDao

    companion object {
        @Volatile
        private var INSTANCE: SparseDatabase? = null

        const val DB_NAME = "lanxin_sparse.db"

        fun getInstance(context: Context): SparseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SparseDatabase::class.java,
                    DB_NAME
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
