# 导游 Guide

> **状态：规划 / 骨架** · 独立于导航 Navigate  
> 看世界抓帧归属**本侧**；复用 `docs/companion-vision-explain.md`，不另起摄像头栈。

## 1. 职责

- 景点 / 展品讲解
- 看世界识别讲解（全屏陪伴「看世界」）
- 历史文化与看点
- 附近景点介绍（位置增强，可选联网）

## 2. 入口

- 陪伴页「看世界」+ 对话讲解（已有 V1 视觉）
- 智能能力 / 陪伴子项「导游/讲解」（后续设置文案）
- **不要**与「导航/周边」做成一个大开关

## 3. 共享底层

- `get_location`、`web_search`、Gate、外链（去景点时跳转 **Navigate**）
- 视觉：`CompanionVisionSession` + CameraX（已实现）

## 4. 代码

```
builtin/guide/
└── domain/
    └── GuideConfig.kt    # feature id / 与 vision 关联
```

实现优先落在 pet companion vision；本包只承载导游域命名与后续位置增强讲解，避免与 `builtin/navigate` 揉包。

## 5. 交付

1. ✅ 看世界 V1（#105）
2. ⬜ 位置增强讲解（当前城市/景点上下文注入）
3. ⬜ 与导航互跳文案（「去这里」→ open_navigation）
