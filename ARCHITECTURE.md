# LanXin Android 架构设计（定稿 v1.0）

> 基于 GPT Mobile 源码改造，引入插件化架构，借鉴 AstrBot 设计思路。
> 当前状态：**Phase 1 ✅ → Phase 2 ✅ → Phase 3 ✅ → Phase 4 ✅ → Phase 5.1–5.7 ✅ → Phase 6.1 ✅ → Phase 6.2 ✅ → Phase 6.3 ✅ → Phase 6.4 ✅ 骨架 → 桌宠 M1 ✅ → M2a ✅ → M2b Live2D 壳 ✅（本 PR）** → **Phase 7.1 系统工具骨架 ✅ → 7.2 日历+闹钟 ✅ → 7.3 笔记 ✅ → 7.4 用户文件 ✅ → 7.5 对话/桌宠一体 ✅** · **桌宠 + 操控手机 = 陪伴操控一体**
> - Step①~⑧ 全部完成
> - 知识库 P0~P6、Unified Inbox 均已落地
> - Phase 4：品牌换皮 + Memory 编辑 UI + UnifiedSearch 四路 RRF
> - Phase 5.1：跨设备同步协议 + 引擎骨架 ✅
> - Phase 5.2：LWW 冲突策略 ✅
> - Phase 5.3：动态插件加载 ✅
> - Phase 5.4：插件管理 UI ✅
> - Phase 5.5：插件市场 ✅
> - Phase 5.6：插件签名验证 ✅（`feat/phase5-6-plugin-signature`）
> - Phase 5.7：记忆全量对齐 AstrBot + 判断包/Decide/注入预算 + 坚果云 WebDAV 同步配置 ✅（`feat/phase5-7-memory-align`）
> - Phase 6.1：MNN 本地推理引擎骨架（接口 + stub + 配置 + 路由选择）✅
> - Phase 6.2：离线兜底（无网自动切本地）✅（`feat/phase6-2-offline-local-fallback`）
> - Phase 6.3：ChatRouter 路由层重构（云端 ↔ 本地）✅（`feat/phase6-3-chat-router`）
> - Phase 6.4：Sherpa-ONNX 离线语音识别（ASR）骨架 ✅（`feat/phase6-4-offline-asr`）
> - Phase 6 主线 M1：妹居风格桌宠 + VoiceSession 听→想→说 ✅（#48 已合 main）
> - Phase 6 主线 M2a：资源路径就绪 + 设置体验 + fetch 脚本指引 ✅（#49）
> - Phase 6 主线 M2b：Live2D 真显示壳（model3 + 降级）✅（`feat/m2b-live2d-display`）
> - Phase 7.1：系统工具骨架 ✅（#52）——DeviceTool + Gate + stub + 设置总开关；**陪伴操控一体**
> - Phase 7.2：闹钟 Intent 真启动 + 日历读/写 ✅（#53）——SET_ALARM/SHOW_ALARMS startActivity + Instances + INSERT Intent + 确认流
> - Phase 7.3：应用内笔记 Room CRUD + SAF 导出/导入 ✅（#54）
> - Phase 7.4：用户文件 SAF 选取/导入 + imports 列表/读/写/分享/删 ✅（#55）
> - Phase 7.5：对话/桌宠一体接入 🚧（`feat/phase7.5-voice-chat-tools`）——DeviceToolBridge chat/voice turn + VoiceSession 听→想→办→说
> - 联网搜索（WebSearch）：配置门闸 + 设置页 + Agent 按开关启用 ✅（`feat/websearch-config`）——默认关；不改 ChatRouter needsTools
> - 设备感知（system_info）：配置门闸 + 设置页 + Agent 按开关启用 ✅（`feat/device-sensing-gate`）——默认关；只读；不改 ChatRouter needsTools
> - 机器人 / Claw 宿主：动态插件常驻 + PlatformHost 扩展点 ✅（`feat/claw-host-dynamic-plugins`）——默认关；签名/市场沿用 5.3–5.6

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
│   ├── sync/               跨设备同步引擎 ✅ 5.1 / LWW ✅ 5.2
│   ├── local_inference/    本地推理（MNN 骨架 + 离线 + ChatRouter）✅ 6.1 · ✅ 6.2 · ✅ 6.3
│   ├── voice/              离线 ASR ✅ 6.4 · TTS stub ✅ M1
│   ├── pet/                桌宠悬浮 + VoiceSession ✅ M1 · 路径 ✅ M2a · L2D 壳 ✅ M2b
│   └── systemtools/        系统能力（日历/闹钟/笔记/用户文件）+ Bridge ✅ Phase 7.5
│
├── plugins/                [外部插件] — 可选增强，可拔插
│   ├── memory/             记忆系统 ✅（5.7 判断包/Decide/衰减维护/坚果云同步配置）
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
| 动态插件包路径 | `filesDir/plugin-packages/*.apk`，与 `plugins/<id>/` 数据目录分离 | 避免与 PluginContext.filesDir 冲突 |
| 提供商模型列表 | OpenAI 兼容走 `GET {apiUrl}/models`；Anthropic/Google/兰心仍手输 | 对齐 AstrBot；不改 ChatRouter / needsTools |
| 联网搜索 web_search | 默认关；设置页开关；Gate 过滤工具列表 + 拒绝执行 | 外发查询需用户授权；有工具 ≠ needsTools 首轮强制云端 |
| 设备感知 system_info | 默认关；设置页开关；Gate 过滤 + 拒绝执行；只读 | 设备/网络/电量上下文需用户授权；不含位置/通讯录 |

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

Phase 4（已落地 ✅）       Phase 5（5.1–5.7 ✅）
─────────────────      ─────────────────
+ 品牌 / UnifiedSearch  + 5.1 同步 ✅  5.2 LWW ✅
                       + 5.3 动态插件加载 ✅
                       + 5.4 管理 UI ✅ / 5.5 市场 ✅ / 5.6 签名 ✅ / 5.7 记忆对齐 ✅
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
| `builtin/platform/` | ✅ Phase 2 · WebSearch ✅ · 设备感知 ✅ | 低 | 剪贴板 / 应用 / **system_info（默认关）** / 文件 / **联网搜索（默认关）** / Intent |
| `builtin/knowledge/` | ✅ Phase 3 | 高 | GTE-small(ONNX) + ObjectBox + BM25 RRF 混合检索 |
| `plugins/unified_inbox/` | ✅ Phase 3 | 中 | 跨 session 历史 + 跨工作区文件浏览 |
| `builtin/local_inference/` | ✅ 6.1 · ✅ 6.2 · ✅ 6.3 | 高 | MNN 骨架 + 离线兜底 + ChatRouter（见第十四节） |
| `builtin/voice/` | ✅ 6.4 ASR 骨架 · ✅ M1 TTS stub · 🔜 真 TTS | 高 | Sherpa-ONNX ASR + Bert-VITS2 TTS（见第十四节 / meiju-style-pet） |
| `builtin/pet/` | ✅ M1 · ✅ M2a · ✅ M2b L2D 壳 | 高 | 悬浮 WebView + VoiceSession + Live2D 壳（见第十四节） |
| `builtin/systemtools/` | ✅ Phase 7.5 | 中 | DeviceTool + Bridge + Gate + Calendar/Alarm/Notes/Files（见第十四节 / system-tools） |

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
| 插件类型 | star（内置）+ 市集安装 | builtin（内置）+ plugins（可选）+ 动态 .apk（5.3） |
| 插件 UI | 在 WebUI 中 | 原生 Compose UI |
| 日志 | loguru + WebUI 实时查看 | LogManager + 日志浏览插件 |
| 知识库 | 本地 embedding + 向量检索 | 端侧 GTE-small(ONNX) + ObjectBox + BM25 RRF 混合 |
| 子代理 | 内置核心功能 | 手机端不建议实现 |
| Skill | AstrBot 指令式 skill | MCP 工具式 Skill 加载器 |
| 跨会话 | unified_inbox 插件 | plugins/unified_inbox（端侧聚合） |
| 本地推理 | — | ✅ 6.1 骨架 · ✅ 6.2 离线 · ✅ 6.3 ChatRouter |
| 语音 / 桌宠 | — | ✅ 6.4 ASR · ✅ M1/M2a/M2b 桌宠 · 🔜 真 TTS |

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
| `builtin/sync/README.md` | 跨设备同步引擎 | ✅ 5.1 / ✅ 5.2 LWW |
| `docs/sync-protocol.md` | Android ↔ AstrBot 同步协议 | ✅ LWW §4 已落地 |
| `docs/dynamic-plugins.md` | 动态插件包格式与加载流程 + 管理 UI + 市场 + 签名 + Claw | ✅ 5.3–5.6 · Claw |
| `docs/claw-host.md` | 机器人 / Claw 常驻宿主 + PlatformHost | ✅ MVP |
| `docs/plugin-signature.md` | 签名策略 / 白名单 / 与市场关系 | ✅ 5.6 |
| `docs/memory-alignment.md` | 记忆插件与 AstrBot 对齐 + 坚果云同步 | ✅ 5.7 |
| `docs/plugin-market.md` | 插件市场索引与安装管线 | ✅ 5.5 |
| `docs/local-inference.md` | 本地推理引擎骨架与离线兜底与 ChatRouter | ✅ 6.1 · ✅ 6.2 · ✅ 6.3 |
| `builtin/local_inference/README.md` | 本地推理模块说明 | ✅ 6.1 · ✅ 6.2 · ✅ 6.3 |
| `docs/voice-asr.md` | 离线 ASR 骨架与权限/路径约定 | ✅ 6.4 |
| `builtin/voice/README.md` | 语音模块（ASR + TTS stub） | ✅ 6.4 · ✅ M1 |
| `docs/meiju-style-pet.md` | 妹居风格桌宠架构对照 + VoiceSession | ✅ M1 · ✅ M2a · ✅ M2b |
| `docs/debug-assets.md` | Debug 开源 Live2D/ASR/TTS 路径与脚本 | ✅ M1 · ✅ M2a · ✅ M2b |
| `builtin/pet/README.md` | 桌宠模块说明 | ✅ M1 · ✅ M2a · ✅ M2b |
| `docs/system-tools.md` | 系统工具范围 / 原则 / 权限 / 里程碑 7.1–7.6 | 🚧 7.1 |
| `builtin/systemtools/README.md` | 系统能力模块说明 | 🚧 7.1 |

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
- ✅ Sherpa-ONNX 骨架（离线 ASR 接口，Phase 6.4）
- ✅ TTS 接口 + StubTtsEngine（M1）；🔜 Bert-VITS2 真机
- 🚧 桌宠 PetOverlay WebView（M1）

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

## 十四、Phase 4~7 完整路线图

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
| **5.2** | 同步冲突策略（LWW：最后写入者胜） | 🟡 中 | 2d | ✅ 已合入 |
| **5.3** | 动态插件加载：从 filesDir 加载 .apk 插件包 | 🔴 高 | 4d | ✅ 已合入 |
| **5.4** | 插件管理 UI：启用/停用/卸载 | 🟡 中 | 2d | ✅ 已合入 |
| **5.5** | 插件市场：从 GitHub 远程获取插件索引 | 🔴 高 | 3d | ✅ 已合入 |
| **5.6** | 插件签名验证 | 🟡 中 | 2d | ✅ 已合入 |
| **5.7** | 记忆全量对齐 + 坚果云 WebDAV 同步配置 | 🔴 高 | 3d | ✅ 已合入 |

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

#### Phase 5.3 设计要点（`feat/phase5-3-dynamic-plugins`）

| 项 | 说明 |
|----|------|
| 设计文档 | `docs/dynamic-plugins.md` |
| 包路径 | `filesDir/plugin-packages/*.apk`（与 `plugins/<id>/` 数据目录分离） |
| 清单 | APK 内 `assets/lanxin-plugin.json`（id / name / version / entryClass / minAppVersion） |
| ClassLoader | `dalvik.system.PathClassLoader`（反射创建，JVM 单测可注入 factory） |
| 生命周期 | `discoverAndLoadDynamicPlugins` / `setEnabled` / `unloadPlugin` / `getPluginRecords` |
| 状态 | `plugin-state.json` enable map；默认启用 |
| 安全 | `PluginSignatureVerifier`；5.6 已实现 AllowAll / DenyAll / Allowlist |
| 与 builtin | 同一 `PluginManager`；动态 id 不得覆盖编译期插件 |
| 单测 | 路径 / 清单 zip / 状态机 / 签名钩子 / 冲突与 enable 不崩溃 |
| 非目标 | 5.5 市场、5.6 完整签名、真机 dex 在 JVM 全覆盖（5.4 UI 见下） |


#### Phase 5.4 设计要点（`feat/phase5-4-plugin-manager-ui`）

| 项 | 说明 |
|----|------|
| 入口 | 设置 →「插件管理」→ `Route.PLUGIN_MANAGER` |
| UI | `PluginManagerScreen` + `PluginManagerViewModel`（Compose Material3） |
| 列表 | `PluginCatalog.getPluginRecords()`：id / name / version / source / enabled |
| 启用 | `setEnabled(id, enabled)`；Switch 切换 |
| 卸载 | 动态插件 `unloadPlugin`；可选删除 APK 文件（二次确认） |
| 扫描 | `discoverAndLoadDynamicPlugins`；展示 `getLastDynamicFailures` |
| 门面 | `PluginCatalog` 接口 + Hilt `PluginModule`；单测用 Fake |
| 安全提示 | 页眉展示签名策略；动态条目显示校验状态（5.6） |
| 非目标 | 安装向导、在线吊销 |


#### Phase 5.5 设计要点（`feat/phase5-5-plugin-market`）

| 项 | 说明 |
|----|------|
| 设计文档 | `docs/plugin-market.md`、`docs/dynamic-plugins.md` §10 |
| 默认索引 URL | `https://raw.githubusercontent.com/Cuering/LanXin-Android/main/docs/plugin-market-index.sample.json`（`MarketDefaults.DEFAULT_CATALOG_URL`） |
| 配置 | DataStore `plugin_market_catalog_url`；市场页可改；空则默认 |
| 模块 | `app/.../plugin/market/*` + Hilt `PluginMarketModule` |
| 门面 | `PluginCatalog.loadDynamicPlugin` 供安装后单包加载 |
| 管线 | 下载 → size/sha256 → `plugin-packages/` → loadDynamicPlugin |
| UI | `PluginMarketScreen` / ViewModel；路由 `PLUGIN_MARKET` |
| 入口 | 设置「插件市场」+ 插件管理页内入口 |
| 回退 | 远程失败 → 内置 `SampleMarketCatalog` |
| 单测 | parser / verifier / installer / repository / ViewModel；Fake 补 `loadDynamicPlugin` |
| 非目标 | 商店账号、付费 |

#### Phase 5.6 设计要点（`feat/phase5-6-plugin-signature`）

| 项 | 说明 |
|----|------|
| 设计文档 | `docs/plugin-signature.md`、`docs/dynamic-plugins.md` §5 |
| 策略 | `allow_all` / `deny_all` / `allowlist`（`SignaturePolicy`） |
| 默认 | debug → allow_all；release（非 debuggable）→ allowlist（空名单失败关闭） |
| 配置 | `filesDir/plugin-signature.json`（`PluginSignatureConfigStore`） |
| 证书 | `ApkCertDigestProvider` / `JarApkCertDigestProvider`（SHA-256）；单测 Fixed |
| 接入 | `DynamicPluginLoader` + `PluginManager` store-backed verifier |
| UI | 管理页眉策略名；动态卡片签名状态；失败「签名问题」；市场 load Failure 透传 |
| 单测 | Verifier / ConfigStore / Loader reject / ViewModel policy |
| 非目标 | 证书链在线吊销、商店级审核、付费签名 |

#### Claw 宿主设计要点（`feat/claw-host-dynamic-plugins`）

| 项 | 说明 |
|----|------|
| 设计文档 | `docs/claw-host.md`、`docs/dynamic-plugins.md` §7 补 |
| 默认 | **关**；不启动 FGS；`NoOpPlatformHost` |
| 配置 | DataStore `claw_host_enabled` / `claw_host_resident_requested` |
| 扩展点 | `PlatformHost`：keep-alive / 状态通知 / 扫码请求桩 |
| 常驻 | `ClawResidentService`（dataSync）+ `ClawResidentController` |
| 插件钩子 | 可选 `ResidentCapablePlugin` |
| 入口 | 设置 →「机器人 / Claw 宿主」→ `Route.CLAW_HOST` |
| 与 5.3–5.6 | 不改签名/市场；`LanXinApp` 在 discover 后 `syncFromSettings` |
| 单测 | Gate + DefaultPlatformHost；CI `claw-host-verify.yml` |
| 非目标 | 具体 IM 协议、VPN、真扫码 UI |

---

#### Phase 5.7 设计要点（`feat/phase5-7-memory-align`）

| 项 | 说明 |
|----|------|
| 设计文档 | `docs/memory-alignment.md`、`plugins/memory/README.md` |
| 能力对齐 | Decide 门控、判断包加载、applies_when 单主包、静默注入、Trace、注入预算 |
| 维护能力 | 衰减/清理、进化索引、用户画像、任务续接、会话追踪、对话归档（默认全开） |
| 同步 | **唯一开关** `syncEnabled`；Provider：AstrBot / 坚果云 WebDAV（配置接口） |
| 资产 | `assets/judgment_packs/*.json` 首次拷贝到 `filesDir/judgment_packs/` |
| Room | `MemoryDatabase` v2：`evolution_entries` / `user_profiles` / `task_resumes` / `dialog_archive` |
| 边界 | 只改 `plugins/memory/`；不新建插件；不改 AstrBot 核心 |

### Phase 6 — 端侧智能（Edge Intelligence）

**目标：** 实现本地优先原则，不让网络成为瓶颈

| 步骤 | 内容 | 难度 | 预估 |
|:----:|------|:----:|:----:|
| **6.1** | MNN 本地推理引擎接入（参考妹居） | 🔴 高 | 5d | ✅ 骨架 |
| **6.2** | 离线兜底：无网络时自动切本地小模型 | 🟡 中 | 2d | ✅ |
| **6.3** | ChatRouter 路由层重构：云端 ↔ 本地自动切换 | 🔴 高 | 3d | ✅ |
| **6.4** | Sherpa-ONNX 离线语音识别（ASR） | 🔴 高 | 4d | ✅ 骨架 |
| **6.5 / M3** | Bert-VITS2 语音合成（TTS） | 🔴 高 | 4d | 🔜 真机；M1 已有 StubTtsEngine |
| **6.6 / M1** | 桌宠悬浮窗 + VoiceSession 主线（听→想→说） | 🔴 高 | 4d | ✅ M1（#48） |
| **6.6 / M2** | 路径就绪 + Live2D 真显 + 引擎可接 | 🔴 高 | 3d | ✅ M2a（#49）· ✅ M2b（`feat/m2b-live2d-display`） |
| **6.7 / M5** | 场景感知：UsageStats + 截屏 → 桌宠主动关怀 | 🔴 高 | 3d | 🔜 |

**交付主线：** **桌宠语音会话**（妹居级：Live2D/占位 + 语音听/说 + 对话）；6.5 TTS / 6.6 Pet 合并进该主线叙述。见 `docs/meiju-style-pet.md`。

#### Phase 6.1 设计要点（`feat/phase6-1-mnn-local-inference`）

| 项 | 说明 |
|----|------|
| 设计文档 | `docs/local-inference.md`、`builtin/local_inference/README.md` |
| 模块 | `app/.../builtin/localinference/*`（domain / data / di / presentation） |
| 接口 | `LocalLlmEngine` / `LocalInferenceProvider` / `LocalInferenceSettings` |
| 实现 | `StubLocalLlmEngine` + `MnnNativeBridge`（JNI 预留，无 so） |
| 配置 | DataStore：`local_inference_enabled` / `model_path` / `max_tokens` / `prefer_local` |
| 路由 | `InferenceRouteSelector` 纯逻辑（preferLocal / offline / cloud）；完整 ChatRouter 见 6.3 |
| UI | 设置 →「本地推理」→ `Route.LOCAL_INFERENCE` |
| 单测 | RouteSelector / StubEngine / NativeBridge / Config |
| 非目标 | 打包模型文件、真实 MNN so、6.3 ChatRouter 重构（6.2 已落地最小 fallback） |



#### Phase 6.2 设计要点（`feat/phase6-2-offline-local-fallback`）

| 项 | 说明 |
|----|------|
| 网络 | `NetworkStatusProvider` / `ConnectivityNetworkStatusProvider`（INTERNET+VALIDATED） |
| 协调 | `InferenceRouteCoordinator`：preferLocal + **engine.isReady** + 网络 → `InferenceRouteSelector` |
| Chat 接入 | `ChatRepositoryImpl.completeChat` 入口：LOCAL / UNAVAILABLE 错误 / CLOUD |
| UX | `ChatGenerationPhase.GENERATING_LOCAL`「本地离线生成中…」；本地时跳过 tool_call 循环 |
| 设置 | 本地推理页展示路由预览 + 离线兜底说明 |
| 单测 | 路由矩阵、Coordinator、ChatRepository offline/online、ChatLocalFallback |
| 非目标 | 完整 ChatRouter（6.3）、真实 MNN so、本地 tool_call |


#### Phase 6.3 设计要点（`feat/phase6-3-chat-router`）

| 项 | 说明 |
|----|------|
| 设计文档 | `docs/local-inference.md`、`builtin/local_inference/README.md` |
| 核心 | `ChatRouter` 纯函数：`ChatRouteContext` → `InferenceRouteDecision` + reason 码 |
| 收敛 | `InferenceRouteSelector` / `InferenceRouteCoordinator` 委托 ChatRouter，避免平行逻辑 |
| 规则 | needsTools→云端；preferLocal+ready→本地；无网+ready→本地；无网+未就绪→UNAVAILABLE |
| Chat | `completeChat(..., needsTools)`；ViewModel 传入工具可用性；`GENERATING_LOCAL` 保留 |
| 可观测 | reason：`prefer_local` / `offline_local` / `need_tools_cloud` / `offline_local_unavailable` / `default_cloud` |
| 设置 | 路由预览增强（含「纯对话/需工具」） |
| 单测 | ChatRouter 矩阵 + Coordinator + Repository needsTools |
| 非目标 | 真实 MNN so、本地 tool_call、ASR/TTS/桌宠 |






#### Phase 6.4 设计要点（`feat/phase6-4-offline-asr`）

| 项 | 说明 |
|----|------|
| 设计文档 | `docs/voice-asr.md`、`builtin/voice/README.md` |
| 模块 | `app/.../builtin/voice/*`（domain / data / di / presentation） |
| 接口 | `AsrEngine` / `AsrSettings` / `VoiceInputCoordinator` |
| 实现 | `StubAsrEngine` + `SherpaOnnxBridge`（JNI 预留，无 so） |
| 配置 | DataStore：`offline_asr_enabled` / `model_path` / `language` / `sample_rate`（**默认关**） |
| 权限 | `RECORD_AUDIO`；`MicPermissionGate` 温柔拒绝文案；不后台偷录 |
| UI | 设置 →「离线语音识别」→ `Route.OFFLINE_ASR`；试转写走 stub PCM |
| Chat | `VoiceInputCoordinator.transcribePcm` 预留；按住说话 UI 可 TODO |
| 单测 | StubEngine / Bridge / Config / PermissionGate / Coordinator / Recorder |
| 非目标 | 打包 so/模型、TTS(6.5)、桌宠、云端 ASR 优先 |



#### Phase 6 桌宠语音会话 M1 ✅ / M2a ✅ / M2b ✅（`feat/m2b-live2d-display`）

> **产品合并（2026-07）：** 桌宠与「操控手机」（Phase 7 系统工具）为 **陪伴操控一体**——模块可分，入口与会话不可分。见 Phase **7.5** / `docs/system-tools.md`。


| 项 | 说明 |
|----|------|
| 设计文档 | `docs/meiju-style-pet.md`、`builtin/pet/README.md` |
| 主线 | **Live2D/占位桌宠 + 语音会话**；非「Chat 按住说话填输入框」优先 |
| 状态机 | `IDLE → LISTENING → THINKING → SPEAKING → IDLE`（`VoiceSessionStateMachine`） |
| 模块 | `builtin/pet`：`FloatingPetService` / WebView `desktop-pet.html` / Bridge |
| Bridge | `DesktopPetBridge` / `AndroidVoiceBridge`（对齐妹居概念命名） |
| 语音 | 复用 6.4 ASR；`TtsEngine` + `StubTtsEngine`（M1） |
| 思考 | `PetChatResponder` stub；后续接 ChatRouter |
| 配置 | DataStore `desktop_pet_*`；**默认关** |
| 权限 | `SYSTEM_ALERT_WINDOW` 设置页引导；不偷录不截屏 |
| UI | 设置 →「桌宠 / 语音陪伴」；试运行 stub 一轮 |
| 单测 | 状态机 / Bridge 协议 / Coordinator / StubTts |
| 红线 | 禁止提交妹居 so/模型/moc3/商业人设；参考 APK 仅本机 |
| M2a | `PetPathReadiness` 路径就绪；设置「已就绪/缺失→fetch-debug-assets.sh」；`local_inference_model_path` + 1.5B 说明；下载不在 AstrBot 服务器 |
| M2b | `Live2dDisplayController` + WebView `LOAD_LIVE2D` 渲染壳；缺资源 fallback；设置显示模式；不打包 moc3 |
| 下一刀 | M2c sherpa 可 load · M3 真 TTS · M4 自有 L2D · M5 场景 |
| Debug 资源 | `docs/debug-assets.md` + `scripts/fetch-debug-assets.sh`（开源栈，不进 git） |


---

## 十四、整体 Timeline


### Phase 7 — 系统工具 / 设备技能（System Tools & Device Skills）

> 目标：让兰心通过 **统一 Tool/Skill 接口** 调用手机能力——**系统日历、闹钟、笔记、用户文件**等。
> **与桌宠合并：** 操控手机不是旁路 App，而是桌宠/语音会话的 **行动层**；聊天、桌宠 VoiceSession、MCP **共用**同一套工具与确认门闸。  
> 详细设计：`docs/system-tools.md`（落地时与本文同步）。模块建议：`builtin/systemtools/`（或 `builtin/device/`）。

#### 7.0 原则

| 原则 | 说明 |
|------|------|
| **用户文件，非系统分区** | 只管理用户可访问内容（SAF / MediaStore / `getExternalFilesDir` 等）；**禁止** root、改 `/system` `/vendor` |
| **能 Intent 就不深挖厂商** | 闹钟优先 `AlarmClock` Intent；日历优先 `CalendarContract` 或交给系统日历 App |
| **写/删要确认** | 创建日程、删文件、覆盖笔记等默认需用户确认或显式 tool 批准；设置页可分项开关 |
| **权限最小** | 能用 SAF / 系统 Intent 的不申请宽权限；日历读写按需申请 |
| **与现有模块协作** | 应用内定时继续用 `builtin/scheduler`；知识库/记忆已有 SAF 导入导出可复用 |

#### 7.1 能力范围

| 能力 | 策略 | 示例工具 ID | 权限 / 机制 |
|------|------|-------------|-------------|
| **日历 Calendar** | 读即将到来的事件；创建简单事件（或 Intent 交给系统日历） | `calendar_list_upcoming` / `calendar_create_event` | `READ_CALENDAR` / `WRITE_CALENDAR` 或 Intent |
| **闹钟 Alarm** | **不**替代系统时钟 App；调起系统设置闹钟 / 打开闹钟列表 | `alarm_set` / `alarm_show` | `AlarmClock.ACTION_SET_ALARM` 等 |
| **笔记 Notes** | 安卓无统一系统笔记 API：① 应用内轻量笔记 ② `CREATE_DOCUMENT` 导出 md/txt ③ 分享到已装笔记 App | `note_create` / `note_list` / `note_append` | 应用私有 DB + SAF；无强制厂商 API |
| **文件 Files（非系统）** | 列表/读文本/写入/分享/删除（确认）；仅用户授权树或应用目录 | `file_pick` / `file_list` / `file_read_text` / `file_write` / `file_share` | SAF + persistable URI；**不做**全盘静默扫描 |

#### 7.2 里程碑

| 阶段 | 内容 | 优先级 | 状态 |
|------|------|--------|------|
| **7.1 骨架** | `DeviceTool` 接口 + 权限门闸 + Fake/Stub + 单测 + 设置总开关 + 本文档 | 🔴 高 | ✅ |
| **7.2 闹钟 + 日历** | Intent 真 startActivity + setAlarmClock；日历 Instances + INSERT Intent + 确认流 + 权限引导 | 🔴 高 | ✅ |
| **7.3 笔记** | 应用内笔记 Room CRUD + SAF 导出/导入 | 🟡 中 | ✅ |
| **7.4 文件** | SAF 选取/导入 + imports 列表/读/写/分享/删（确认） | 🔴 高 | ✅ |
| **7.5 对话/桌宠一体接入** | `DeviceToolBridge` chat/voice turn + VoiceSession + ChatRouter hint | 🔴 高 | 🚧 |
| **7.6 打磨** | 权限引导 UX、隐私文案、失败降级、审计日志（可选） | 🟡 中 | 🔜 |

#### 7.3 架构要点

```
Chat / VoiceSession / MCP
        │
        ▼
  ToolRegistry（统一注册）
        │
        ▼
  SystemToolsCoordinator（权限 + 用户确认 + 开关）
        │
  ┌─────┼─────────┬──────────┐
  ▼     ▼         ▼          ▼
Calendar Alarm   Notes    UserFiles
Contract Intent  Store    SAF/MediaStore
```

- **默认**：危险操作（写日历、删文件、覆盖笔记）→ UI 确认后再执行。  
- **设置**：`系统能力` 分项开关（日历/闹钟/笔记/文件），总开关可一键全关。  
- **非目标（本阶段）**：无障碍模拟点击第三方 App、厂商笔记私有协议逆向、系统分区文件管理。

#### 7.4 与 Phase 6：**桌宠 + 操控手机 = 同一条产品线**

> 哥哥拍板：**桌宠和操控手机合在一起**，不要拆成两套互不相关的功能。

| 表现层（桌宠 / 语音） | 行动层（系统工具） |
|----------------------|-------------------|
| 悬浮窗、Live2D/占位、听→想→说 | 日历 / 闹钟 / 笔记 / 用户文件 |
| VoiceSession 状态机 | ToolRegistry + 确认门闸 |
| 「兰心在桌面陪着」 | 「兰心能动手办事」 |

**一体工作流（目标态）：**

```
用户对桌宠说话 / 点快捷
        │
        ▼
  VoiceSession（听 → 想）
        │
        ├─ 纯闲聊 → 本地/云端回复 → TTS/字幕
        │
        └─ 要办事 → SystemToolsCoordinator（权限 + 确认）
                      ├─ 日历 / 闹钟 / 笔记 / 文件 …
                      └─ 结果回灌 → 想（总结）→ 说
```

| 原分述 | 合并后 |
|--------|--------|
| Phase 6 只负责形象与语音 | Phase 6：**感官 + 会话壳**（听得见、看得到、说得出） |
| Phase 7 只负责系统 API | Phase 7：**双手**（同一会话里 tool_call，桌宠与聊天共用） |
| 两套入口 | **统一入口**：桌宠 / 聊天 / 快捷指令 → 同一 ToolRegistry |

实现上仍可按模块拆 PR（`builtin/pet` 与 `builtin/systemtools`），但 **产品叙事、设置信息架构、VoiceSession 钩子必须一体**：设置里「桌宠与助手能力」同级编排，而不是「桌宠一页、系统工具另一宇宙」。

#### 7.5 遗留对照（本阶段纳入）

| 需求 | 归入 |
|------|------|
| 接入系统日历 | **7.2** |
| 接入系统闹钟 | **7.2**（Intent） |
| 笔记（系统/应用内/导出） | **7.3** |
| 管理非系统文件 | **7.4** |
| 桌宠/聊天语音触发上述能力 | **7.5** |



#### Phase 7.5 设计要点（`feat/phase7.5-voice-chat-tools`）🚧

| 项 | 说明 |
|----|------|
| 统一入口 | `DeviceToolBridge`：`chatTurn` / `voiceTurn` + 发现 + 意图 + Gate + summarize |
| 意图 | `DeviceToolIntentResolver` 关键词 → tool id（无 LLM；不替代云端 tool_call） |
| 桌宠 | `VoiceSessionCoordinator` → `voiceTurn`：听→想→**办**→说 |
| ChatRouter | `decideWithDeviceToolHint(deviceToolIntentHit)` → needsTools 优先云端 |
| Chat / MCP | 既有 `SystemToolsPlugin` 经 Gate；Bridge 可被 Chat 工具路由复用 |
| 安全 | 默认全关；写/删确认不变 |
| 单测 | `DeviceToolBridgeTest` + `VoiceSessionToolBridgeTest` + ChatRouter hint |
| 非目标 | 重做整条会话、厂商深度、下模型 |

#### Phase 7.1 设计要点（`feat/phase7-system-tools-skeleton`）🚧

| 项 | 说明 |
|----|------|
| 设计文档 | `docs/system-tools.md`、`builtin/systemtools/README.md` |
| 模块 | `app/.../builtin/systemtools/*`（domain / data / di / presentation） |
| 接口 | `DeviceTool` / `DeviceToolGate` / `SystemToolsSettings` / `DeviceToolIds` |
| 实现 | Stub：`AlarmIntentBuilder` + 日历/笔记内存 Gateway；**不** `startActivity` / 不申请日历权限 |
| 工具（M1） | `alarm_set` / `alarm_show` / `calendar_list_upcoming` / `calendar_create_event` / `note_*` |
| 配置 | DataStore `system_tools_*`；**默认全关**；写操作默认需确认 |
| UI | 设置 →「系统能力」→ `Route.SYSTEM_TOOLS`（桌宠旁同级） |
| 插件 | `SystemToolsPlugin`（`lanxin.systemtools`）注册 MCP tools |
| 一体钩子 | **7.5** `DeviceToolBridge` + `VoiceSessionCoordinator` 听→想→办→说；Chat/MCP 经 Plugin+Gate |
| 单测 | AlarmIntentBuilder / Calendar stub / Gate / Config / Ids |
| CI | `.github/workflows/phase7-system-tools-verify.yml`（不 curl 模型） |
| 非目标 | 厂商笔记深度、无障碍乱点、系统分区、完整 7.2–7.5 |



```
Phase 4（基础夯实）   Phase 5（平台扩展）     Phase 6（端侧智能）        Phase 7（系统工具）
────────────────      ────────────────      ────────────────        ────────────────
4.1 目录迁移         5.1 跨设备同步         6.1 MNN 本地推理         7.1 工具骨架 🚧
4.2 品牌换皮         5.2 冲突策略           6.2 离线兜底             7.2 日历 + 闹钟 Intent
4.3 Memory 编辑 UI    5.3 动态插件加载       6.3 ChatRouter 重构       7.3 笔记（应用内+导出）
4.4 统一搜索          5.4 插件管理 UI         6.4 ASR 语音识别 ✅       7.4 用户文件 SAF
4.5 搜索设置页        5.5 插件市场           6.5/M3 TTS（M1 stub ✅）  7.5 对话/桌宠接入工具
                      5.6 插件签名           6.6/M1✅ M2a✅ M2b✅   7.6 UX / 隐私打磨
                                             6.7/M5 场景感知

预计总工时：约 45~50 人天（Phase 6）+ 约 10~15 人天（Phase 7 视权限与厂商差异）
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
| VoiceRepo / ASR + TTS | **Phase 6.4~6.5 / M1–M3** | Sherpa-ONNX + Bert-VITS2（会话优先桌宠） |
| 桌宠 PetOverlay | **Phase 6 主线 M1~M5** | 参考妹居 2.2.2 架构（不复制资源） |
| engine/ 路由层重构 | **Phase 6.3** | 做离线兜底时一并重构 |
| 系统日历 / 闹钟 Intent | **Phase 7.2** | CalendarContract + AlarmClock |
| 笔记（应用内 + 导出/分享） | **Phase 7.3** | 无统一系统笔记 API |
| 非系统文件管理（SAF） | **Phase 7.4** | 用户授权目录，禁止系统分区 |
| 对话/桌宠调用系统工具（**与桌宠一体**） | **Phase 7.5** | 同一 VoiceSession / ToolRegistry + 确认门闸 |
| OpenAI 兼容模型列表 / 提供商配置对齐 | **提供商对齐 P0+P1** | 自动拉取 + 选模型 UX + 中性 token 测速（`ping-lx-1`）；见 `docs/provider-alignment.md` |
| 联网搜索 web_search 配置门闸 | **WebSearch 配置** | DataStore + WebSearchGate + 设置页；默认关；见 `docs/websearch.md` |
| 设备感知 system_info 配置门闸 | **Device Sensing** | DataStore + DeviceSensingGate + 设置页；默认关；见 `docs/device-sensing.md` |

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
