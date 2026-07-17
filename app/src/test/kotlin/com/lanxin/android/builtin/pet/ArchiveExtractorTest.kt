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

package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.ArchiveExtractor
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveExtractorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun extractZip_writesFiles() {
        val zip = tmp.newFile("sample.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            zos.putNextEntry(ZipEntry("nested/hello.txt"))
            zos.write("hi".toByteArray())
            zos.closeEntry()
        }
        val dest = tmp.newFolder("out")
        ArchiveExtractor.extract(zip, dest)
        val f = File(dest, "nested/hello.txt")
        assertTrue(f.isFile)
        assertEquals("hi", f.readText())
    }

    @Test
    fun safeResolve_rejectsZipSlip() {
        val dest = tmp.newFolder("safe")
        try {
            ArchiveExtractor.safeResolve(dest, "../evil.txt")
            org.junit.Assert.fail("expected zip-slip rejection")
        } catch (e: SecurityException) {
            assertTrue(
                e.message!!.contains("zip-slip") || e.message!!.contains("outside"),
            )
        }
    }

    @Test
    fun safeResolve_rejectsAbsolutePath() {
        val dest = tmp.newFolder("safe")
        try {
            ArchiveExtractor.safeResolve(dest, "/tmp/evil.txt")
            org.junit.Assert.fail("expected absolute path rejection")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.isNotEmpty())
        }
    }

    @Test
    fun extractZip_rejectsZipSlipEntry() {
        val zip = tmp.newFile("slip.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            zos.putNextEntry(ZipEntry("../evil.txt"))
            zos.write("pwn".toByteArray())
            zos.closeEntry()
        }
        val dest = tmp.newFolder("out")
        try {
            ArchiveExtractor.extract(zip, dest)
            org.junit.Assert.fail("expected zip-slip rejection")
        } catch (e: SecurityException) {
            assertTrue(
                e.message!!.contains("outside") || e.message!!.contains("evil"),
            )
        }
        assertFalse(File(dest.parentFile, "evil.txt").exists())
    }

    @Test
    fun extractZip_rejectsNestedZipSlip() {
        val zip = tmp.newFile("nested-slip.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            zos.putNextEntry(ZipEntry("ok/../../evil2.txt"))
            zos.write("pwn".toByteArray())
            zos.closeEntry()
        }
        val dest = tmp.newFolder("out2")
        try {
            ArchiveExtractor.extract(zip, dest)
            org.junit.Assert.fail("expected nested zip-slip rejection")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("outside") || e.message!!.contains("evil"))
        }
        assertFalse(File(dest.parentFile, "evil2.txt").exists())
    }
}
