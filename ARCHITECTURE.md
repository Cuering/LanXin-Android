# LanXin Android 架构设计（定稿 v1.0）

> 基于 GPT Mobile 源码改造，引入插件化架构，借鉴 AstrBot 设计思路。
> 当前状态：**Phase 1 落地完成 → Phase 2 开发中**
> - Step① Plugin 框架 ✅
> - Step② 三大插件迁移（memory / chat / logger）✅
> - Step③ MCP 工具调用引擎 + 记忆工具 ✅
> - Step④ Skill 加载器（SkillLoader / SkillEngine）✅
> - Step⑤ Persona 人格设定 ✅

---

## 一、项目背景

LanXin Android 是一个在手机上运行的 AI 助理 APP，核心能力：
- 多模型提供商支持（OpenAI / Anthropic / Google / Groq 等）
- 本地记忆系统（已迁入插件）
- 聊天对话管理
- 自动更新/版本回退
- 本地数据备份还原
- 日志系统

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
│   ├── statistics/         数据统计 🔥 Phase 2
│   ├── scheduler/          定时任务/提醒 ⏳ Phase 2
│   └── knowledge/          知识库 ⏳ Phase 3（初期可对接云端）
│
├── plugins/                [外部插件] — 可选增强，可拔插
│   ├── memory/             记忆系统 ✅
│   ├── chat/               聊天历史管理 ✅
│   └── logger/             日志查看 UI ✅
│
├── app/                    [壳应用]
│   ├── LanXinApp.kt        初始化入口
│   ├── builtin/            内置功能实现（persona ✅ / statistics 等）
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
Phase 1（已落地 ✅）       Phase 2（中期）          Phase 3（远期）
─────────────────      ─────────────────      ─────────────────
register()             + getPluginsByType()    + 插件依赖声明
initializeAll()        + setEnabled()          + 状态持久化
getPlugin(id)          + 插件管理 UI           + 插件市场（可选）
+ Skill（Step④）       + setEnabled()
```

---

## 五、模块开发优先级

| 模块 | 优先级 | 难度 | 备注 |
|------|--------|------|------|
| `plugins/memory/` | ✅ 已完成 | - | 已迁入 |
| `plugins/chat/` | ✅ Phase 1 | 中 | 已搬迁 |
| `core/updater/` | ✅ Phase 1 | 中 | 版本检查 + 下载 + 备份 + 回退 |
| `core/log/` | ✅ Phase 1 | 低 | 复刻 AstrBot LogManager |
| `plugins/logger/` | ✅ Phase 1 | 低 | 日志浏览 UI |
| `app/skill/` | ✅ Phase 1 | 低 | Skill 加载器（Step④） |
| `builtin/persona/` | ✅ Phase 2 | 低 | Step⑤ 人格 system prompt + MCP 工具 + 设置页 |
| `builtin/statistics/` | 🔥 Phase 2 | 低 | 对话轮数、token 估算 |
| `builtin/scheduler/` | ⏳ Phase 2 | 中 | 需要后台 Service + 通知权限 |
| `builtin/knowledge/` | ⏳ Phase 3 | 高 | 初期对接云端，本地向量检索延后 |

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
| 知识库 | 本地 embedding + 向量检索 | 初期建议对接云端 |
| 子代理 | 内置核心功能 | 手机端不建议实现 |
| Skill | AstrBot 指令式 skill | MCP 工具式 Skill 加载器 |

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
| `app/skill/README.md` | Skill 加载器设计 | 📋 待补充 |
| `builtin/persona/README.md` | 人格设定模块 | 🚧 待开发 |

---

## 十、技术栈

- Kotlin + Jetpack Compose
- Room（本地数据库）
- Hilt（依赖注入）
- DataStore（偏好设置）
- Kotlin Coroutines + Flow
- 版本号：Semver（语义化版本号，参考 AstrBot VersionComparator）
