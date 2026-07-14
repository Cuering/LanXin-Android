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

package com.lanxin.android.builtin.github.tools.pr

import com.lanxin.android.builtin.github.data.GithubApi
import com.lanxin.android.builtin.github.data.GithubConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject

/**
 * PR 工具：gh_pr_list / gh_pr_create / gh_pr_merge
 */
@Singleton
class PrTools @Inject constructor(
    private val api: GithubApi,
    private val config: GithubConfig
) {
    suspend fun list(
        owner: String?,
        repo: String?,
        state: String = "open",
        head: String? = null,
        base: String? = null,
        sort: String? = null,
        direction: String? = null,
        perPage: Int = 20,
        page: Int = 1
    ): JsonObject {
        val (o, r) = config.resolveRepo(owner, repo)
        return api.listPulls(o, r, state, head, base, sort, direction, perPage, page)
    }

    suspend fun create(
        owner: String?,
        repo: String?,
        title: String,
        head: String,
        base: String,
        body: String? = null,
        draft: Boolean = false
    ): JsonObject {
        val (o, r) = config.resolveRepo(owner, repo)
        return api.createPull(o, r, title, head, base, body, draft)
    }

    suspend fun merge(
        owner: String?,
        repo: String?,
        pullNumber: Int,
        commitTitle: String? = null,
        commitMessage: String? = null,
        mergeMethod: String = "merge"
    ): JsonObject {
        val (o, r) = config.resolveRepo(owner, repo)
        return api.mergePull(o, r, pullNumber, commitTitle, commitMessage, mergeMethod)
    }
}
