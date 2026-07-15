# app/skill — Skill 加载器

> 插件 ID: `lanxin.skill`  
> 状态：✅ 已完成（Phase 1 Step④）

## 职责

从本地目录扫描并加载 **Skill（能力指令包）**，注册为 MCP 工具供 AI 调用。  
Skill 比单次 MCP 工具更复杂：包含多步骤指令（`SKILL.md`）、可选脚本与资源。

## 扫描路径

| 来源 | 路径 | 优先级 |
|------|------|--------|
| 内置 | `assets/skills/` | 低 |
| 用户 | `filesDir/skills/` | 高（同名覆盖 assets） |

目录约定：

```
skills/
  <skill-name>/
    SKILL.md          # 必需
    scripts/          # 可选：辅助脚本（一层文件）
    assets/           # 可选：资源文件
```

## SKILL.md frontmatter 格式

```markdown
---
name: material-learning-summary
description: >-
  When the user gives learning materials, extract key points,
  compare with existing capabilities, and propose improvements.
model:                    # 可选，预留字段
tools:                    # 可选，预留字段（列表）
---

# Skill: material-learning-summary

## When to use
...

## Steps
1. ...
2. ...
```

| 字段 | 必需 | 说明 |
|------|------|------|
| `name` | 建议 | Skill 名称；缺省时用目录名 |
| `description` | 建议 | 简短描述；缺省时取正文首个 `#` 标题 |
| `model` | 可选 | 预留，当前解析器可读取但未消费 |
| `tools` | 可选 | 预留，当前解析器可读取但未消费 |

兼容无 frontmatter 的纯 Markdown（目录名 / 首个标题兜底）。  
YAML 支持 `key: value`、引号字符串、以及 `>-` / `>` / `|` 多行折叠。

## 组件职责

代码位于 `app/src/main/kotlin/com/lanxin/android/skill/`：

| 类 | 职责 |
|----|------|
| **Skill** | 数据模型：`name` / `description` / `instructionMd` / `scripts` / `assets` / `source` |
| **SkillLoader** | 扫描 `assets/skills/` + `filesDir/skills/`，合并加载（files 覆盖 assets） |
| **SkillMdParser** | 解析 SKILL.md frontmatter + 正文；极简 YAML，无外部依赖 |
| **SkillEngine** | 实现 `LanXinPlugin`；加载后注册 MCP 工具；处理 `invokeSkill` |
| **SkillModule** | Hilt DI 绑定 |

## MCP 工具注册流程

```
LanXinApp 启动
  └─ PluginManager 加载 SkillEngine (id=lanxin.skill)
       └─ onLoad(PluginContext)
            └─ SkillLoader.loadAll()
                 ├─ assets/skills/*
                 └─ filesDir/skills/*  （同名覆盖）
            └─ installSkills()
                 ├─ registerTool(skill_list)
                 ├─ registerTool(skill_load)
                 └─ 每个 skill → registerTool(skill.name)
```

### 工具一览

| 工具 | 说明 |
|------|------|
| `skill_list` | 列出全部已加载 Skill 及描述 / 来源 |
| `skill_load` | 按 `name` 加载完整 SKILL.md 指令 + 可选 `input`/`context` |
| `<skill.name>` | 每个 Skill 注册为同名工具，参数 `input` / `context` |

### 调用返回

AI 调用 skill 后，handler 返回 JSON：

```json
{
  "ok": true,
  "skill": "material-learning-summary",
  "description": "...",
  "guidance": "已加载技能…请严格按 instruction 执行",
  "input": "用户输入",
  "instruction": "SKILL.md 全文",
  "scripts": { "...": "..." },
  "assets": ["..."]
}
```

后续对话按 `instruction` 多步骤执行，可继续调用其它 MCP 工具。

### 热重载

`SkillEngine.reload()` 重新扫描目录并注册工具（需已 `onLoad`）。

## 内置 Skill 清单

| 目录名 | name | 用途 |
|--------|------|------|
| `material-learning-summary` | `material-learning-summary` | 学习资料消化：提取要点、对比能力缺口、提出改进建议 |
| `lanxin-build-workflow` | （无 frontmatter，目录名兜底） | LanXin Android 构建/编译工作流、常见错误修复、CI 流程 |

## 入口

无独立设置页；由 `SkillEngine` 作为插件在 App 启动时自动加载并注册 MCP 工具。  
AI 通过 `skill_list` / `skill_load` / 同名工具触发。
