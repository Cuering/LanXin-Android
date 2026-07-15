# LanXin Android 架构设计（定稿 v1.0）

> 基于 GPT Mobile 源码改造，引入插件化架构，借鉴 AstrBot 设计思路。
> 当前状态：**Phase 1 ✅ → Phase 2 ✅ → Phase 3 ✅ 已完成**
> - Step① Plugin 框架 ✅
> - Step② 三大插件迁移（memory / chat / logger）✅
> - Step③ MCP 工具调用引擎 + 记忆工具 ✅
> - Step④ Skill 加载器（SkillLoader / SkillEngine）✅
> - Step⑤ Persona 人格设定 ✅
> - Step⑥ GitHub / Platform MCP 模块 ✅
> - Step⑦ 知识库（Knowledge） + 记忆向量检索 ✅（P0~P6 全部完成）
> - Step⑧ 统一收件箱（Unified Inbox）✅

---

## 一、项目背景

LanXin Android 是一个在手机上运行的 AI 助理 APP，核心能力：
- 多模型提供商支持（OpenAI / Anthropic / Google / Groq 等）
- 本地记忆系统（已迁入插件）
- 聊天对话管理
- 自动更新/版本回退
- 本地数据备份还原
- 日志系统
- 端侧知识库（向量 + BM25 混合检索）
- 跨会话历史与工作区文件浏览

原始项目为 GPT Mobile（Android chat app），在其基础上做插件化改造。

---

## 二、整体架构

### 2.1 三层分级

```
LanXin-Android/
│
├── core/                   [原生内核] — 软件必需的骨头和肉
│   ├── engine/             PluginManager, LanXinPlugin 接口, 核心抽象接口
│   ├── provider/           模型提供商（API 实现、DTO、网络层）
│   ├── config/             设置/配置存储（DataStore）
│   ├── chat-engine/        对话引擎（ChatViewModel 等核心逻辑）
│   ├── log/                日志底层（LogManager, LogBroker, 文件轮转）
│   ├── updater/            更新系统（版本检查、下载、安装 + 简单 UI 组件）
│   └── util/               工具类（VersionComparator 等）
│
├── builtin/                [内置功能] — 随包提供，不可卸载
│   ├── persona/            人格设定 ✅
│   ├── statistics/         数据统计 ✅
│   ├── scheduler/          定时任务/提醒 ✅
│   ├── platform/           手机平台工具 ✅
│   └── knowledge/          知识库 ✅ Phase 3（参见第十一节）
│
├── plugins/                [外部插件] — 可选增强，可拔插
│   ├── memory/             记忆系统 ✅
│   ├── chat/               聊天历史管理 ✅
│   ├── logger/             日志查看 UI ✅
│   └── unified_inbox/      跨会话历史 + 跨工作区文件 ✅
│
├── app/                    [壳应用]
│   ├── LanXinApp.kt        初始化入口
│   ├── builtin/            内置功能实现（persona / statistics / scheduler / platform / knowledge ✅）
│   ├── skill/              Skill 加载器（SkillLoader / SkillEngine / SkillMdParser ✅）
│   ├── presentation/
│   │   ├── theme/          主题
│   │   ├── ui/
│   │   │   ├── home/       首页
│   │   │   ├── main/       MainActivity
│   │   │   ├── setting/    设置页（含更新入口）
│   │   │   ├── setup/      欢迎页/初始化向导
│   │   │   └── migrate/    数据迁移
│   │   └── common/         共用 UI 组件
│   └── ...
│
└── README.md + 各模块文档
```

### 2.2 关键决策记录

| 问题 | 决策 | 理由 |
|------|------|------|
| 三层划分 | ✅ 合理。记忆留 plugins，知识库/人格放 builtin | 核心模块不直接依赖具体插件，通过接口解耦 |
| 更新系统放哪 | `core/updater` 含逻辑 + 简单 UI 组件 | Android 更新是系统级能力，不适合做成可卸载插件 |
| 日志分层 | ✅ 保持现状。core/log 底层 + plugins/logger UI | 日志 UI 是调试工具，可作为「开发者工具插件」验证模式 |
| 提供商插件化 | ❌ 不拆。保持 core/provider，抽象接口即可 | 提供商是应用基础能力，去掉等于空壳，手机端无热插拔需求 |
| 版本号策略 | App 和插件版本独立，不联动 | App 版本用于更新检测，插件版本仅做元数据 |
| 数据备份 | 分两步走：先硬编码核心数据，再通过插件接口扩展 | Phase 1 先做基本备份，Phase 2 加 getBackupFiles() |
| PluginManager | Phase 1 最小可用，Phase 2 加 getPluginsByType<T>() | 当前急需按类型查找 |
| Skill 加载方式 | asset + file 两级扫描，SKILL.md frontmatter 解析 | 借鉴 AstrBot，内置 skill 预置 asset，用户自定义放 filesDir |
| 子代理编排 | ❌ 不建议在手机端做 | 多 agent 编排太重，性能/耗电/内存都成问题 |

---

## 三、核心抽象接口（core/engine/）

core 定义接口，插件和 builtin 模块实现，core 内其他模块只依赖接口。

```kotlin
// 记忆提供者 — plugins/memory 实现
interface MemoryProvider {
    suspend fun inject(context: String): String
    suspend fun getMemories(): List<MemoryEntity>
}

// 聊天历史提供者 — plugins/chat 实现
interface ChatHistoryProvider {
    suspend fun search(query: String): List<Message>
    suspend fun export(): File
}

// 备份贡献者 — 各插件可选实现
interface BackupContributor {
    fun getBackupFiles(context: Context): List<File>
}

// 插件元数据 — 统一描述格式
data class PluginMetadata(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val icon: Int? = null,
    val author: String = "",
    val removable: Boolean = true,  // 是否允许卸载
    val minAppVersion: String = ""  // 兼容的最低 App 版本
)
```

---

## 四、PluginManager 演进路线

```
Phase 1（已落地 ✅）       Phase 2（已落地 ✅）       Phase 3（已落地 ✅）
─────────────────      ─────────────────      ─────────────────
register()             + getPluginsByType()    + 知识库 / 向量检索
initializeAll()        + setEnabled()          + Skill / Persona 增强
getPlugin(id)          + 插件管理 UI           + Unified Inbox
+ Skill（Step④）       + BackupContributor
```

---

## 五、模块开发优先级

| 模块 | 优先级 | 难度 | 备注 |
|------|--------|------|------|
| `plugins/memory/` | ✅ 已完成 | - | 已迁入；P1/P4/P5a 检索与导出增强 |
| `plugins/chat/` | ✅ Phase 1 | 中 | 已搬迁 |
| `core/updater/` | ✅ Phase 1 | 中 | 版本检查 + 下载 + 备份 + 回退 |
| `core/log/` | ✅ Phase 1 | 低 | 复刻 AstrBot LogManager |
| `plugins/logger/` | ✅ Phase 1 | 低 | 日志浏览 UI |
| `app/skill/` | ✅ Phase 1 | 低 | Skill 加载器（Step④） |
| `builtin/persona/` | ✅ Phase 2 | 低 | Step⑤ 人格 system prompt + MCP 工具 + 设置页；P6 tools/skills 过滤 + Mood |
| `builtin/statistics/` | ✅ Phase 2 | 低 | 对话轮数、token 估算 + MCP + 设置页 |
| `builtin/scheduler/` | ✅ Phase 2 | 中 | WorkManager + AlarmManager 双轨调度 |
| `builtin/platform/` | ✅ Phase 2 | 低 | 剪贴板 / 已安装应用 / 系统信息 MCP 工具 |
| `builtin/knowledge/` | ✅ Phase 3 | 高 | GTE-small(ONNX) + ObjectBox + BM25 RRF 混合检索 |
| `plugins/unified_inbox/` | ✅ Phase 3 | 中 | 跨 session 历史 + 跨工作区文件浏览 |

---

## 六、数据备份设计

### 6.1 Phase 1（硬编码）

更新/回退时自动备份：

```
lanxin_backup_2026-07-13_22-00.zip
├── manifest.json            ← 备份时间 / App版本 / 各插件版本
├── databases/
│   ├── lanxin_memory.db     ← 记忆数据
│   └── chat_*.db            ← 聊天记录
└── datastore/               ← 设置
```

### 6.2 Phase 2（插件扩展）

在 LanXinPlugin 接口中增加可选方法：

```kotlin
interface LanXinPlugin {
    // ... 现有方法
    
    // 插件声明需要备份的文件
    fun getBackupFiles(context: Context): List<File> = emptyList()
}
```

core/updater 在打包备份时，遍历所有已注册插件，收集这些文件。

`plugins/unified_inbox` 通过 `BackupContributor` 贡献 `lanxin_unified_inbox.db`。

---

## 七、更新时间线

```
检查更新 (core/updater)
  │
  ├─ 获取 GitHub Releases 版本列表
  │
  ├─ 用户选择版本（可高可低 = 更新或回退）
  │
  ├─ 自动备份当前数据
  │
  ├─ 下载 APK（带进度）
  │
  ├─ Intent 调系统安装器
  │
  └─ 安装完成 → 检测备份 → 提示恢复
```

更新入口在 `app/presentation/ui/setting/` 的设置页中，core/updater 提供 Compose UI 组件（进度弹窗、版本选择）给设置页使用。

---

## 八、与 AstrBot 的差异对照

| 维度 | AstrBot | LanXin Android |
|------|---------|----------------|
| 运行环境 | Python 服务端 | Android 客户端 |
| 更新方式 | 热替换代码文件 | APK Intent 安装 |
| 提供商 | 动态加载 | 编译时确定，接口抽象 |
| 插件类型 | star（内置）+ 市集安装 | builtin（内置）+ plugins（可选） |
| 插件 UI | 在 WebUI 中 | 原生 Compose UI |
| 日志 | loguru + WebUI 实时查看 | LogManager + 日志浏览插件 |
| 知识库 | 本地 embedding + 向量检索 | 端侧 GTE-small(ONNX) + ObjectBox + BM25 RRF 混合 |
| 子代理 | 内置核心功能 | 手机端不建议实现 |
| Skill | AstrBot 指令式 skill | MCP 工具式 Skill 加载器 |
| 跨会话 | unified_inbox 插件 | plugins/unified_inbox（端侧聚合） |

---

## 九、模块文档清单

| 文档 | 内容 | 状态 |
|------|------|------|
| `README.md` | 项目简介 + 功能清单 + 构建方式 | ✅ 已完成 |
| `ARCHITECTURE.md` | 本文 | ✅ 已完成 |
| `core/log/README.md` | 日志系统设计 | ✅ Phase 1 |
| `plugins/memory/README.md` | 记忆模型、导入导出策略 | ✅ Phase 1 |
| `plugins/chat/README.md` | 消息模型、搜索/导出 | ✅ Phase 1 |
| `plugins/logger/README.md` | 日志级别、实时查看 | ✅ Phase 1 |
| `plugins/unified_inbox/README.md` | 跨会话历史 + 跨工作区文件 | ✅ Phase 3 |
| `app/skill/README.md` | Skill 加载器设计 | ✅ 已完成 |
| `builtin/persona/README.md` | 人格设定模块 | ✅ Phase 2 |
| `builtin/statistics/README.md` | 数据统计模块 | ✅ Phase 2 |
| `builtin/scheduler/README.md` | 定时任务模块 | ✅ Phase 2 |
| `builtin/platform/README.md` | 手机平台工具模块 | ✅ Phase 2 |
| `builtin/github/README.md` | GitHub MCP 工具 | ✅ Phase 2 |
| `builtin/knowledge/README.md` | 知识库方案（见第十一节） | ✅ Phase 3 |

---

## 十、技术栈

- Kotlin + Jetpack Compose
- Room（本地数据库）+ FTS4（稀疏检索）
- ObjectBox VectorDB（HNSW 向量检索）
- ONNX Runtime Mobile（GTE-small int8 嵌入）
- Hilt（依赖注入）
- DataStore（偏好设置）
- Kotlin Coroutines + Flow
- 版本号：Semver（语义化版本号，参考 AstrBot VersionComparator）

---

## 十一、知识库方案（Phase 3）✅ 已完成

### 11.1 最终推荐组合

| 组件 | 选择 | 说明 |
|------|------|------|
| 嵌入模型 | **GTE-small**（HuggingFace） | 30M 参数，int8 量化 ~30MB，CPU 推理 < 10ms |
| 推理引擎 | **ONNX Runtime Mobile** | 加载 int8 量化模型，封装为 `EmbeddingService` |
| 向量数据库 | **ObjectBox VectorDB** | HNSW 索引，Native Kotlin API，新建独立 VectorStore |
| 稀疏检索 | **BM25 + Room FTS4** | 纯 Kotlin Tokenizer / Bm25Index |
| 检索策略 | **dense + sparse 双路 RRF** | `score = Σ 1/(k + rank_i), k=60` |

### 11.2 双路召回流程

```
用户输入
  ├─ 稀疏路：Tokenizer → BM25 / FTS MATCH（失败回退 Room LIKE）
  └─ 语义路：embed → ObjectBox Top-10 → 余弦相似度
  │
  ↓ RRF 融合: score = Σ 1 / (k + rank_i), k=60
  ↓ Top-5 注入 prompt
```

### 11.3 实现路线

| 阶段 | 内容 | 状态 |
|------|------|------|
| **P0** | 打通向量管道（ONNX + GTE-small int8 + ObjectBox embed/检索） | ✅ 已完成 |
| **P1** | 改造 MemoryInjector 为双路 RRF 融合，保留关键词回退开关 | ✅ 已完成 |
| **P2** | builtin/knowledge 文档导入（txt/md/pdf/word），512/50 滑动窗口分段 | ✅ 已完成 |
| **P3** | 自动从对话抽取知识点，轻量摘要后向量化入库 | ✅ 已完成 |
| **P4** | 记忆导出增强（Markdown 格式 + type/lifecycle 过滤 + UI 弹窗） | ✅ 已完成 |
| **P5a** | BM25 稀疏检索：纯 Kotlin Tokenizer + Bm25Index + Room FTS4 + VectorPipeline RRF 混合 | ✅ 已完成 |
| **P5b** | MarkdownChunker：ATX 标题解析 + 代码 fence 跳过 + 标题栈路径注入 | ✅ 已完成 |
| **P6** | 人格增强：Tools/Skills 按人格过滤 + Mood Imitation 情绪模拟 | ✅ 已完成 |

### 11.4 技术指标

| 指标 | 目标 |
|------|------|
| APK 增幅 | < 8MB |
| 推理速度 | < 10ms（CPU 单次） |
| 容量 | 10 万级条目毫秒级查询 |
| 隐私 | 完全离线，数据不出设备 |

---

## 十二、统一收件箱（Unified Inbox）✅

> 模块：`plugins/unified_inbox`  
> 插件 ID：`lanxin.unified_inbox`  
> 对应 AstrBot 侧 `unified_inbox` / CrossSessionHistoryPlugin 的 Android 端实现

### 12.1 职责

| 组件 | 说明 |
|------|------|
| **CrossSessionDatabase** | Room `lanxin_unified_inbox.db`，聚合所有平台会话历史；索引 `(platform, session_id, time)` |
| **CrossSessionIndexer** | 从 `chat_v2`（`ChatRoomV2Dao` + `MessageV2Dao`）全量重建索引 |
| **UnifiedFileBrowser** | 跨工作区文件浏览，默认根目录 `filesDir/workspaces/` |
| **CrossSessionHistoryInjector** | 可选跨会话引用注入（默认关闭，风格对齐 MemoryInjector） |

### 12.2 结构

```
app/.../plugins/unifiedinbox/
├── data/          CrossSessionEntity / Dao / Database / Repository
├── domain/        CrossSessionIndexer / UnifiedFileBrowser / CrossSessionHistoryInjector
├── presentation/  History + FileBrowser Screen/ViewModel
└── di/            UnifiedInboxModule (Hilt)
```

### 12.3 入口与 MCP

- **UI 入口**：设置 →「跨会话历史」→ `Route.UNIFIED_INBOX`；历史页内 →「文件浏览」→ `Route.UNIFIED_FILE_BROWSER`
- **MCP 工具**：`inbox_search` / `inbox_reindex` / `workspace_list`
- **备份**：贡献 `lanxin_unified_inbox.db`
- **文档**：`plugins/unified_inbox/README.md`
