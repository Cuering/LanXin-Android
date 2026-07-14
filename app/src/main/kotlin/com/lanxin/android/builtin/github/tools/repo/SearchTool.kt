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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject

/**
 * gh_repo_search — 搜索 GitHub 仓库。
 */
@Singleton
class SearchTool @Inject constructor(
    private val api: GithubApi
) {
    suspend fun search(
        query: String,
        sort: String? = null,
        order: String? = null,
        perPage: Int = 10,
        page: Int = 1
    ): JsonObject = api.searchRepos(query, sort, order, perPage, page)
}
