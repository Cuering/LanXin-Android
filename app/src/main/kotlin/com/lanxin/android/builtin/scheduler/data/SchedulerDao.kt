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

package com.lanxin.android.builtin.scheduler.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SchedulerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: SchedulerTaskEntity)

    @Update
    suspend fun update(task: SchedulerTaskEntity)

    @Query("SELECT * FROM scheduler_tasks ORDER BY created_at DESC")
    fun observeAll(): Flow<List<SchedulerTaskEntity>>

    @Query("SELECT * FROM scheduler_tasks ORDER BY created_at DESC")
    suspend fun getAll(): List<SchedulerTaskEntity>

    @Query("SELECT * FROM scheduler_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SchedulerTaskEntity?

    @Query(
        """
        SELECT * FROM scheduler_tasks
        WHERE enabled = 1 AND status IN ('IDLE', 'SCHEDULED', 'FAILED')
        ORDER BY next_run_at ASC
        """
    )
    suspend fun getEnabledTasks(): List<SchedulerTaskEntity>

    @Query(
        """
        SELECT * FROM scheduler_tasks
        WHERE (:type IS NULL OR type = :type)
          AND (:status IS NULL OR status = :status)
        ORDER BY created_at DESC
        """
    )
    suspend fun filter(type: String?, status: String?): List<SchedulerTaskEntity>

    @Query(
        """
        UPDATE scheduler_tasks
        SET next_run_at = :nextRunAt, status = :status, last_error = NULL
        WHERE id = :id
        """
    )
    suspend fun updateNextRunAndStatus(id: String, nextRunAt: Long?, status: String)

    @Query("UPDATE scheduler_tasks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query(
        """
        UPDATE scheduler_tasks
        SET last_error = :error, status = :status
        WHERE id = :id
        """
    )
    suspend fun updateLastError(id: String, error: String?, status: String)

    @Query(
        """
        UPDATE scheduler_tasks
        SET last_run_at = :lastRunAt
        WHERE id = :id
        """
    )
    suspend fun updateLastRunAt(id: String, lastRunAt: Long)

    @Query("UPDATE scheduler_tasks SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM scheduler_tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM scheduler_tasks")
    suspend fun count(): Int
}
