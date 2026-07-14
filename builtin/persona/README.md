# builtin/persona — 人格设定

> 插件 ID: `lanxin.persona`  
> 状态：✅ 已完成（Phase 2）

## 职责

- 管理 AI 人格预设（system prompt）
- 对话开始时将当前人格注入 system prompt
- 提供设置页切换 / 编辑 / 新建 / 删除人格
- 注册 MCP 工具：`persona_list` / `persona_switch` / `persona_current`

## 结构

代码位于 `app/src/main/kotlin/com/lanxin/android/builtin/persona/`（与 plugins 一致，随 app 模块编译）：

```
builtin/persona/
├── README.md
└── (实现) app/.../builtin/persona/
    ├── PersonaPlugin.kt              # LanXinPlugin + MCP 工具
    ├── data/
    │   ├── PersonaEntity / PersonaDao / PersonaDatabase
    ├── domain/
    │   ├── Persona / BuiltinPersonas
    │   └── PersonaRepository         # Room 列表 + DataStore 当前选中
    ├── presentation/
    │   ├── PersonaListScreen
    │   ├── PersonaEditScreen
    │   └── PersonaViewModel
    └── di/PersonaModule.kt
```

## 存储

| 数据 | 方案 | 说明 |
|------|------|------|
| 人格列表 | Room `lanxin_persona.db` | 支持多条预设 + 自定义 |
| 当前选中 ID | DataStore（共用 token prefs） | key: `current_persona_id` |

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
2. 与平台自身 `systemPrompt` 拼接（人格在前）
3. 再经 `ToolCallEngine.mergeSystemPrompt()` 叠加工具说明

## MCP 工具

| 工具 | 作用 |
|------|------|
| `persona_list` | 列出全部人格，标注当前项 |
| `persona_switch` | 按 id 切换当前人格 |
| `persona_current` | 返回当前人格与 system prompt |

## 入口

设置页 →「人格设定」→ `PersonaListScreen`
