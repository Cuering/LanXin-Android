# 导游 Guide

> **状态：V1 已实现** · 分支 `feat/guide-v1`  
> **独立模块**，与导航 Navigate 拆开；**不要**做成 monolithic `ScenicGuide` / `realtime-local-guide`。

## 1. 职责

| 能力 | 说明 | V1 |
|------|------|----|
| 看世界讲解 | 全屏陪伴抓帧 + 多模态 | ✅ 复用 companion vision（#105） |
| 位置增强 | last known 粗坐标注入 prompt | ✅ `GuideLocationContext` |
| 导航互跳 | 「想去/导航」→ 提示 `open_navigation` | ✅ `GuideNavHandoff`（不复制导航） |
| 附近景点介绍 | 联网 POI 讲解 | 后续（非导航 POI 工具） |

**不做**：自研 turn-by-turn、后台轨迹、无 consent 摄像头、与导航共用一个大开关。

## 2. 入口

- **主入口**：全屏陪伴「看世界」+ 提问 /「看一眼」
- **对话**：可走文本会话；V1 视觉讲解在 Companion
- **不要**占用导航「周边/出口」主路径

## 3. 共享底层（不复制）

- 视觉：`CompanionVisionSession` + CameraX + `VisionExplainClient`
- 位置：`get_location` / `LocationTool.readOnce`（last known）
- 导航互跳：仅提示调用 Navigate 的 `open_navigation`
- Gate / consent：#99 场景视觉 consent；位置 prefs + 智能能力 master

## 4. 代码

```
builtin/guide/
└── domain/
    ├── GuideConfig.kt
    ├── GuideGate.kt
    ├── GuideLocationContext.kt
    ├── GuideNavHandoff.kt
    └── GuidePromptBuilder.kt
```

接线：`CompanionViewModel.explainWithFrame` 注入位置 snippet + handoff。  
单测：`GuideDomainTest`。  
CI：`.github/workflows/guide-verify.yml`（JDK 21）。

## 5. 与导航

| | 导航 Navigate | 导游 Guide |
|--|---------------|------------|
| 用户说法 | 出口/厕所/酒店价/带我去 | 这是什么/讲讲景点 |
| 相机 | 一般不需要 | 「看世界」+ consent |
| 交付 | #109 已合 | **本 V1** |
| 跳转 | 到景点后可提示开导游 | 讲完「去这里」→ 调导航 |

详见 `docs/navigate.md`、`docs/companion-vision-explain.md`。

## 6. 隐私

- 相机：consent + 会话开关，不后台偷拍
- 位置：用时 last known，无持续轨迹
- 讲解内容标明「供参考」

## 7. 交互示例

- 开「看世界」→「这是什么」→ 抓帧讲解（有定位则附粗位置）
- 「带我去那里」→ 讲解 + 文末提示 open_navigation
