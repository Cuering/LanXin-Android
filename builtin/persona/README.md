# builtin/persona — 人格设定

> 插件 ID: `lanxin.persona`  
> 状态：✅ 已完成（Phase 2）

## 职责

- 管理 AI 人格预设（system prompt）
- 对话开始时将当前人格注入 system prompt
- 支持 AstrBot 兼容的 beginDialogs、tools/skills 过滤、customErrorMessage
- 提供设置页切换 / 编辑 / 新建 / 删除人格
- 注册 MCP 工具：`persona_list` / `persona_get` / `persona_set` / `persona_create` / `persona_delete`
- 兼容旧名：`persona_switch` / `persona_current`

## 结构

代码位于 `app/src/main/kotlin/com/lanxin/android/builtin/persona/`：

```
builtin/persona/
├── README.md
└── (实现) app/.../builtin/persona/
    ├── PersonaPlugin.kt              # LanXinPlugin + MCP 工具
    ├── data/
    │   ├── PersonaEntity.kt          # Room Entity（含 beginDialogs/tools/skills 等）
    │   ├── PersonaDao.kt             # DAO（含文件夹查询）
    │   └── PersonaDatabase.kt        # Room DB v2（含 MIGRATION_1_2）
    ├── domain/
    │   ├── Persona.kt                # 领域模型（对齐 AstrBot Perona 字段）
    │   └── PersonaRepository.kt      # Room + DataStore 仓库
    ├── presentation/
    │   ├── PersonaListScreen.kt      # 列表页（含元信息标签）
    │   ├── PersonaEditScreen.kt      # 编辑页（支持全部字段）
    │   └── PersonaViewModel.kt       # 状态管理
    └── di/PersonaModule.kt           # Hilt 注册
```

## 数据模型（对齐 AstrBot）

| 字段 | 类型 | 说明 | 默认 |
|------|------|------|------|
| `id` | String | 唯一标识 | - |
| `name` | String | 显示名称 | - |
| `systemPrompt` | String | 系统提示词 | - |
| `beginDialogs` | List<String>? | 预设对话（交替 user/assistant） | null |
| `tools` | List<String>? | 可用工具列表 | null=全部 |
| `skills` | List<String>? | 可用技能列表 | null=全部 |
| `customErrorMessage` | String? | 自定义报错回复 | null |
| `folderId` | String? | 所属文件夹 | null |
| `sortOrder` | Int | 排序 | 0 |
| `isBuiltin` | Boolean | 内置预设（不可删） | false |

## 存储

| 数据 | 方案 | 说明 |
|------|------|------|
| 人格列表 | Room `lanxin_persona.db` (v2) | MIGRATION_1_2 添加新字段 |
| 当前选中 ID | DataStore | key: `current_persona_id` |

## 内置预设

| ID | 名称 | 风格 |
|----|------|------|
| `default` | 默认助理 | 温柔体贴的兰心 |
| `cute` | 可爱风格 | 元气活泼 |
| `professional` | 专业风格 | 结构清晰、高效 |

内置人格可编辑 prompt，不可删除。自定义人格可删。

## 注入链路

`ChatViewModel.resolveSystemPrompt()`：

1. 读取 `PersonaRepository.getCurrentSystemPrompt()`
2. 若人格有 `beginDialogs`，将其作为前置对话注入
3. 与平台自身 `systemPrompt` 拼接（人格在前）
4. 按 `tools` / `skills` 过滤 MCP 工具列表
5. `customErrorMessage` 覆盖 API 报错文案

## MCP 工具

| 工具 | 作用 |
|------|------|
| `persona_list` | 列出全部人格，含元信息（beginDialogs/tools/skills 等） |
| `persona_get` | 按 id 获取人格详情 |
| `persona_set` | 设置当前人格（原名 `persona_switch`，兼容保留） |
| `persona_switch` | 兼容旧名，等同 `persona_set` |
| `persona_current` | 获取当前人格完整信息 |
| `persona_create` | 创建新人格（支持全部字段） |
| `persona_delete` | 删除自定义人格 |

## 入口

设置页 →「人格设定」→ `PersonaListScreen`
