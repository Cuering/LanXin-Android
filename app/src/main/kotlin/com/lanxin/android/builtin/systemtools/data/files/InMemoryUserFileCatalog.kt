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

package com.lanxin.android.builtin.systemtools.data.files

import com.lanxin.android.builtin.systemtools.domain.UserFileCatalog
import com.lanxin.android.builtin.systemtools.domain.UserFileEntry
import com.lanxin.android.builtin.systemtools.domain.UserFileSort
import com.lanxin.android.builtin.systemtools.domain.UserFileSource
import com.lanxin.android.builtin.systemtools.domain.sortUserFiles
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内存用户文件目录（真机登记表 + 单测）。
 * 不持久化到 Room；应用重启后仅剩应用私有目录实体文件，由 [UserFileIoGateway] 重新扫描。
 */
@Singleton
class InMemoryUserFileCatalog @Inject constructor() : UserFileCatalog {

    private val entries = CopyOnWriteArrayList<UserFileEntry>()

    override suspend fun list(
        sort: UserFileSort,
        limit: Int,
        source: UserFileSource?
    ): List<UserFileEntry> {
        val n = limit.coerceIn(1, 500)
        val filtered = if (source == null) {
            entries.toList()
        } else {
            val key = when (source) {
                UserFileSource.SAF -> "saf"
                UserFileSource.APP_PRIVATE -> "app_private"
            }
            entries.filter { it.source == key }
        }
        return sortUserFiles(filtered, sort).take(n)
    }

    override suspend fun get(id: String): UserFileEntry? =
        entries.firstOrNull { it.id == id || it.uriOrPath == id }

    override suspend fun upsert(entry: UserFileEntry): UserFileEntry {
        val idx = entries.indexOfFirst { it.id == entry.id || it.uriOrPath == entry.uriOrPath }
        if (idx >= 0) {
            entries[idx] = entry
        } else {
            entries.add(entry)
        }
        return entry
    }

    override suspend fun remove(id: String): Boolean {
        val idx = entries.indexOfFirst { it.id == id || it.uriOrPath == id }
        if (idx < 0) return false
        entries.removeAt(idx)
        return true
    }

    override suspend fun count(): Int = entries.size

    override suspend fun clearAll() {
        entries.clear()
    }

    /** 单测重置。 */
    fun resetForTest() {
        entries.clear()
    }
}
