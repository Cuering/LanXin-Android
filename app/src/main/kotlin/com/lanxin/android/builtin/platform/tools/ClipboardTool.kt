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

package com.lanxin.android.builtin.platform.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 系统剪贴板读写，基于 [ClipboardManager]。
 * 不需要额外运行时权限。
 */
@Singleton
class ClipboardTool @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val clipboard: ClipboardManager
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun get(): JsonObject {
        val cm = clipboard
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            return buildJsonObject {
                put("ok", true)
                put("empty", true)
                put("text", "")
                put("mime_types", "")
            }
        }
        val item = clip.getItemAt(0)
        val text = item.coerceToText(context)?.toString().orEmpty()
        val description = clip.description
        val mimeTypes = (0 until description.mimeTypeCount)
            .map { description.getMimeType(it) }
            .joinToString(",")
        return buildJsonObject {
            put("ok", true)
            put("empty", text.isEmpty())
            put("text", text)
            put("label", description.label?.toString().orEmpty())
            put("mime_types", mimeTypes)
            put("item_count", clip.itemCount)
        }
    }

    fun set(text: String, label: String = "lanxin"): JsonObject {
        val cm = clipboard
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        return buildJsonObject {
            put("ok", true)
            put("text", text)
            put("label", label)
            put("length", text.length)
        }
    }
}
