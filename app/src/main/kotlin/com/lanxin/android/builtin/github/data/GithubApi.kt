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

package com.lanxin.android.builtin.github.data

import com.lanxin.android.data.network.NetworkClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * GitHub REST API 封装（Ktor NetworkClient）。
 *
 * 覆盖 Contents / Repos / Pulls / Issues / Git Data / Search 等本模块所需端点。
 * 认证：Bearer Token（PAT 或 fine-grained token）。
 */
@Singleton
class GithubApi @Inject constructor(
    private val networkClient: NetworkClient,
    private val config: GithubConfig
) {
    private val client get() = networkClient()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }

    // ── Config helpers ──────────────────────────────────────────────

    suspend fun authHeaders(): Map<String, String> {
        val token = config.requireToken()
        return mapOf(
            HttpHeaders.Authorization to "Bearer $token",
            HttpHeaders.Accept to "application/vnd.github+json",
            "X-GitHub-Api-Version" to API_VERSION
        )
    }

    private suspend fun baseUrl(): String = config.getBaseUrl().trimEnd('/')

    // ── Low-level HTTP ──────────────────────────────────────────────

    private suspend fun get(
        path: String,
        params: Map<String, String> = emptyMap()
    ): ApiResult {
        val headers = authHeaders()
        val url = "${baseUrl()}$path"
        val response = client.get(url) {
            headers.forEach { (k, v) -> header(k, v) }
            params.forEach { (k, v) -> parameter(k, v) }
        }
        return parseResponse(response)
    }

    private suspend fun post(path: String, body: JsonObject): ApiResult {
        val headers = authHeaders()
        val url = "${baseUrl()}$path"
        val response = client.post(url) {
            headers.forEach { (k, v) -> header(k, v) }
            setBody(body.toString())
        }
        return parseResponse(response)
    }

    private suspend fun put(path: String, body: JsonObject): ApiResult {
        val headers = authHeaders()
        val url = "${baseUrl()}$path"
        val response = client.put(url) {
            headers.forEach { (k, v) -> header(k, v) }
            setBody(body.toString())
        }
        return parseResponse(response)
    }

    private suspend fun delete(path: String, body: JsonObject? = null): ApiResult {
        val headers = authHeaders()
        val url = "${baseUrl()}$path"
        val response = client.delete(url) {
            headers.forEach { (k, v) -> header(k, v) }
            if (body != null) setBody(body.toString())
        }
        return parseResponse(response)
    }

    private suspend fun patch(path: String, body: JsonObject): ApiResult {
        val headers = authHeaders()
        val url = "${baseUrl()}$path"
        val response = client.request(url) {
            method = HttpMethod.Patch
            headers.forEach { (k, v) -> header(k, v) }
            setBody(body.toString())
        }
        return parseResponse(response)
    }

    private suspend fun parseResponse(response: HttpResponse): ApiResult {
        val text = response.bodyAsText()
        val code = response.status.value
        if (!response.status.isSuccess()) {
            val message = extractErrorMessage(text) ?: "HTTP $code"
            return ApiResult.Error(code, message, text)
        }
        if (text.isBlank()) {
            return ApiResult.Success(JsonObject(emptyMap()), code)
        }
        val element = runCatching { json.parseToJsonElement(text) }.getOrElse {
            return ApiResult.Error(code, "响应 JSON 解析失败：${it.message}", text)
        }
        return ApiResult.Success(element, code)
    }

    private fun extractErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            val msg = obj["message"]?.jsonPrimitive?.contentOrNull
            val errors = obj["errors"]?.jsonArray
            if (errors != null && errors.isNotEmpty()) {
                val detail = errors.joinToString("; ") { e ->
                    val o = e as? JsonObject
                    o?.get("message")?.jsonPrimitive?.contentOrNull
                        ?: o?.get("code")?.jsonPrimitive?.contentOrNull
                        ?: e.toString()
                }
                if (msg != null) "$msg ($detail)" else detail
            } else {
                msg
            }
        }.getOrNull()
    }

    // ── Repo contents ───────────────────────────────────────────────

    /**
     * GET /repos/{owner}/{repo}/contents/{path}
     * 文件返回 content（base64）+ sha；目录返回数组。
     */
    suspend fun getContents(
        owner: String,
        repo: String,
        path: String = "",
        ref: String? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        val cleanPath = path.trim().trimStart('/')
        val endpoint = if (cleanPath.isEmpty()) {
            "/repos/$owner/$repo/contents"
        } else {
            "/repos/$owner/$repo/contents/$cleanPath"
        }
        val params = buildMap {
            if (!ref.isNullOrBlank()) put("ref", ref)
        }
        when (val result = get(endpoint, params)) {
            is ApiResult.Error -> err(result)
            is ApiResult.Success -> {
                val el = result.body
                when {
                    el is JsonArray -> buildJsonObject {
                        put("ok", true)
                        put("type", "dir")
                        put("path", cleanPath.ifEmpty { "/" })
                        put("count", el.size)
                        put(
                            "entries",
                            buildJsonArray {
                                el.forEach { item ->
                                    val o = item.jsonObject
                                    add(
                                        buildJsonObject {
                                            put("name", o.str("name"))
                                            put("path", o.str("path"))
                                            put("type", o.str("type"))
                                            put("sha", o.str("sha"))
                                            put("size", o.long("size"))
                                            put("html_url", o.str("html_url"))
                                            put("download_url", o.str("download_url"))
                                        }
                                    )
                                }
                            }
                        )
                    }
                    el is JsonObject -> {
                        val type = el.str("type")
                        val encoding = el.str("encoding")
                        val rawContent = el.str("content")
                        val decoded = if (type == "file" && encoding == "base64" && rawContent != null) {
                            decodeBase64Content(rawContent)
                        } else {
                            null
                        }
                        buildJsonObject {
                            put("ok", true)
                            put("type", type ?: "file")
                            put("name", el.str("name"))
                            put("path", el.str("path"))
                            put("sha", el.str("sha"))
                            put("size", el.long("size"))
                            put("encoding", encoding)
                            put("html_url", el.str("html_url"))
                            put("download_url", el.str("download_url"))
                            if (decoded != null) {
                                put("content", decoded)
                                put("content_base64", rawContent?.replace("\n", ""))
                            } else if (rawContent != null) {
                                put("content_base64", rawContent.replace("\n", ""))
                            }
                        }
                    }
                    else -> errMsg("未知响应类型")
                }
            }
        }
    }

    // ── Push (Contents API single file / Git Data multi) ────────────

    /**
     * 单文件：PUT /repos/{owner}/{repo}/contents/{path}
     */
    suspend fun putFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        branch: String? = null,
        sha: String? = null,
        contentIsBase64: Boolean = false
    ): JsonObject = withContext(Dispatchers.IO) {
        val cleanPath = path.trim().trimStart('/')
        require(cleanPath.isNotEmpty()) { "path 必填" }
        require(message.isNotBlank()) { "message 必填" }

        val encoded = if (contentIsBase64) {
            content.replace("\n", "").trim()
        } else {
            Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
        }

        val body = buildJsonObject {
            put("message", message)
            put("content", encoded)
            if (!branch.isNullOrBlank()) put("branch", branch)
            if (!sha.isNullOrBlank()) put("sha", sha)
        }

        when (val result = put("/repos/$owner/$repo/contents/$cleanPath", body)) {
            is ApiResult.Error -> {
                // 若未传 sha 且是更新冲突，尝试自动取 sha 重试一次
                if (sha.isNullOrBlank() && result.code == 422) {
                    val existing = getContents(owner, repo, cleanPath, branch)
                    val existingSha = existing["sha"]?.jsonPrimitive?.contentOrNull
                    if (existingSha != null) {
                        return@withContext putFile(
                            owner, repo, path, content, message, branch, existingSha, contentIsBase64
                        )
                    }
                }
                err(result)
            }
            is ApiResult.Success -> {
                val o = result.body as? JsonObject ?: return@withContext errMsg("响应非对象")
                val contentObj = o["content"] as? JsonObject
                val commitObj = o["commit"] as? JsonObject
                buildJsonObject {
                    put("ok", true)
                    put("path", contentObj?.str("path") ?: cleanPath)
                    put("sha", contentObj?.str("sha"))
                    put("html_url", contentObj?.str("html_url"))
                    put("commit_sha", commitObj?.str("sha"))
                    put("commit_url", commitObj?.str("html_url"))
                    put("message", message)
                }
            }
        }
    }

    /**
     * 多文件原子提交：Git Data API
     * 1. GET ref → commit sha
     * 2. GET commit → tree sha
     * 3. POST blobs for each file
     * 4. POST tree
     * 5. POST commit
     * 6. PATCH ref
     */
    suspend fun pushFiles(
        owner: String,
        repo: String,
        branch: String,
        message: String,
        files: List<FileChange>
    ): JsonObject = withContext(Dispatchers.IO) {
        require(branch.isNotBlank()) { "branch 必填" }
        require(message.isNotBlank()) { "message 必填" }
        require(files.isNotEmpty()) { "files 不能为空" }

        // 1. ref
        val refResult = get("/repos/$owner/$repo/git/ref/heads/$branch")
        val baseCommitSha = when (refResult) {
            is ApiResult.Error -> {
                // 空仓库或分支不存在：尝试从 default branch / 创建
                return@withContext err(refResult)
            }
            is ApiResult.Success -> {
                val obj = refResult.body as? JsonObject
                    ?: return@withContext errMsg("ref 响应异常")
                obj["object"]?.jsonObject?.str("sha")
                    ?: return@withContext errMsg("无法解析 ref sha")
            }
        }

        // 2. commit → base tree
        val commitResult = get("/repos/$owner/$repo/git/commits/$baseCommitSha")
        val baseTreeSha = when (commitResult) {
            is ApiResult.Error -> return@withContext err(commitResult)
            is ApiResult.Success -> {
                val obj = commitResult.body as? JsonObject
                    ?: return@withContext errMsg("commit 响应异常")
                obj["tree"]?.jsonObject?.str("sha")
                    ?: return@withContext errMsg("无法解析 tree sha")
            }
        }

        // 3. blobs
        val treeItems = mutableListOf<JsonObject>()
        for (file in files) {
            val cleanPath = file.path.trim().trimStart('/')
            require(cleanPath.isNotEmpty()) { "file.path 不能为空" }

            if (file.delete) {
                treeItems += buildJsonObject {
                    put("path", cleanPath)
                    put("mode", "100644")
                    put("type", "blob")
                    put("sha", JsonNull)
                }
                continue
            }

            val contentBytes = if (file.contentIsBase64) {
                Base64.getDecoder().decode(file.content.replace("\n", "").trim())
            } else {
                file.content.toByteArray(Charsets.UTF_8)
            }
            val blobBody = buildJsonObject {
                put("content", Base64.getEncoder().encodeToString(contentBytes))
                put("encoding", "base64")
            }
            when (val blobResult = post("/repos/$owner/$repo/git/blobs", blobBody)) {
                is ApiResult.Error -> return@withContext err(blobResult)
                is ApiResult.Success -> {
                    val blobSha = (blobResult.body as? JsonObject)?.str("sha")
                        ?: return@withContext errMsg("blob 无 sha")
                    treeItems += buildJsonObject {
                        put("path", cleanPath)
                        put("mode", file.mode)
                        put("type", "blob")
                        put("sha", blobSha)
                    }
                }
            }
        }

        // 4. tree
        val treeBody = buildJsonObject {
            put("base_tree", baseTreeSha)
            put(
                "tree",
                buildJsonArray {
                    treeItems.forEach { add(it) }
                }
            )
        }
        val newTreeSha = when (val treeResult = post("/repos/$owner/$repo/git/trees", treeBody)) {
            is ApiResult.Error -> return@withContext err(treeResult)
            is ApiResult.Success -> {
                (treeResult.body as? JsonObject)?.str("sha")
                    ?: return@withContext errMsg("tree 无 sha")
            }
        }

        // 5. commit
        val commitBody = buildJsonObject {
            put("message", message)
            put("tree", newTreeSha)
            put(
                "parents",
                buildJsonArray {
                    add(JsonPrimitive(baseCommitSha))
                }
            )
        }
        val newCommitSha = when (val cResult = post("/repos/$owner/$repo/git/commits", commitBody)) {
            is ApiResult.Error -> return@withContext err(cResult)
            is ApiResult.Success -> {
                (cResult.body as? JsonObject)?.str("sha")
                    ?: return@withContext errMsg("commit 无 sha")
            }
        }

        // 6. update ref (PATCH)
        val refBody = buildJsonObject {
            put("sha", newCommitSha)
            put("force", false)
        }
        when (val updateResult = patch("/repos/$owner/$repo/git/refs/heads/$branch", refBody)) {
            is ApiResult.Error -> return@withContext err(updateResult)
            is ApiResult.Success -> { /* ok */ }
        }

        buildJsonObject {
            put("ok", true)
            put("branch", branch)
            put("commit_sha", newCommitSha)
            put("tree_sha", newTreeSha)
            put("base_commit_sha", baseCommitSha)
            put("files_count", files.size)
            put(
                "files",
                buildJsonArray {
                    files.forEach { f ->
                        add(
                            buildJsonObject {
                                put("path", f.path)
                                put("delete", f.delete)
                            }
                        )
                    }
                }
            )
            put("message", message)
            put("html_url", "https://github.com/$owner/$repo/commit/$newCommitSha")
        }
    }

    // ── Search / Create repo ────────────────────────────────────────

    suspend fun searchRepos(
        query: String,
        sort: String? = null,
        order: String? = null,
        perPage: Int = 10,
        page: Int = 1
    ): JsonObject = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "query 必填" }
        val params = buildMap {
            put("q", query)
            put("per_page", perPage.coerceIn(1, 100).toString())
            put("page", page.coerceAtLeast(1).toString())
            if (!sort.isNullOrBlank()) put("sort", sort)
            if (!order.isNullOrBlank()) put("order", order)
        }
        when (val result = get("/search/repositories", params)) {
            is ApiResult.Error -> err(result)
            is ApiResult.Success -> {
                val o = result.body as? JsonObject ?: return@withContext errMsg("响应异常")
                val items = o["items"]?.jsonArray ?: JsonArray(emptyList())
                buildJsonObject {
                    put("ok", true)
                    put("total_count", o.long("total_count") ?: 0)
                    put("incomplete_results", o["incomplete_results"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false)
                    put("returned", items.size)
                    put(
                        "items",
                        buildJsonArray {
                            items.forEach { item ->
                                val r = item.jsonObject
                                add(
                                    buildJsonObject {
                                        put("id", r.long("id"))
                                        put("full_name", r.str("full_name"))
                                        put("name", r.str("name"))
                                        put("owner", r["owner"]?.jsonObject?.str("login"))
                                        put("description", r.str("description"))
                                        put("html_url", r.str("html_url"))
                                        put("private", r.bool("private"))
                                        put("fork", r.bool("fork"))
                                        put("language", r.str("language"))
                                        put("stargazers_count", r.long("stargazers_count"))
                                        put("forks_count", r.long("forks_count"))
                                        put("open_issues_count", r.long("open_issues_count"))
                                        put("default_branch", r.str("default_branch"))
                                        put("updated_at", r.str("updated_at"))
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    suspend fun createRepo(
        name: String,
        description: String? = null,
        private: Boolean = false,
        autoInit: Boolean = false,
        org: String? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "name 必填" }
        val body = buildJsonObject {
            put("name", name)
            if (!description.isNullOrBlank()) put("description", description)
            put("private", private)
            put("auto_init", autoInit)
        }
        val path = if (!org.isNullOrBlank()) {
            "/orgs/$org/repos"
        } else {
            "/user/repos"
        }
        when (val result = post(path, body)) {
            is ApiResult.Error -> err(result)
            is ApiResult.Success -> {
                val o = result.body as? JsonObject ?: return@withContext errMsg("响应异常")
                buildJsonObject {
                    put("ok", true)
                    put("id", o.long("id"))
                    put("full_name", o.str("full_name"))
                    put("name", o.str("name"))
                    put("owner", o["owner"]?.jsonObject?.str("login"))
                    put("html_url", o.str("html_url"))
                    put("clone_url", o.str("clone_url"))
                    put("ssh_url", o.str("ssh_url"))
                    put("private", o.bool("private"))
                    put("default_branch", o.str("default_branch"))
                }
            }
        }
    }

    // ── Pull requests ───────────────────────────────────────────────

    suspend fun listPulls(
        owner: String,
        repo: String,
        state: String = "open",
        head: String? = null,
        base: String? = null,
        sort: String? = null,
        direction: String? = null,
        perPage: Int = 20,
        page: Int = 1
    ): JsonObject = withContext(Dispatchers.IO) {
        val params = buildMap {
            put("state", state)
            put("per_page", perPage.coerceIn(1, 100).toString())
            put("page", page.coerceAtLeast(1).toString())
            if (!head.isNullOrBlank()) put("head", head)
            if (!base.isNullOrBlank()) put("base", base)
            if (!sort.isNullOrBlank()) put("sort", sort)
            if (!direction.isNullOrBlank()) put("direction", direction)
        }
        when (val result = get("/repos/$owner/$repo/pulls", params)) {
            is ApiResult.Error -> err(result)
            is ApiResult.Success -> {
                val arr = result.body as? JsonArray ?: JsonArray(emptyList())
                buildJsonObject {
                    put("ok", true)
                    put("count", arr.size)
                    put(
                        "pulls",
                        buildJsonArray {
                            arr.forEach { item ->
                                add(summarizePull(item.jsonObject))
                            }
                        }
                    )
                }
            }
        }
    }

    suspend fun createPull(
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String? = null,
        draft: Boolean = false
    ): JsonObject = withContext(Dispatchers.IO) {
        require(title.isNotBlank()) { "title 必填" }
        require(head.isNotBlank()) { "head 必填" }
        require(base.isNotBlank()) { "base 必填" }
        val req = buildJsonObject {
            put("title", title)
            put("head", head)
            put("base", base)
            if (!body.isNullOrBlank()) put("body", body)
            put("draft", draft)
        }
        when (val result = post("/repos/$owner/$repo/pulls", req)) {
            is ApiResult.Error -> err(result)
            is ApiResult.Success -> {
                val o = result.body as? JsonObject ?: return@withContext errMsg("响应异常")
                buildJsonObject {
                    put("ok", true)
                    put("pull", summarizePull(o))
                }
            }
        }
    }

    suspend fun mergePull(
        owner: String,
        repo: String,
        pullNumber: Int,
        commitTitle: String? = null,
        commitMessage: String? = null,
        mergeMethod: String = "merge"
    ): JsonObject = withContext(Dispatchers.IO) {
        require(pullNumber > 0) { "pull_number 无效" }
        val method = mergeMethod.lowercase().ifBlank { "merge" }
        require(method in setOf("merge", "squash", "rebase")) {
            "merge_method 须为 merge / squash / rebase"
        }
        val req = buildJsonObject {
            if (!commitTitle.isNullOrBlank()) put("commit_title", commitTitle)
            if (!commitMessage.isNullOrBlank()) put("commit_message", commitMessage)
            put("merge_method", method)
        }
        when (val result = put("/repos/$owner/$repo/pulls/$pullNumber/merge", req)) {
            is ApiResult.Error -> err(result)
            is ApiResult.Success -> {
                val o = result.body as? JsonObject ?: return@withContext errMsg("响应异常")
                buildJsonObject {
                    put("ok", true)
                    put("merged", o.bool("merged") ?: true)
                    put("message", o.str("message"))
                    put("sha", o.str("sha"))
                    put("pull_number", pullNumber)
                }
            }
        }
    }

    private fun summarizePull(o: JsonObject): JsonObject = buildJsonObject {
        put("number", o.long("number"))
        put("title", o.str("title"))
        put("state", o.str("state"))
        put("draft", o.bool("draft"))
        put("html_url", o.str("html_url"))
        put("user", o["user"]?.jsonObject?.str("login"))
        put("head", o["head"]?.jsonObject?.str("ref"))
        put("base", o["base"]?.jsonObject?.str("ref"))
        put("merged", o.bool("merged"))
        put("mergeable", o.bool("mergeable"))
        put("created_at", o.str("created_at"))
        put("updated_at", o.str("updated_at"))
        put("body", o.str("body")?.take(500))
    }

    // ── Issues ──────────────────────────────────────────────────────

    suspend fun listIssues(
        owner: String,
        repo: String,
        state: String = "open",
        labels: String? = null,
        sort: String? = null,
        direction: String? = null,
        since: String? = null,
        perPage: Int = 20,
        page: Int = 1
    ): JsonObject = withContext(Dispatchers.IO) {
        val params = buildMap {
            put("state", state)
            put("per_page", perPage.coerceIn(1, 100).toString())
            put("page", page.coerceAtLeast(1).toString())
            // GitHub issues API 会混入 PR；调用方可用 filter 或我们本地过滤
            if (!labels.isNullOrBlank()) put("labels", labels)
            if (!sort.isNullOrBlank()) put("sort", sort)
            if (!direction.isNullOrBlank()) put("direction", direction)
            if (!since.isNullOrBlank()) put("since", since)
        }
        when (val result = get("/repos/$owner/$repo/issues", params)) {
            is ApiResult.Error -> err(result)
            is ApiResult.Success -> {
                val arr = result.body as? JsonArray ?: JsonArray(emptyList())
                // 过滤掉 pull_request 字段存在的条目
                val issues = arr.mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    if (o.containsKey("pull_request")) null else o
                }
                buildJsonObject {
                    put("ok", true)
                    put("count", issues.size)
                    put(
                        "issues",
                        buildJsonArray {
                            issues.forEach { o ->
                                add(summarizeIssue(o))
                            }
                        }
                    )
                }
            }
        }
    }

    suspend fun createIssue(
        owner: String,
        repo: String,
        title: String,
        body: String? = null,
        labels: List<String>? = null,
        assignees: List<String>? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        require(title.isNotBlank()) { "title 必填" }
        val req = buildJsonObject {
            put("title", title)
            if (!body.isNullOrBlank()) put("body", body)
            if (!labels.isNullOrEmpty()) {
                put(
                    "labels",
                    buildJsonArray {
                        labels.forEach { add(JsonPrimitive(it)) }
                    }
                )
            }
            if (!assignees.isNullOrEmpty()) {
                put(
                    "assignees",
                    buildJsonArray {
                        assignees.forEach { add(JsonPrimitive(it)) }
                    }
                )
            }
        }
        when (val result = post("/repos/$owner/$repo/issues", req)) {
            is ApiResult.Error -> err(result)
            is ApiResult.Success -> {
                val o = result.body as? JsonObject ?: return@withContext errMsg("响应异常")
                buildJsonObject {
                    put("ok", true)
                    put("issue", summarizeIssue(o))
                }
            }
        }
    }

    private fun summarizeIssue(o: JsonObject): JsonObject = buildJsonObject {
        put("number", o.long("number"))
        put("title", o.str("title"))
        put("state", o.str("state"))
        put("html_url", o.str("html_url"))
        put("user", o["user"]?.jsonObject?.str("login"))
        put("created_at", o.str("created_at"))
        put("updated_at", o.str("updated_at"))
        put("body", o.str("body")?.take(500))
        val labels = o["labels"]?.jsonArray
        if (labels != null) {
            put(
                "labels",
                buildJsonArray {
                    labels.forEach { l ->
                        val name = when (l) {
                            is JsonObject -> l.str("name")
                            is JsonPrimitive -> l.contentOrNull
                            else -> null
                        }
                        if (name != null) add(JsonPrimitive(name))
                    }
                }
            )
        }
    }

    // ── Branch / commits / compare ──────────────────────────────────

    suspend fun createBranch(
        owner: String,
        repo: String,
        branch: String,
        fromBranch: String? = null,
        fromSha: String? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        require(branch.isNotBlank()) { "branch 必填" }
        val sha = when {
            !fromSha.isNullOrBlank() -> fromSha
            else -> {
                val refName = fromBranch?.takeIf { it.isNotBlank() } ?: run {
                    // default branch
                    when (val r = get("/repos/$owner/$repo")) {
                        is ApiResult.Error -> return@withContext err(r)
                        is ApiResult.Success -> {
                            (r.body as? JsonObject)?.str("default_branch") ?: "main"
                        }
                    }
                }
                when (val ref = get("/repos/$owner/$repo/git/ref/heads/$refName")) {
                    is ApiResult.Error -> return@withContext err(ref)
                    is ApiResult.Success -> {
                        (ref.body as? JsonObject)?.get("object")?.jsonObject?.str("sha")
                            ?: return@withContext errMsg("无法解析源分支 sha")
                    }
                }
            }
        }
        val body = buildJsonObject {
            put("ref", "refs/heads/$branch")
            put("sha", sha)
        }
        when (val result = post("/repos/$owner/$repo/git/refs", body)) {
            is ApiResult.Error -> err(result)
            is ApiResult.Success -> {
                val o = result.body as? JsonObject ?: return@withContext errMsg("响应异常")
                buildJsonObject {
                    put("ok", true)
                    put("ref", o.str("ref"))
                    put("sha", o["object"]?.jsonObject?.str("sha") ?: sha)
                    put("branch", branch)
                    put("from_sha", sha)
                    put("url", o.str("url"))
                }
            }
        }
    }

    suspend fun listCommits(
        owner: String,
        repo: String,
        sha: String? = null,
        path: String? = null,
        author: String? = null,
        since: String? = null,
        until: String? = null,
        perPage: Int = 20,
        page: Int = 1
    ): JsonObject = withContext(Dispatchers.IO) {
        val params = buildMap {
            put("per_page", perPage.coerceIn(1, 100).toString())
            put("page", page.coerceAtLeast(1).toString())
            if (!sha.isNullOrBlank()) put("sha", sha)
            if (!path.isNullOrBlank()) put("path", path)
            if (!author.isNullOrBlank()) put("author", author)
            if (!since.isNullOrBlank()) put("since", since)
            if (!until.isNullOrBlank()) put("until", until)
        }
        when (val result = get("/repos/$owner/$repo/commits", params)) {
            is ApiResult.Error -> err(result)
            is ApiResult.Success -> {
                val arr = result.body as? JsonArray ?: JsonArray(emptyList())
                buildJsonObject {
                    put("ok", true)
                    put("count", arr.size)
                    put(
                        "commits",
                        buildJsonArray {
                            arr.forEach { item ->
                                val o = item.jsonObject
                                val commit = o["commit"]?.jsonObject
                                add(
                                    buildJsonObject {
                                        put("sha", o.str("sha"))
                                        put("html_url", o.str("html_url"))
                                        put("message", commit?.str("message")?.take(300))
                                        put("author", commit?.get("author")?.jsonObject?.str("name"))
                                        put("author_date", commit?.get("author")?.jsonObject?.str("date"))
                                        put("committer", commit?.get("committer")?.jsonObject?.str("name"))
                                        put("login", o["author"]?.jsonObject?.str("login"))
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    suspend fun compare(
        owner: String,
        repo: String,
        base: String,
        head: String
    ): JsonObject = withContext(Dispatchers.IO) {
        require(base.isNotBlank()) { "base 必填" }
        require(head.isNotBlank()) { "head 必填" }
        when (val result = get("/repos/$owner/$repo/compare/$base...$head")) {
            is ApiResult.Error -> err(result)
            is ApiResult.Success -> {
                val o = result.body as? JsonObject ?: return@withContext errMsg("响应异常")
                val files = o["files"]?.jsonArray
                val commits = o["commits"]?.jsonArray
                buildJsonObject {
                    put("ok", true)
                    put("status", o.str("status"))
                    put("ahead_by", o.long("ahead_by"))
                    put("behind_by", o.long("behind_by"))
                    put("total_commits", o.long("total_commits"))
                    put("html_url", o.str("html_url"))
                    put("permalink_url", o.str("permalink_url"))
                    put("diff_url", o.str("diff_url"))
                    put("base", base)
                    put("head", head)
                    if (commits != null) {
                        put("commits_count", commits.size)
                        put(
                            "commits",
                            buildJsonArray {
                                commits.take(30).forEach { c ->
                                    val co = c.jsonObject
                                    val commit = co["commit"]?.jsonObject
                                    add(
                                        buildJsonObject {
                                            put("sha", co.str("sha"))
                                            put("message", commit?.str("message")?.take(200))
                                            put("author", commit?.get("author")?.jsonObject?.str("name"))
                                        }
                                    )
                                }
                            }
                        )
                    }
                    if (files != null) {
                        put("files_count", files.size)
                        put(
                            "files",
                            buildJsonArray {
                                files.take(100).forEach { f ->
                                    val fo = f.jsonObject
                                    add(
                                        buildJsonObject {
                                            put("filename", fo.str("filename"))
                                            put("status", fo.str("status"))
                                            put("additions", fo.long("additions"))
                                            put("deletions", fo.long("deletions"))
                                            put("changes", fo.long("changes"))
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────

    data class FileChange(
        val path: String,
        val content: String = "",
        val contentIsBase64: Boolean = false,
        val delete: Boolean = false,
        val mode: String = "100644"
    )

    sealed class ApiResult {
        data class Success(val body: JsonElement, val code: Int) : ApiResult()
        data class Error(val code: Int, val message: String, val raw: String) : ApiResult()
    }

    private fun err(result: ApiResult.Error): JsonObject = buildJsonObject {
        put("ok", false)
        put("error", result.message)
        put("status", result.code)
    }

    private fun errMsg(message: String): JsonObject = buildJsonObject {
        put("ok", false)
        put("error", message)
    }

    private fun decodeBase64Content(raw: String): String {
        val cleaned = raw.replace("\n", "").replace("\r", "")
        val bytes = Base64.getDecoder().decode(cleaned)
        return String(bytes, Charsets.UTF_8)
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

    private fun JsonObject.bool(key: String): Boolean? =
        this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
            ?: this[key]?.jsonPrimitive?.contentOrNull?.let {
                when (it) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
            }

    companion object {
        const val API_VERSION = "2022-11-28"
    }
}
