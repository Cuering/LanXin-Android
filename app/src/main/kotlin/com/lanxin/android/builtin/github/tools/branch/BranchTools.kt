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

package com.lanxin.android.builtin.github.tools.branch

import com.lanxin.android.builtin.github.data.GithubApi
import com.lanxin.android.builtin.github.data.GithubConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject

/**
 * 分支工具：gh_branch_create / gh_commits_list / gh_compare
 */
@Singleton
class BranchTools @Inject constructor(
    private val api: GithubApi,
    private val config: GithubConfig
) {
    suspend fun createBranch(
        owner: String?,
        repo: String?,
        branch: String,
        fromBranch: String? = null,
        fromSha: String? = null
    ): JsonObject {
        val (o, r) = config.resolveRepo(owner, repo)
        return api.createBranch(o, r, branch, fromBranch, fromSha)
    }

    suspend fun listCommits(
        owner: String?,
        repo: String?,
        sha: String? = null,
        path: String? = null,
        author: String? = null,
        since: String? = null,
        until: String? = null,
        perPage: Int = 20,
        page: Int = 1
    ): JsonObject {
        val (o, r) = config.resolveRepo(owner, repo)
        return api.listCommits(o, r, sha, path, author, since, until, perPage, page)
    }

    suspend fun compare(
        owner: String?,
        repo: String?,
        base: String,
        head: String
    ): JsonObject {
        val (o, r) = config.resolveRepo(owner, repo)
        return api.compare(o, r, base, head)
    }
}
