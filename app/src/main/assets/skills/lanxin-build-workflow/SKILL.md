# LanXin Build Workflow Skill

## 触发时机
哥哥要求构建/编译 LanXin Android APK 时。

## 构建步骤

### 1. 环境准备
```bash
cd /AstrBot/data/workspaces/lark-_FriendMessage_ou_e2158acd59773be81d7c4a8a7cb90160
# 或LanXin-Android仓库目录
export ANDROID_HOME=/opt/android-sdk  # 或本地SDK路径
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

### 2. 编译命令
```bash
./gradlew assembleDebug
```

### 3. APK 输出路径
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 常见编译错误及修复方案

### 错误类型 1：未闭合括号（最高频）

**根因**：代码生成机器人（Bot）替换 `)` 为换行 + `,`，造成语法错误。

**错例**：
```kotlin
// ❌ 错误
.mapValues { (_, model,
 -> model.trim() }

// ✅ 修复
.mapValues { (_, model) -> model.trim() }
```

**效率最优修复方案（按优先级）**：

#### 方案 A：批量正则替换脚本（10+ 文件时推荐）
```python
import urllib.request, re

# 获取分支文件列表
url = "https://api.github.com/repos/Cuering/LanXin-Android/git/trees/fix/kotlin-syntax-errors?recursive=1"
# 下载每个 .kt 文件内容，应用 fix_kt_brackets()，然后推送

def fix_kt_brackets(content: str) -> str:
    """修复 Bot 生成的 Kotlin 未闭合括号"""
    fixes = [
        # pattern: (r'搜索模式', '替换'),
        (r'\.mapValues\s*\{\s*\(([^)]+),\s*\n\s*->', r'.mapValues { (\1) ->'),
        (r'(\w+)\(([^)]+),\s*\n\s*\)', r'(\2)'),
        (r'remember\(([^)]+),\s*\n\s*\{', r'remember(\1) {'),
        (r'mapNotNull\{\(([^)]+),\s*\n\s*->', r'mapNotNull { (\1) ->'),
        (r',\s*\n\s*([\)\}])', r'\1'),  # 通用收尾
    ]
    for pattern, replacement in fixes:
        content = re.sub(pattern, replacement, content)
    return content
```

#### 方案 B：手动逐个修复（1-3 文件时推荐）
用 `get_file_contents` 取内容 → Python 字符串替换 → `create_or_update_file` 回推。

#### 方案 C：从原项目重建（50%+ 文件损坏时推荐）
从上游 GPT Mobile 仓库重新 fork/generate，只保留 LanXin 定制改动文件。

---

### 错误类型 2：NPE / 空指针
**解法**：检查 !! 断言、Map 取值加 `?: default`、新增 DataStore key。

### 错误类型 3：缺少依赖
**解法**：在 `app/build.gradle.kts` 和 `gradle/libs.versions.toml` 补充对应库。

### 错误类型 4：HTTP 401 — 请求不带 Token
**现象**：服务端返回 `{"error":"Missing API key"}`  
**根因**：`LanXinAPIImpl` POST 请求没带认证 Header  
**修复**：
```kotlin
fun HttpRequestBuilder.applyAuthHeader() {
    if (token.isBlank()) return
    if (token.startsWith("abk_")) {
        header("X-API-Key", token)   # AstrBot API Key
    } else {
        header(HttpHeaders.Authorization, "Bearer $token")  # JWT
    }
}
```

### 错误类型 5：SSE 工具调用 JSON 显示为回复文本
**现象**：回复中看到 `{"id":"call-...","name":"send_message_to_user",...}` 的原始 JSON  
**根因**：AstrBot 工具调用事件格式是：
```json
{"type":"plain","data":"{\"id\":\"call-...\"}","chain_type":"tool_call"}
```
`type` 为 `"plain"` 但多了一个 `chain_type` 标记。旧代码只看 `type` 就把 data 当文本提取了。

**修复**：在 `handleSsePayload()` 中检查 `chain_type`，跳过工具调用：
```kotlin
val chainType = json.optString("chain_type")
if (chainType == "tool_call" || chainType == "tool_call_result") return null
```

### 错误类型 6：用户名硬编码
**现象**：服务端历史记录看到用户名永远是 `default_user`  
**根因**：`ChatRepositoryImpl.completeChatWithLanXin()` 写死了 `"default_user"`  
**修复**：通过 `SettingRepositoryImpl.getLanXinUserName()` 从 DataStore 读取持久化的用户名

---

## AstrBot SSE 事件格式速查（重要）

| `type` | `chain_type` | 含义 | App 处理 |
|--------|-------------|------|---------|
| `plain` | `""`（空） | 正常文本流 | ✅ `ApiState.Success(data)` |
| `complete` | — | 完整回复（已流完的汇总） | 跳过（避免重复） |
| `plain` | `"tool_call"` | 工具调用参数 | 跳过（显示 raw JSON 是 bug） |
| `plain` | `"tool_call_result"` | 工具执行结果 | 跳过 |
| `think` | — | 思考过程 | 跳过（未来可做 Thinking 展示） |
| `error` | — | 服务端错误 | `ApiState.Error(msg)` |
| `end` | — | 流结束 | `ApiState.Done` |
| `agent_stats` | `"agent_stats"` | 代理统计信息 | 跳过 |

---

## GitHub PR 工作流（远程编译模式）

### 完整流程
```
哥哥要求改代码
  ↓
创建分支 bot/task-xxx（或 fix/xxx）
  ↓
用 create_or_update_file / push_files 提交修改
  ↓
创建 PR → 自动触发 CI
  ↓
轮询 CI 状态（future_task 定时检查，或手动查）
  ↓
通知哥哥结果，哥哥合并 PR
  ↓
哥哥安装新 APK（GitHub Actions 产出的 build artifact）
```

### 轮询 CI 状态
```bash
# API 方式
curl -s -H "Authorization: token $TOKEN" \
  "https://api.github.com/repos/Cuering/LanXin-Android/actions/runs?branch=fix/lanxin-username-hardcode" \
  | python3 -c "import json,sys; runs=json.load(sys.stdin)['workflow_runs']; print(runs[0]['status'], runs[0]['conclusion'])"
```

---

## 效率决策树

```
文件扫描
├── 1-3 个文件有问题 → 方案 B：手动逐个修复
├── 4-15 个文件有问题 → 方案 A：批量正则脚本
└── 16+ 个文件有问题 → 方案 C：从原项目重建
     评估：重建耗时 vs 修复耗时，一般 >30 个文件重建更快
```

---

## 调试技巧

1. **本地编译优先**：能在本地 gradlew assembleDebug 跑过的，优先本地验证
2. **CI 日志拿不到时**：用 `get_file_contents` 拿到文件内容做静态模式匹配
3. **CI 通过但 APK 功能异常**：检查 ktlint 和 R8/ProGuard 规则
4. **SSE 响应异常（显示 JSON/重复）**：先看 AstrBot 日志确认事件格式，再检查 `handleSsePayload` 的 `type` + `chain_type` 匹配逻辑
5. **401 错误**：检查 `LanXinAPIImpl.applyAuthHeader()` 是否被调用，Token 是否已正确设置
