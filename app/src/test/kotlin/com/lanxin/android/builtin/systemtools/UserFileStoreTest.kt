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

package com.lanxin.android.builtin.systemtools

import com.lanxin.android.builtin.systemtools.data.files.FileDeleteDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.FileListDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.FilePickDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.FileReadTextDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.FileShareDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.FileWriteDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.InMemoryUserFileCatalog
import com.lanxin.android.builtin.systemtools.domain.DeviceToolGate
import com.lanxin.android.builtin.systemtools.domain.DeviceToolIds
import com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome
import com.lanxin.android.builtin.systemtools.domain.SystemToolsConfig
import com.lanxin.android.builtin.systemtools.domain.UserFileEntry
import com.lanxin.android.builtin.systemtools.domain.UserFileIoGateway
import com.lanxin.android.builtin.systemtools.domain.UserFileIoResult
import com.lanxin.android.builtin.systemtools.domain.UserFileProbe
import com.lanxin.android.builtin.systemtools.domain.UserFileSort
import com.lanxin.android.builtin.systemtools.domain.guessMimeFromName
import com.lanxin.android.builtin.systemtools.domain.parseUserFileSort
import com.lanxin.android.builtin.systemtools.domain.sortUserFiles
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserFileStoreTest {

    private lateinit var catalog: InMemoryUserFileCatalog
    private lateinit var io: FakeUserFileIo

    @Before
    fun setUp() {
        catalog = InMemoryUserFileCatalog()
        catalog.resetForTest()
        io = FakeUserFileIo()
    }

    @Test
    fun `sort by name date type size`() {
        val files = listOf(
            UserFileEntry("b", "b.txt", sizeBytes = 10, mimeType = "text/plain", modifiedAtEpochMs = 2),
            UserFileEntry("a", "a.md", sizeBytes = 30, mimeType = "text/markdown", modifiedAtEpochMs = 3),
            UserFileEntry("c", "c.json", sizeBytes = 5, mimeType = "application/json", modifiedAtEpochMs = 1)
        )
        assertEquals(listOf("a.md", "b.txt", "c.json"), sortUserFiles(files, UserFileSort.NAME).map { it.name })
        assertEquals("a.md", sortUserFiles(files, UserFileSort.DATE_DESC).first().name)
        assertEquals("c.json", sortUserFiles(files, UserFileSort.DATE_ASC).first().name)
        // SIZE == size_desc：大文件优先
        assertEquals("a.md", sortUserFiles(files, UserFileSort.SIZE).first().name)
        assertEquals("c.json", sortUserFiles(files, UserFileSort.SIZE).last().name)
        assertEquals("application/json", sortUserFiles(files, UserFileSort.TYPE).first().mimeType)
        assertEquals(UserFileSort.NAME, parseUserFileSort("name"))
        assertEquals(UserFileSort.DATE_DESC, parseUserFileSort(null))
        assertEquals("text/markdown", guessMimeFromName("x.md"))
    }

    @Test
    fun `catalog upsert list remove`() = runBlocking {
        catalog.upsert(
            UserFileEntry(
                id = "app:/tmp/a.txt",
                uriOrPath = "/tmp/a.txt",
                name = "a.txt",
                sizeBytes = 3,
                source = "app_private",
                modifiedAtEpochMs = 10
            )
        )
        catalog.upsert(
            UserFileEntry(
                id = "saf:content://x",
                uriOrPath = "content://x",
                name = "x",
                source = "saf",
                modifiedAtEpochMs = 20
            )
        )
        assertEquals(2, catalog.count())
        val listed = catalog.list(sort = UserFileSort.DATE_DESC, limit = 10)
        assertEquals("x", listed.first().name)
        assertTrue(catalog.remove("app:/tmp/a.txt"))
        assertEquals(1, catalog.count())
        catalog.clearAll()
        assertEquals(0, catalog.count())
    }

    @Test
    fun `file write list read via tools`() = runBlocking {
        val write = FileWriteDeviceTool(catalog, io)
        val list = FileListDeviceTool(catalog, io)
        val read = FileReadTextDeviceTool(catalog, io)

        val created = write.invoke(
            mapOf("text" to "hello lanxin", "name" to "hello.txt", "mode" to "app"),
            confirmed = true
        )
        assertTrue(created is DeviceToolOutcome.Ok)
        val data = (created as DeviceToolOutcome.Ok).data
        assertEquals(true, data["ok"])
        assertTrue(data["path"]?.toString()?.contains("hello.txt") == true)

        val listed = list.invoke(mapOf("sort" to "name"), confirmed = false)
        assertTrue(listed is DeviceToolOutcome.Ok)
        assertEquals(1, (listed as DeviceToolOutcome.Ok).data["count"])

        val readOut = read.invoke(mapOf("id" to data["id"]), confirmed = false)
        assertTrue(readOut is DeviceToolOutcome.Ok)
        assertTrue((readOut as DeviceToolOutcome.Ok).data["text"]?.toString()?.contains("hello") == true)
    }

    @Test
    fun `file pick import registers saf and app entries`() = runBlocking {
        io.probeResult = UserFileProbe(name = "picked.md", sizeBytes = 12, mimeType = "text/markdown")
        io.readBodies["content://doc/1"] = "# title"
        val pick = FilePickDeviceTool(catalog, io)
        val out = pick.invoke(
            mapOf("uri" to "content://doc/1", "import" to true),
            confirmed = true
        )
        assertTrue(out is DeviceToolOutcome.Ok)
        assertEquals(2, catalog.count())
        val names = catalog.list(limit = 10).map { it.source }.toSet()
        assertTrue(names.contains("saf"))
        assertTrue(names.contains("app_private"))
    }

    @Test
    fun `file delete requires gate confirmation`() = runBlocking {
        val write = FileWriteDeviceTool(catalog, io)
        val delete = FileDeleteDeviceTool(catalog, io)
        val ok = write.invoke(
            mapOf("text" to "x", "name" to "del.txt", "mode" to "app"),
            confirmed = true
        ) as DeviceToolOutcome.Ok
        val id = ok.data["id"]!!.toString()

        val gate = DeviceToolGate {
            SystemToolsConfig(
                masterEnabled = true,
                userFileEnabled = true,
                requireConfirmOnWrite = true
            )
        }
        val needs = gate.invoke(delete, mapOf("id" to id), confirmed = false)
        assertTrue(needs is DeviceToolOutcome.NeedsConfirmation)

        val done = gate.invoke(delete, mapOf("id" to id), confirmed = true)
        assertTrue(done is DeviceToolOutcome.Ok)
        assertEquals(0, catalog.count())
    }

    @Test
    fun `capability off denies file tools`() = runBlocking {
        val list = FileListDeviceTool(catalog, io)
        val gate = DeviceToolGate {
            SystemToolsConfig(masterEnabled = true, userFileEnabled = false)
        }
        val out = gate.invoke(list, emptyMap(), confirmed = false)
        assertTrue(out is DeviceToolOutcome.Denied)
        assertEquals("capability_disabled", (out as DeviceToolOutcome.Denied).code)
    }

    @Test
    fun `file share text mode`() = runBlocking {
        val share = FileShareDeviceTool(catalog, io)
        val out = share.invoke(mapOf("text" to "hi"), confirmed = false)
        assertTrue(out is DeviceToolOutcome.Ok)
        assertTrue(io.sharedTexts.contains("hi"))
    }

    @Test
    fun `files ready set size`() {
        assertEquals(6, DeviceToolIds.FILES_READY.size)
        assertTrue(DeviceToolIds.ALL.contains(DeviceToolIds.FILE_DELETE))
        assertTrue(DeviceToolIds.M1_STUB_READY.containsAll(DeviceToolIds.FILES_READY))
    }

    @Test
    fun `write rejects empty text`() = runBlocking {
        val write = FileWriteDeviceTool(catalog, io)
        val out = write.invoke(mapOf("mode" to "app", "name" to "a.txt"), confirmed = true)
        assertTrue(out is DeviceToolOutcome.Error)
        assertFalse(io.privateFiles.isNotEmpty() && io.privateFiles.values.any { it.isEmpty() })
    }

    /** 内存 Fake IO（不碰 Android）。 */
    private class FakeUserFileIo : UserFileIoGateway {
        val privateFiles = ConcurrentHashMap<String, String>()
        val sharedTexts = mutableListOf<String>()
        val sharedUris = mutableListOf<String>()
        var probeResult: UserFileProbe? = null
        val readBodies = ConcurrentHashMap<String, String>()

        override fun readText(uriOrPath: String, maxChars: Int): UserFileIoResult {
            val body = privateFiles[uriOrPath]
                ?: privateFiles.entries.firstOrNull { it.key.endsWith(uriOrPath) }?.value
                ?: readBodies[uriOrPath]
                ?: return UserFileIoResult.Error("not found", "not_found")
            val truncated = body.length > maxChars
            val text = body.take(maxChars)
            return UserFileIoResult.Ok(
                message = text,
                bytes = text.length,
                uri = uriOrPath,
                preview = text.take(500),
                truncated = truncated
            )
        }

        override fun writeText(uriOrPath: String, text: String, mimeType: String): UserFileIoResult {
            privateFiles[uriOrPath] = text
            return UserFileIoResult.Ok("written", bytes = text.length, uri = uriOrPath)
        }

        override fun copyToAppPrivate(uriString: String, preferredName: String?): UserFileIoResult {
            val name = preferredName ?: probeResult?.name ?: "import.bin"
            val path = "/fake/imports/$name"
            val body = readBodies[uriString] ?: "copied"
            privateFiles[path] = body
            return UserFileIoResult.Ok(
                message = "imported",
                bytes = body.length,
                uri = path,
                name = name
            )
        }

        override fun writeAppPrivateText(name: String, text: String, mimeType: String): UserFileIoResult {
            val path = "/fake/imports/$name"
            privateFiles[path] = text
            return UserFileIoResult.Ok("ok", bytes = text.length, uri = path, name = name)
        }

        override fun listAppPrivateFiles(): List<UserFileEntry> {
            return privateFiles
                .filterKeys { it.startsWith("/fake/imports/") }
                .map { (path, body) ->
                    UserFileEntry(
                        id = "app:$path",
                        uriOrPath = path,
                        name = path.substringAfterLast('/'),
                        sizeBytes = body.length.toLong(),
                        mimeType = guessMimeFromName(path),
                        modifiedAtEpochMs = System.currentTimeMillis(),
                        source = "app_private"
                    )
                }
        }

        override fun deleteAppPrivate(pathOrName: String): UserFileIoResult {
            val key = privateFiles.keys.firstOrNull {
                it == pathOrName || it.endsWith("/$pathOrName") || it.endsWith(pathOrName)
            } ?: return UserFileIoResult.Error("missing", "not_found")
            privateFiles.remove(key)
            return UserFileIoResult.Ok("deleted", uri = key, name = key.substringAfterLast('/'))
        }

        override fun shareUri(uriOrPath: String, mimeType: String?, chooserTitle: String): UserFileIoResult {
            sharedUris.add(uriOrPath)
            return UserFileIoResult.Ok("shared", uri = uriOrPath)
        }

        override fun shareText(text: String, mimeType: String, chooserTitle: String): UserFileIoResult {
            sharedTexts.add(text)
            return UserFileIoResult.Ok("shared", bytes = text.length)
        }

        override fun probe(uriString: String): UserFileProbe? = probeResult

        override fun takePersistableIfPossible(uriString: String): Boolean = uriString.startsWith("content://")

        override fun deleteDocument(uriString: String): UserFileIoResult {
            if (uriString.startsWith("content://")) {
                readBodies.remove(uriString)
                return UserFileIoResult.Ok("deleted", uri = uriString)
            }
            return deleteAppPrivate(uriString)
        }
    }
}
