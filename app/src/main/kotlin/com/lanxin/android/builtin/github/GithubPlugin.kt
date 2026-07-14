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

package com.lanxin.android.builtin.github

import com.lanxin.android.builtin.github.tools.ConfigTool
import com.lanxin.android.builtin.github.tools.branch.BranchTools
import com.lanxin.android.builtin.github.tools.issue.IssueTools
import com.lanxin.android.builtin.github.tools.pr.PrTools
import com.lanxin.android.builtin.github.tools.repo.ContentsTool
import com.lanxin.android.builtin.github.tools.repo.CreateRepoTool
import com.lanxin.android.builtin.github.tools.repo.PushTool
import com.lanxin.android.builtin.github.tools.repo.SearchTool
import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.ToolDef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * GitHub MCP 工具插件（Android 本地版）。
 *
 * 工具：
 * - gh_config_get / gh_config_set
 * - gh_repo_contents / gh_repo_push / gh_repo_search / gh_repo_create
 * - gh_pr_list / gh_pr_create / gh_pr_merge
 * - gh_issue_list / gh_issue_create
 * - gh_branch_create / gh_commits_list / gh_compare
 */
@Singleton
class GithubPlugin @Inject constructor(
    private val configTool: ConfigTool,
    private val contentsTool: ContentsTool,
    private val pushTool: PushTool,
    private val searchTool: SearchTool,
    private val createRepoTool: CreateRepoTool,
    private val prTools: PrTools,
    private val issueTools: IssueTools,
    private val branchTools: BranchTools
) : LanXinPlugin {

    override val id = "lanxin.github"
    override val name = "GitHub MCP"
    override val version = "1.0.0"
    override val description =
        "GitHub REST API 封装：配置 Token、读写仓库、PR/Issue/分支管理（Ktor NetworkClient）"

    override suspend fun onLoad(context: PluginContext) {
        registerConfig(context)
        registerRepo(context)
        registerPr(context)
        registerIssue(context)
        registerBranch(context)
    }

    // ── config ──────────────────────────────────────────────────────

    private fun registerConfig(context: PluginContext) {
        context.registerTool(
            ToolDef(
                name = "gh_config_get",
                description = "读取 GitHub 配置（Token 脱敏、默认 owner/repo、API base_url）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("mask_token", boolProp("是否脱敏 Token，默认 true"))
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        configTool.get(maskToken = args.bool("mask_token") ?: true)
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "gh_config_set",
                description = "设置 GitHub Token / 默认仓库 / API 地址。token 需有 repo 权限（PAT 或 fine-grained）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("token", stringProp("GitHub Personal Access Token；不传则不改"))
                            put("owner", stringProp("默认仓库 owner；空字符串清除"))
                            put("repo", stringProp("默认仓库名；空字符串清除"))
                            put(
                                "base_url",
                                stringProp("API 根地址，默认 https://api.github.com；企业版可改")
                            )
                            put("clear_token", boolProp("为 true 时清除已存 Token"))
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        configTool.set(
                            token = args.string("token"),
                            owner = args.string("owner"),
                            repo = args.string("repo"),
                            baseUrl = args.string("base_url"),
                            clearToken = args.bool("clear_token") ?: false
                        )
                    }.toToolResult()
                }
            )
        )
    }

    // ── repo ────────────────────────────────────────────────────────

    private fun registerRepo(context: PluginContext) {
        context.registerTool(
            ToolDef(
                name = "gh_repo_contents",
                description = "获取仓库文件或目录内容。文件返回解码后的 content 与 sha；目录返回 entries 列表",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("owner", stringProp("仓库 owner，缺省用 gh_config 默认值"))
                            put("repo", stringProp("仓库名，缺省用 gh_config 默认值"))
                            put("path", stringProp("路径，空或 / 表示根目录"))
                            put("ref", stringProp("分支/tag/commit SHA，默认默认分支"))
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        contentsTool.get(
                            owner = args.string("owner"),
                            repo = args.string("repo"),
                            path = args.string("path").orEmpty(),
                            ref = args.string("ref")
                        )
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "gh_repo_push",
                description = "推送文件到仓库。单文件用 path+content（Contents API）；多文件用 files 数组（Git Data API 原子提交）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("owner", stringProp("仓库 owner"))
                            put("repo", stringProp("仓库名"))
                            put("message", stringProp("commit message（必填）"))
                            put("branch", stringProp("目标分支；多文件模式必填"))
                            put("path", stringProp("单文件路径"))
                            put("content", stringProp("单文件文本内容（或 base64）"))
                            put("sha", stringProp("更新已有文件时的 blob sha（可省略，冲突时自动重试）"))
                            put("content_base64", boolProp("content 是否已是 base64，默认 false"))
                            put(
                                "files",
                                stringProp(
                                    "多文件 JSON 数组字符串：[{path,content,delete?,is_base64?,mode?}]"
                                )
                            )
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("message")) })
                },
                handler = { args ->
                    runCatching {
                        val filesRaw = args.string("files")
                        val files = parseFiles(filesRaw, args["files"])
                        pushTool.push(
                            owner = args.string("owner"),
                            repo = args.string("repo"),
                            message = args.string("message") ?: error("message 必填"),
                            branch = args.string("branch"),
                            path = args.string("path"),
                            content = args.string("content"),
                            sha = args.string("sha"),
                            contentIsBase64 = args.bool("content_base64") ?: false,
                            files = files
                        )
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "gh_repo_search",
                description = "搜索 GitHub 仓库（Search API），query 支持 GitHub 搜索语法",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("query", stringProp("搜索关键词，如 lanxin android in:name"))
                            put("sort", stringProp("排序：stars / forks / help-wanted-issues / updated"))
                            put("order", stringProp("asc / desc"))
                            put("per_page", intProp("每页条数，默认 10，最大 100"))
                            put("page", intProp("页码，从 1 起"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("query")) })
                },
                handler = { args ->
                    runCatching {
                        searchTool.search(
                            query = args.string("query") ?: error("query 必填"),
                            sort = args.string("sort"),
                            order = args.string("order"),
                            perPage = args.int("per_page") ?: 10,
                            page = args.int("page") ?: 1
                        )
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "gh_repo_create",
                description = "创建 GitHub 仓库（用户或组织）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("name", stringProp("仓库名（必填）"))
                            put("description", stringProp("描述"))
                            put("private", boolProp("是否私有，默认 false"))
                            put("auto_init", boolProp("是否用 README 初始化，默认 false"))
                            put("org", stringProp("组织名；不传则在当前用户下创建"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("name")) })
                },
                handler = { args ->
                    runCatching {
                        createRepoTool.create(
                            name = args.string("name") ?: error("name 必填"),
                            description = args.string("description"),
                            private = args.bool("private") ?: false,
                            autoInit = args.bool("auto_init") ?: false,
                            org = args.string("org")
                        )
                    }.toToolResult()
                }
            )
        )
    }

    // ── PR ──────────────────────────────────────────────────────────

    private fun registerPr(context: PluginContext) {
        context.registerTool(
            ToolDef(
                name = "gh_pr_list",
                description = "列出仓库 Pull Request",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("owner", stringProp("仓库 owner"))
                            put("repo", stringProp("仓库名"))
                            put("state", stringProp("open / closed / all，默认 open"))
                            put("head", stringProp("过滤 head，如 user:branch"))
                            put("base", stringProp("过滤 base 分支"))
                            put("sort", stringProp("created / updated / popularity / long-running"))
                            put("direction", stringProp("asc / desc"))
                            put("per_page", intProp("每页条数，默认 20"))
                            put("page", intProp("页码"))
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        prTools.list(
                            owner = args.string("owner"),
                            repo = args.string("repo"),
                            state = args.string("state") ?: "open",
                            head = args.string("head"),
                            base = args.string("base"),
                            sort = args.string("sort"),
                            direction = args.string("direction"),
                            perPage = args.int("per_page") ?: 20,
                            page = args.int("page") ?: 1
                        )
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "gh_pr_create",
                description = "创建 Pull Request",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("owner", stringProp("仓库 owner"))
                            put("repo", stringProp("仓库名"))
                            put("title", stringProp("PR 标题（必填）"))
                            put("head", stringProp("源分支（必填），同仓直接分支名，跨仓 user:branch"))
                            put("base", stringProp("目标分支（必填），如 main"))
                            put("body", stringProp("PR 描述"))
                            put("draft", boolProp("是否草稿，默认 false"))
                        }
                    )
                    put(
                        "required",
                        buildJsonArray {
                            add(JsonPrimitive("title"))
                            add(JsonPrimitive("head"))
                            add(JsonPrimitive("base"))
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        prTools.create(
                            owner = args.string("owner"),
                            repo = args.string("repo"),
                            title = args.string("title") ?: error("title 必填"),
                            head = args.string("head") ?: error("head 必填"),
                            base = args.string("base") ?: error("base 必填"),
                            body = args.string("body"),
                            draft = args.bool("draft") ?: false
                        )
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "gh_pr_merge",
                description = "合并 Pull Request（merge / squash / rebase）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("owner", stringProp("仓库 owner"))
                            put("repo", stringProp("仓库名"))
                            put("pull_number", intProp("PR 编号（必填）"))
                            put("commit_title", stringProp("合并提交标题"))
                            put("commit_message", stringProp("合并提交说明"))
                            put(
                                "merge_method",
                                stringProp("merge / squash / rebase，默认 merge")
                            )
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("pull_number")) })
                },
                handler = { args ->
                    runCatching {
                        val num = args.int("pull_number") ?: error("pull_number 必填")
                        prTools.merge(
                            owner = args.string("owner"),
                            repo = args.string("repo"),
                            pullNumber = num,
                            commitTitle = args.string("commit_title"),
                            commitMessage = args.string("commit_message"),
                            mergeMethod = args.string("merge_method") ?: "merge"
                        )
                    }.toToolResult()
                }
            )
        )
    }

    // ── issue ───────────────────────────────────────────────────────

    private fun registerIssue(context: PluginContext) {
        context.registerTool(
            ToolDef(
                name = "gh_issue_list",
                description = "列出仓库 Issue（自动过滤 PR）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("owner", stringProp("仓库 owner"))
                            put("repo", stringProp("仓库名"))
                            put("state", stringProp("open / closed / all，默认 open"))
                            put("labels", stringProp("逗号分隔标签过滤"))
                            put("sort", stringProp("created / updated / comments"))
                            put("direction", stringProp("asc / desc"))
                            put("since", stringProp("ISO8601 时间，仅返回之后更新的"))
                            put("per_page", intProp("每页条数，默认 20"))
                            put("page", intProp("页码"))
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        issueTools.list(
                            owner = args.string("owner"),
                            repo = args.string("repo"),
                            state = args.string("state") ?: "open",
                            labels = args.string("labels"),
                            sort = args.string("sort"),
                            direction = args.string("direction"),
                            since = args.string("since"),
                            perPage = args.int("per_page") ?: 20,
                            page = args.int("page") ?: 1
                        )
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "gh_issue_create",
                description = "创建 Issue",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("owner", stringProp("仓库 owner"))
                            put("repo", stringProp("仓库名"))
                            put("title", stringProp("标题（必填）"))
                            put("body", stringProp("正文"))
                            put(
                                "labels",
                                stringProp("标签，逗号分隔或 JSON 数组字符串")
                            )
                            put(
                                "assignees",
                                stringProp("指派人登录名，逗号分隔或 JSON 数组字符串")
                            )
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("title")) })
                },
                handler = { args ->
                    runCatching {
                        issueTools.create(
                            owner = args.string("owner"),
                            repo = args.string("repo"),
                            title = args.string("title") ?: error("title 必填"),
                            body = args.string("body"),
                            labels = parseStringList(args.string("labels")),
                            assignees = parseStringList(args.string("assignees"))
                        )
                    }.toToolResult()
                }
            )
        )
    }

    // ── branch ──────────────────────────────────────────────────────

    private fun registerBranch(context: PluginContext) {
        context.registerTool(
            ToolDef(
                name = "gh_branch_create",
                description = "从指定分支或 SHA 创建新分支",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("owner", stringProp("仓库 owner"))
                            put("repo", stringProp("仓库名"))
                            put("branch", stringProp("新分支名（必填）"))
                            put("from_branch", stringProp("源分支，默认仓库 default_branch"))
                            put("from_sha", stringProp("源 commit SHA；优先于 from_branch"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("branch")) })
                },
                handler = { args ->
                    runCatching {
                        branchTools.createBranch(
                            owner = args.string("owner"),
                            repo = args.string("repo"),
                            branch = args.string("branch") ?: error("branch 必填"),
                            fromBranch = args.string("from_branch"),
                            fromSha = args.string("from_sha")
                        )
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "gh_commits_list",
                description = "列出仓库 commits",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("owner", stringProp("仓库 owner"))
                            put("repo", stringProp("仓库名"))
                            put("sha", stringProp("分支名或 commit SHA"))
                            put("path", stringProp("仅包含该路径的提交"))
                            put("author", stringProp("作者 GitHub login 或邮箱"))
                            put("since", stringProp("ISO8601 起始时间"))
                            put("until", stringProp("ISO8601 截止时间"))
                            put("per_page", intProp("每页条数，默认 20"))
                            put("page", intProp("页码"))
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        branchTools.listCommits(
                            owner = args.string("owner"),
                            repo = args.string("repo"),
                            sha = args.string("sha"),
                            path = args.string("path"),
                            author = args.string("author"),
                            since = args.string("since"),
                            until = args.string("until"),
                            perPage = args.int("per_page") ?: 20,
                            page = args.int("page") ?: 1
                        )
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "gh_compare",
                description = "比较两个分支/提交（base...head），返回 ahead/behind、commits 与 files 摘要",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("owner", stringProp("仓库 owner"))
                            put("repo", stringProp("仓库名"))
                            put("base", stringProp("基线分支或 SHA（必填）"))
                            put("head", stringProp("对比分支或 SHA（必填）"))
                        }
                    )
                    put(
                        "required",
                        buildJsonArray {
                            add(JsonPrimitive("base"))
                            add(JsonPrimitive("head"))
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        branchTools.compare(
                            owner = args.string("owner"),
                            repo = args.string("repo"),
                            base = args.string("base") ?: error("base 必填"),
                            head = args.string("head") ?: error("head 必填")
                        )
                    }.toToolResult()
                }
            )
        )
    }

    // ── helpers ─────────────────────────────────────────────────────

    private fun parseFiles(
        rawString: String?,
        element: kotlinx.serialization.json.JsonElement?
    ): JsonArray? {
        if (element is JsonArray) return element
        if (!rawString.isNullOrBlank()) {
            return runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(rawString).jsonArray
            }.getOrNull()
        }
        return null
    }

    private fun parseStringList(raw: String?): List<String>? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            return runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(trimmed).jsonArray
                    .mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                    .filter { it.isNotEmpty() }
            }.getOrNull()
        }
        return trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun Result<JsonObject>.toToolResult(): JsonObject =
        fold(
            onSuccess = { it },
            onFailure = { e ->
                buildJsonObject {
                    put("ok", false)
                    put("error", e.message ?: e.toString())
                }
            }
        )

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    private fun stringProp(desc: String) = buildJsonObject {
        put("type", "string")
        put("description", desc)
    }

    private fun boolProp(desc: String) = buildJsonObject {
        put("type", "boolean")
        put("description", desc)
    }

    private fun intProp(desc: String) = buildJsonObject {
        put("type", "integer")
        put("description", desc)
    }
}
