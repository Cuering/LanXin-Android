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

package com.lanxin.android.builtin.systemtools.data.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lanxin.android.builtin.systemtools.domain.NotesIoResult
import com.lanxin.android.builtin.systemtools.domain.NotesSafGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SAF / content Uri 读写 + 分享 Intent。
 *
 * 写：用户通过 CreateDocument 选中的 Uri 后传入 [writeText]。
 * 读：用户通过 OpenDocument 选中的 Uri 后传入 [readText]。
 * 不申请 MANAGE_EXTERNAL_STORAGE；禁止系统分区路径。
 */
@Singleton
class AndroidNotesSafGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : NotesSafGateway {

    override fun writeText(uriString: String, text: String, mimeType: String): NotesIoResult {
        return try {
            val uri = Uri.parse(uriString)
            rejectDangerousPath(uri)
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: return NotesIoResult.Error("无法打开 Uri 写入: $uriString", "uri_open_failed")
            NotesIoResult.Ok(
                message = "已写入 ${bytes.size} 字节",
                bytes = bytes.size,
                uri = uriString
            )
        } catch (e: SecurityException) {
            NotesIoResult.Error("无权限写入 Uri：${e.message}", "security")
        } catch (e: Exception) {
            NotesIoResult.Error(e.message ?: e.toString(), "write_failed")
        }
    }

    override fun readText(uriString: String): NotesIoResult {
        return try {
            val uri = Uri.parse(uriString)
            rejectDangerousPath(uri)
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
            } ?: return NotesIoResult.Error("无法打开 Uri 读取: $uriString", "uri_open_failed")
            // Ok.message 承载完整正文，供 import 解析
            NotesIoResult.Ok(
                message = text,
                bytes = text.toByteArray(StandardCharsets.UTF_8).size,
                uri = uriString
            )
        } catch (e: SecurityException) {
            NotesIoResult.Error("无权限读取 Uri：${e.message}", "security")
        } catch (e: Exception) {
            NotesIoResult.Error(e.message ?: e.toString(), "read_failed")
        }
    }

    override fun shareText(text: String, mimeType: String, chooserTitle: String): NotesIoResult {
        return try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(send, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = chooser.resolveActivity(context.packageManager)
                ?: send.resolveActivity(context.packageManager)
            if (resolved == null) {
                return NotesIoResult.Error("没有可处理分享的应用", "activity_not_found")
            }
            context.startActivity(chooser)
            NotesIoResult.Ok(
                message = "已打开分享面板",
                bytes = text.toByteArray(StandardCharsets.UTF_8).size
            )
        } catch (e: Exception) {
            NotesIoResult.Error(e.message ?: e.toString(), "share_failed")
        }
    }

    private fun rejectDangerousPath(uri: Uri) {
        val path = uri.path.orEmpty()
        val scheme = uri.scheme.orEmpty().lowercase()
        if (scheme == "file") {
            val forbidden = listOf(
                "/system", "/vendor", "/proc", "/dev", "/sys",
                "/data/system", "/data/misc"
            )
            if (forbidden.any { path == it || path.startsWith("$it/") }) {
                throw SecurityException("禁止访问系统路径: $path")
            }
        }
    }
}
