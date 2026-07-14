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

package com.lanxin.android.builtin.github.tools

import com.lanxin.android.builtin.github.data.GithubConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * gh_config_get / gh_config_set — Token 与默认仓库配置。
 */
@Singleton
class ConfigTool @Inject constructor(
    private val config: GithubConfig
) {
    suspend fun get(maskToken: Boolean = true): JsonObject = config.snapshot(maskToken)

    suspend fun set(
        token: String? = null,
        owner: String? = null,
        repo: String? = null,
        baseUrl: String? = null,
        clearToken: Boolean = false
    ): JsonObject {
        if (clearToken) {
            config.setToken(null)
        } else if (token != null) {
            config.setToken(token)
        }
        if (owner != null) {
            config.setOwner(owner.ifBlank { null })
        }
        if (repo != null) {
            config.setRepo(repo.ifBlank { null })
        }
        if (baseUrl != null) {
            config.setBaseUrl(baseUrl.ifBlank { null })
        }
        val snap = config.snapshot(maskToken = true)
        return buildJsonObject {
            put("ok", true)
            put("updated", true)
            put("has_token", snap["has_token"])
            put("token_masked", snap["token_masked"])
            put("default_owner", snap["default_owner"])
            put("default_repo", snap["default_repo"])
            put("base_url", snap["base_url"])
        }
    }
}
