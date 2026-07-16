# LanXin Android 架构设计（定稿 v1.0）

> 基于 GPT Mobile 源码改造，引入插件化架构，借鉴 AstrBot 设计思路。
> 当前状态：**Phase 1 ✅ → Phase 2 ✅ → Phase 3 ✅ → Phase 4 ✅ → Phase 5.1 ✅ → Phase 5.2 🚧**
> - Step①~⑧ 全部完成
> - 知识库 P0~P6、Unified Inbox 均已落地
> - Phase 4：品牌换皮 + Memory 编辑 UI + UnifiedSearch 四路 RRF
> - Phase 5.1：跨设备同步协议 + 引擎骨架 ✅
> - Phase 5.2：LWW 冲突策略（分支 `feat/phase5-2-lww`）进行中

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
│   ├── knowledge/          知识库 ✅
│   ├── unified_search/     统一搜索（四路 RRF）✅
│   └── sync/               跨设备同步引擎 ✅ 5.1 / LWW 🚧 5.2
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

Phase 4（规划中 🔜）      Phase 5（规划中 🔜）
─────────────────      ─────────────────
+ 动态插件加载           + 插件市场 / 远程安装
+ 插件生命周期管理       + 插件签名验证
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
| `builtin/voice/` | 🔜 Phase 6 | 高 | ASR + TTS（见第十四节） |
| `builtin/pet/` | 🔜 Phase 6 | 高 | 桌宠悬浮窗（见第十四节） |

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
| 本地推理 | — | 🔜 Phase 6 MNN 引擎 |
| 语音 | — | 🔜 Phase 6 ASR + TTS |

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
| `builtin/sync/README.md` | 跨设备同步引擎 | ✅ 5.1 / 🚧 5.2 LWW |
| `docs/sync-protocol.md` | Android ↔ AstrBot 同步协议 | ✅ LWW §4 已落地 |

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
- 🔜 MNN（阿里本地推理引擎，Phase 6）
- 🔜 Sherpa-ONNX（离线语音识别，Phase 6）
- 🔜 Bert-VITS2（中文语音合成，Phase 6）

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

---

## 十三、统一搜索（Phase 4 核心交付）✅

> 将 memory / knowledge / chat / unified_inbox 四路合一，RRF 融合排序

### 13.1 检索管道

```
用户输入
  ├──→ memory/      MemoryProvider.inject()     ← 记忆语义 + 关键词
  ├──→ knowledge/   KnowledgeService.search()   ← 知识库 dense + sparse
  ├──→ chat/        ChatHistoryProvider.search() ← 聊天历史 FTS
  └──→ unified_inbox/ CrossSessionRepo.search() ← 跨会话 FTS
  │
  ↓ RRF 融合: score = Σ 1 / (k + rank_i), k=60
  ↓ Top-8 注入 prompt
```

### 13.2 注入格式

```
[统一参考]
━━━ 记忆 ━━━
- {记忆摘要}
━━━ 知识 ━━━
- {知识片段}
━━━ 聊天历史 ━━━
- [{时间}] {对话摘要}
━━━ 跨会话 ━━━
- [{平台}/{会话}] {历史消息}
━━━ 参考结束 ━━━
```

### 13.3 实现要点

- 各路由并行调用，超时 2s 降级
- 零结果路由自动跳过（不注入空段）
- 搜索页 UI 可查看各路由独立命中数
- ChatViewModel 优先 `UnifiedSearchService.inject()`；关闭时回退 `MemoryInjector`

---

## 十四、Phase 4~6 完整路线图

### Phase 4 — 基础夯实（Foundation Overhaul）✅

**目标：** 补齐早期设计缺口，整合核心搜索体验

| 步骤 | 内容 | 难度 | 预估 |
|:----:|------|:----:|:----:|
| **4.1** | 源码目录迁移 `dev/chungjungsoo/gptmobile` → `com/lanxin/android` | 🟢 低 | 1d | ✅ |
| **4.2** | 图标 / 启动屏换皮（兰心品牌） | 🟢 低 | 0.5d | ✅ |
| **4.3** | Memory 可编辑 UI：查看/编辑/删除记忆条目 | 🟡 中 | 2d | ✅ |
| **4.4** | 统一搜索（UnifiedSearch）：四路 RRF 融合 | 🟡 中 | 3d | ✅ |
| **4.5** | UnifiedSearch 设置页 + 各路由命中数展示 | 🟢 低 | 1d | ✅ |

**交付：** `builtin/unified_search` 模块 + Memory 编辑页 + 品牌换皮 ✅

---

### Phase 5 — 平台扩展（Platform Expansion）

**目标：** 打通 Android ↔ AstrBot 数据链路，实现插件生态

| 步骤 | 内容 | 难度 | 预估 | 状态 |
|:----:|------|:----:|:----:|:----:|
| **5.1** | 跨设备同步：knowledge / memory 与 AstrBot 双向同步 | 🔴 高 | 5d | ✅ 已合入 |
| **5.2** | 同步冲突策略（LWW：最后写入者胜） | 🟡 中 | 2d | 🚧 进行中 |
| **5.3** | 动态插件加载：从 filesDir 加载 .apk 插件包 | 🔴 高 | 4d | 🔜 |
| **5.4** | 插件管理 UI：启用/停用/卸载 | 🟡 中 | 2d | 🔜 |
| **5.5** | 插件市场：从 GitHub 远程获取插件索引 | 🔴 高 | 3d | 🔜 |
| **5.6** | 插件签名验证 | 🟡 中 | 2d | 🔜 |

**交付：** `plugins/market` 模块 + 同步引擎

#### Phase 5.1 设计要点（`feat/phase5-sync`）

| 项 | 说明 |
|----|------|
| 协议 | `docs/sync-protocol.md`：SyncItem、pull/push、增量 since/cursor、tombstone |
| 模块 | `builtin/sync`（`app/.../builtin/sync` + `builtin/sync/README.md`） |
| 身份 | `device_id`（DataStore UUID）+ Bearer token + 可选 `user_id` |
| Outbox | `InMemorySyncOutbox`（进程级；后续可 Room 化） |
| Memory 路径 | Memory UI 增删改 → enqueue → `syncOnce` push/pull → LWW 合并进 `MemoryRepository` |
| Knowledge | 协议 `type=knowledge` + mapper 占位，适配器后置 |
| 冲突 | `LwwResolver`（updated_at → deleted → source → preferRemote）；完整策略见 5.2 |
| AstrBot | 建议 `POST /api/sync/pull`、`/api/sync/push`；本仓不改 `/AstrBot/astrbot` 系统源码 |
| 隔离 | **独立 HTTP**，不走聊天消息 / 不污染会话 |

#### Phase 5.2 设计要点（`feat/phase5-2-lww`）

| 项 | 说明 |
|----|------|
| 比较顺序 | `updated_at` → `deleted=true` 防复活 → `source` 字典序 → `preferRemote` |
| API | `compare` / `pick` / `shouldApply` / `decide` / `mergeById` / `isConflictResolution` |
| 统一入口 | push `applied` 与 pull items 均走 `DefaultSyncRepository.applyRemoteItem` |
| Memory | 完整 LWW 写库（upsert / tombstone 删除） |
| Knowledge | `mergeById` 列表层可测；无存储适配时 cycle 计 skipped |
| 观测 | `SyncCycleResult.skipped` / `conflictResolved` |
| 单测 | `LwwResolverTest` 边界：相等 ts、防复活、source、preferRemote、knowledge merge |
| 非目标 | 字段级合并、importance 保护、冲突 UI、AstrBot 服务端实现 |

---

### Phase 6 — 端侧智能（Edge Intelligence）

**目标：** 实现本地优先原则，不让网络成为瓶颈

| 步骤 | 内容 | 难度 | 预估 |
|:----:|------|:----:|:----:|
| **6.1** | MNN 本地推理引擎接入（参考妹居） | 🔴 高 | 5d |
| **6.2** | 离线兜底：无网络时自动切本地小模型 | 🟡 中 | 2d |
| **6.3** | ChatRouter 路由层重构：云端 ↔ 本地自动切换 | 🔴 高 | 3d |
| **6.4** | Sherpa-ONNX 离线语音识别（ASR） | 🔴 高 | 4d |
| **6.5** | Bert-VITS2 语音合成（TTS） | 🔴 高 | 4d |
| **6.6** | 桌宠悬浮窗（PetOverlay）：WindowManager Overlay | 🔴 高 | 4d |
| **6.7** | 场景感知：UsageStats + 截屏 → 桌宠主动关怀 | 🔴 高 | 3d |

**交付：** 本地推理引擎 + 语音 + 桌宠完整链路

---

## 十四、整体 Timeline

```
Phase 4（基础夯实）   Phase 5（平台扩展）     Phase 6（端侧智能）
────────────────      ────────────────      ────────────────
4.1 目录迁移         5.1 跨设备同步         6.1 MNN 本地推理
4.2 品牌换皮         5.2 冲突策略           6.2 离线兜底
4.3 Memory 编辑 UI    5.3 动态插件加载       6.3 ChatRouter 重构
4.4 统一搜索          5.4 插件管理 UI         6.4 ASR 语音识别
4.5 搜索设置页        5.5 插件市场           6.5 TTS 语音合成
                      5.6 插件签名           6.6 桌宠悬浮窗
                                             6.7 场景感知

预计总工时：约 45~50 人天
（各阶段可并行推进，具体排期由兰心按当前进度灵活调整）
```

### 遗留设计补对照

以下为早期技术文档中设计、但后续推进中遗失的能力，已归入对应 Phase：

| 原设计 | 归入 | 说明 |
|--------|------|------|
| 源码目录 → `com/lanxin/android` | **Phase 4.1** | ✅ 已完成（仅剩品牌/文档清理已扫清） |
| 图标/启动屏换皮 | **Phase 4.2** | ✅ 兰心图标/启动屏/strings 已换皮 |
| MemoryRepo 可编辑 UI + 向量 | **Phase 4.3** | ✅ 列表/编辑/删除已就绪；注入走 UnifiedSearch |
| 本地优先 / 离线兜底 | **Phase 6.1~6.3** | MNN + ChatRouter |
| VoiceRepo / ASR + TTS | **Phase 6.4~6.5** | Sherpa-ONNX + Bert-VITS2 |
| 桌宠 PetOverlay | **Phase 6.6~6.7** | 参考妹居 |
| engine/ 路由层重构 | **Phase 6.3** | 做离线兜底时一并重构 |

---

## 十五、参考项目链接

| 项目 | 链接 | 可借鉴 |
|------|------|--------|
| **妹居 2.2.2**（Meiju） | `https://github.com/Cuering/MeiJu` | MNN 本地推理、Bert-VITS2 语音、桌宠、场景感知 |
| **GPT Mobile**（上游） | `https://github.com/Cuering/gpt-mobile` | 本项目基座，多 Provider、Compose UI |
| **LanXin-Android**（当前） | `https://github.com/Cuering/LanXin-Android` | 当前开发项目 |
| **AstrBot** | 哥哥自建后端 | 聊天 API + 插件系统 |
| **sherpa-onnx** | `https://github.com/k2-fsa/sherpa-onnx` | 离线语音识别 |
| **Bert-VITS2** | `https://github.com/fishaudio/Bert-VITS2` | 中文语音合成 |
| **MNN** | `https://github.com/alibaba/MNN` | 阿里本地推理引擎 |
