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

package com.lanxin.android.builtin.pet.domain

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * 解压 zip / tar.bz2 / tar.gz 到目标目录（防 zip-slip）。
 *
 * CodeQL java/zipslip: 每个 entry 在写出前用
 * `file.toPath().normalize().startsWith(destinationDir.toPath())` 校验。
 * 另显式拒绝绝对路径条目名（Unix 上 File(parent, "/abs") 不会逃逸 dest）。
 */
object ArchiveExtractor {

    fun extract(archive: File, destDir: File) {
        require(archive.isFile) { "归档不存在: ${archive.absolutePath}" }
        destDir.mkdirs()
        val name = archive.name.lowercase()
        when {
            name.endsWith(".zip") -> extractZip(archive, destDir)
            name.endsWith(".tar.bz2") || name.endsWith(".tbz2") ->
                extractTarCompressed(archive, destDir, bzip2 = true)
            name.endsWith(".tar.gz") || name.endsWith(".tgz") ->
                extractTarCompressed(archive, destDir, bzip2 = false)
            name.endsWith(".tar") -> extractTar(FileInputStream(archive), destDir)
            else -> throw IllegalArgumentException("不支持的归档格式: ${archive.name}")
        }
    }

    private fun extractZip(archive: File, destDir: File) {
        val destinationDir = destDir.canonicalFile
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                // CodeQL java/zipslip: guard same scope as FileOutputStream sink
                if (File(entry.name).isAbsolute) {
                    throw SecurityException("Zip entry is outside of the target dir: ${entry.name}")
                }
                val file = File(destinationDir, entry.name)
                if (!file.toPath().normalize().startsWith(destinationDir.toPath())) {
                    throw SecurityException("Zip entry is outside of the target dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { out -> zis.copyTo(out) }
                }
                zis.closeEntry()
            }
        }
    }

    private fun extractTarCompressed(archive: File, destDir: File, bzip2: Boolean) {
        val raw = BufferedInputStream(FileInputStream(archive))
        val compressed: InputStream = if (bzip2) {
            BZip2CompressorInputStream(raw)
        } else {
            GZIPInputStream(raw)
        }
        extractTar(compressed, destDir)
    }

    private fun extractTar(input: InputStream, destDir: File) {
        val destinationDir = destDir.canonicalFile
        TarArchiveInputStream(input).use { tis ->
            while (true) {
                val entry = tis.nextEntry ?: break
                if (!tis.canReadEntryData(entry)) continue
                // CodeQL java/zipslip: guard same scope as FileOutputStream sink
                if (File(entry.name).isAbsolute) {
                    throw SecurityException("Tar entry is outside of the target dir: ${entry.name}")
                }
                val file = File(destinationDir, entry.name)
                if (!file.toPath().normalize().startsWith(destinationDir.toPath())) {
                    throw SecurityException("Tar entry is outside of the target dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { out -> tis.copyTo(out) }
                }
            }
        }
    }

    /**
     * 解析归档条目相对路径；拒绝绝对路径与跳出 [destDir]（zip-slip）。
     * 供单测与调用方复用。
     */
    fun safeResolve(destDir: File, entryName: String): File {
        if (File(entryName).isAbsolute) {
            throw SecurityException("非法归档路径（zip-slip）: $entryName")
        }
        val destinationDir = destDir.canonicalFile
        val file = File(destinationDir, entryName)
        if (!file.toPath().normalize().startsWith(destinationDir.toPath())) {
            throw SecurityException("非法归档路径（zip-slip）: $entryName")
        }
        return file
    }
}
