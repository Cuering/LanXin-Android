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

package com.lanxin.android.builtin.github.tools.repo

import com.lanxin.android.builtin.github.data.GithubApi
import com.lanxin.android.builtin.github.data.GithubConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * gh_repo_push — 推送文件。
 *
 * - 单文件：Contents API（path + content）
 * - 多文件：Git Data API（files 数组）
 */
@Singleton
class PushTool @Inject constructor(
    private val api: GithubApi,
    private val config: GithubConfig
) {
    suspend fun push(
        owner: String?,
        repo: String?,
        message: String,
        branch: String? = null,
        path: String? = null,
        content: String? = null,
        sha: String? = null,
        contentIsBase64: Boolean = false,
        files: JsonArray? = null
    ): JsonObject {
        val (o, r) = config.resolveRepo(owner, repo)
        require(message.isNotBlank()) { "message 必填" }

        // 多文件模式
        if (files != null && files.isNotEmpty()) {
            val branchName = branch?.takeIf { it.isNotBlank() }
                ?: error("多文件推送必须指定 branch")
            val changes = files.map { el ->
                val obj = el as? JsonObject
                    ?: error("files 元素须为对象 {path, content, ...}")
                val p = obj["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                require(p.isNotEmpty()) { "files[].path 必填" }
                val delete = obj["delete"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                val c = obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val b64 = obj["content_base64"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                    ?: obj["is_base64"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                    ?: false
                val mode = obj["mode"]?.jsonPrimitive?.contentOrNull ?: "100644"
                if (!delete) {
                    require(c.isNotEmpty() || b64) {
                        "files[].content 在非 delete 时必填"
                    }
                }
                GithubApi.FileChange(
                    path = p,
                    content = c,
                    contentIsBase64 = b64,
                    delete = delete,
                    mode = mode
                )
            }
            return api.pushFiles(o, r, branchName, message, changes)
        }

        // 单文件模式
        val singlePath = path?.trim().orEmpty()
        require(singlePath.isNotEmpty()) {
            "单文件推送需 path+content，或多文件 files[]"
        }
        require(!content.isNullOrEmpty() || contentIsBase64) { "content 必填" }
        return api.putFile(
            owner = o,
            repo = r,
            path = singlePath,
            content = content.orEmpty(),
            message = message,
            branch = branch,
            sha = sha,
            contentIsBase64 = contentIsBase64
        )
    }

    fun parseFilesArg(raw: String?): JsonArray? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonArray
        }.getOrNull()
    }

    companion object {
        fun emptyError(msg: String) = buildJsonObject {
            put("ok", false)
            put("error", msg)
        }
    }
}
