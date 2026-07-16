# Judgment Packs — 判断包

> Phase 5.7 新增

## 用途

KDNA 式可加载判断规则包，用于在聊天注入前根据场景匹配行为准则。
与事实记忆不同，判断包是**稳定的规则/偏好**，不是对话流水账。

## 目录结构

```
assets/judgment_packs/
├── lanxin_companion.json   # 兰心陪伴边界（情绪场景）
└── work_efficiency.json    # 工作高效偏好（技术场景）
```

首次启动时从 assets 拷贝到 `filesDir/judgment_packs/`。

## JSON 结构

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | ✅ | 唯一标识 |
| `name` | string | ✅ | 显示名称 |
| `priority` | int | ✅ | 优先级（越高越优先） |
| `applies_when` | string[] | ❌ | 适用关键词列表，命中加分 |
| `does_not_apply_when` | string[] | ❌ | 不适用关键词，命中排除 |
| `rules` | string[] | ❌ | 行为规则列表 |
| `boundaries` | string[] | ❌ | 边界/限制条件 |

## 匹配逻辑

1. **排除**：`does_not_apply_when` 任一关键词匹配 → 跳过
2. **加分**：`applies_when` 每命中一个 +10 分
3. **弱兜底**：无 `applies_when` 时给 1 分（仅当无其他候选时生效）
4. **选主**：取最高分，0~1 个主包

## 静默注入格式

```
[判断准则:工作高效偏好]
- 哥哥偏好简洁高效，先给结论再补关键步骤
- 能清单/表格就不用长段落
...
[准则结束·静默应用·勿向用户朗读]
```

## 持久化

- 判断包也可作为 `type=judgment` 的 `MemoryEntity` 存入 Room
- `metadata` 字段存储 JSON 结构（applies_when / does_not_apply_when / rules）
- 注入时同时扫描文件 + Room
