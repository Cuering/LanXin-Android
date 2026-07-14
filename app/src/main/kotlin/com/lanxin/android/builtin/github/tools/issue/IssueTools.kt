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

package com.lanxin.android.builtin.github.tools.issue

import com.lanxin.android.builtin.github.data.GithubApi
import com.lanxin.android.builtin.github.data.GithubConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject

/**
 * Issue 工具：gh_issue_list / gh_issue_create
 */
@Singleton
class IssueTools @Inject constructor(
    private val api: GithubApi,
    private val config: GithubConfig
) {
    suspend fun list(
        owner: String?,
        repo: String?,
        state: String = "open",
        labels: String? = null,
        sort: String? = null,
        direction: String? = null,
        since: String? = null,
        perPage: Int = 20,
        page: Int = 1
    ): JsonObject {
        val (o, r) = config.resolveRepo(owner, repo)
        return api.listIssues(o, r, state, labels, sort, direction, since, perPage, page)
    }

    suspend fun create(
        owner: String?,
        repo: String?,
        title: String,
        body: String? = null,
        labels: List<String>? = null,
        assignees: List<String>? = null
    ): JsonObject {
        val (o, r) = config.resolveRepo(owner, repo)
        return api.createIssue(o, r, title, body, labels, assignees)
    }
}
