# builtin/github — GitHub MCP 工具（Android 本地版）

在手机端通过 **GitHub REST API** 操作仓库、PR、Issue、分支。Token 存 DataStore，网络复用项目 Ktor `NetworkClient`。

## MCP 工具

| 工具 | 说明 |
|------|------|
| `gh_config_get` | 读取配置（Token 脱敏、默认 owner/repo、base_url） |
| `gh_config_set` | 设置 Token / 默认仓库 / API 地址 |
| `gh_repo_contents` | 获取文件或目录内容 |
| `gh_repo_push` | 推送文件（单文件 Contents API / 多文件 Git Data API） |
| `gh_repo_search` | 搜索仓库 |
| `gh_repo_create` | 创建仓库 |
| `gh_pr_list` | 列出 PR |
| `gh_pr_create` | 创建 PR |
| `gh_pr_merge` | 合并 PR（merge / squash / rebase） |
| `gh_issue_list` | 列出 Issue（自动过滤 PR） |
| `gh_issue_create` | 创建 Issue |
| `gh_branch_create` | 创建分支 |
| `gh_commits_list` | 列出 commits |
| `gh_compare` | 比较 base...head |

## 快速开始

1. 设置 Token（需 `repo` 权限）：

```json
{
  "token": "ghp_xxx",
  "owner": "your-name",
  "repo": "your-repo"
}
```

→ 调用 `gh_config_set`

2. 读文件：

```json
{ "path": "README.md", "ref": "main" }
```

→ `gh_repo_contents`

3. 单文件推送：

```json
{
  "path": "notes/a.txt",
  "content": "hello",
  "message": "docs: add note",
  "branch": "main"
}
```

→ `gh_repo_push`

4. 多文件原子提交：

```json
{
  "branch": "feat/x",
  "message": "feat: multi files",
  "files": "[{\"path\":\"a.kt\",\"content\":\"...\"},{\"path\":\"b.md\",\"content\":\"...\"}]"
}
```

→ `gh_repo_push`

## 参数约定

- 绝大多数工具的 `owner` / `repo` 可省略：优先参数，否则用 `gh_config_set` 写入的默认值。
- `gh_repo_push`：
  - **单文件**：`path` + `content`（可选 `sha` / `content_base64`）
  - **多文件**：`files` 为 JSON 数组字符串，元素字段 `path`、`content`、`delete?`、`is_base64?`、`mode?`
- `gh_pr_merge.merge_method`：`merge` | `squash` | `rebase`
- 企业版 GitHub：`gh_config_set.base_url` 设为 `https://github.example.com/api/v3`

## 代码位置

```
app/src/main/kotlin/com/lanxin/android/builtin/github/
├── GithubPlugin.kt          # 注册全部工具
├── di/GithubModule.kt       # Hilt DI + PluginManager 注册
├── data/
│   ├── GithubApi.kt         # Ktor REST 封装
│   └── GithubConfig.kt      # DataStore Token / 默认仓库
└── tools/
    ├── ConfigTool.kt
    ├── repo/   ContentsTool · PushTool · SearchTool · CreateRepoTool
    ├── pr/     PrTools
    ├── issue/  IssueTools
    └── branch/ BranchTools
```

## 安全说明

- Token 仅存应用私有 DataStore（`token` preferences）。
- `gh_config_get` 默认脱敏 Token（前后各 4 位）。
- 网络请求 `Authorization` 头在 Ktor Logging 中已 sanitize。
- 不在日志中打印完整 Token。
