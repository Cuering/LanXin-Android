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
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * 解压 zip / tar.bz2 / tar.gz 到目标目录（防 zip-slip）。
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
        val root = destDir.canonicalFile
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                // codeql[java/zipslip] — validate before use
                val outFile = safeResolve(root, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out -> zis.copyTo(out) }
                }
                zis.closeEntry()
            }
        }
    }

    private fun extractTarCompressed(archive: File, destDir: File, bzip2: Boolean) {
        val raw = BufferedInputStream(FileInputStream(archive))
        val compressed = if (bzip2) {
            BZip2CompressorInputStream(raw)
        } else {
            GZIPInputStream(raw)
        }
        extractTar(compressed, destDir)
    }

    private fun extractTar(input: java.io.InputStream, destDir: File) {
        val root = destDir.canonicalFile
        TarArchiveInputStream(input).use { tis ->
            while (true) {
                val entry = tis.nextEntry ?: break
                if (!tis.canReadEntryData(entry)) continue
                // codeql[java/zipslip] — validate before use
                val outFile = safeResolve(root, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out -> tis.copyTo(out) }
                }
            }
        }
    }

    /**
     * 解析归档条目相对路径到目标目录；拒绝跳出 [root]（zip-slip 防护）。
     *
     * @param root 已 canonical（由调用方计算一次即可）
     * @param entryName 归档内相对路径
     * @throws IllegalStateException 若路径尝试跳出 root
     */
    fun safeResolve(root: File, entryName: String): File {
        // 用 canonical 解析所有 .. 和符号链接
        val dest = File(root, entryName).canonicalFile
        val rootPath = root.canonicalPath
        val destPath = dest.canonicalPath
        require(destPath.startsWith(rootPath + File.separator) || destPath == rootPath) {
            "非法归档路径（zip-slip）: $entryName -> $destPath"
        }
        return dest
    }
}
