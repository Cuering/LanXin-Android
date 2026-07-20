# 导游 Guide

> **状态：V1 插件形态 · 默认 OFF** · 分支 `feat/navigate-guide-plugins-default-off`  
> **独立模块**，与导航 Navigate 拆开；**不要**做成 monolithic `ScenicGuide` / `realtime-local-guide`。  
> 看世界抓帧归属**本侧**；复用 `docs/companion-vision-explain.md`，不另起摄像头栈。

## 1. 职责

| 能力 | 说明 | V1 |
|------|------|----|
| 讲解为主 | 全屏陪伴「看世界」抓帧 + 多模态讲解 | ✅ 复用 companion vision |
| 位置增强 | 位置 ON 且有 last known → 粗略坐标注入提示 | ✅ 不持续定位 |
| 与导航互跳（轻） | 识别「想去/导航」→ 文案提示 `open_navigation` | ✅ 不复制导航实现 |
| 对话 tool | `explain_sight`（插件开后注册） | ✅ |

**不做**：自研 turn-by-turn、后台轨迹、无 consent 摄像头、与 Navigate 合并成一个大开关、默认开启。

## 2. 插件与默认关

| 项 | 值 |
|----|-----|
| 插件 id | `lanxin.guide`（GuidePlugin） |
| 默认 | **OFF**（`GuideConfig.DEFAULT_ENABLED = false`） |
| PluginManager | `register(GuidePlugin, defaultEnabled=false)` |
| DataStore | `smart_capabilities_guide`（智能能力子开关，默认 false） |
| 门闸 | `GuideGate.filterTools(pluginEnabled, master)` + Companion 入口门闸 |

**关时**：不注册 `explain_sight`、不露「看世界」入口、不主动开相机、不位置增强。

## 3. 开启步骤

1. 设置 → **智能能力** → 打开 **主开关**
2. 打开 **导游** 子开关（默认关）
3. 「看世界」仍需会话开关 + consent + CAMERA
4. 位置增强另需位置 prefs ON

## 4. 入口

- **主入口**：全屏陪伴「看世界」+ 提问（仅导游插件开后显示）
- **对话**：`explain_sight`（插件开后）
- **不**塞进导航主开关；不占用 `nearby_poi`

## 5. 共享底层（不复制）

| 能力 | 来源 |
|------|------|
| 视觉抓帧 / 多模态 | `CompanionVisionSession` · VisionExplainClient · CameraX |
| 位置 last known | `get_location` / `LocationTool.readOnce` |
| 外链导航 | **Navigate** `open_navigation`（仅文案互跳） |
| Gate | 智能能力 master + 导游插件 + 位置 prefs + consent |

## 6. 代码

```
builtin/guide/
├── GuidePlugin.kt             # id=lanxin.guide · explain_sight
├── di/GuideModule.kt          # register defaultEnabled=false
└── domain/
    ├── GuideConfig.kt         # DEFAULT_ENABLED=false · PLUGIN_ID
    ├── GuideGate.kt           # pluginEnabled 门闸 + filterTools
    ├── GuideLocationContext.kt
    ├── GuidePromptBuilder.kt
    └── GuideNavHandoff.kt
```

接线：`LanXinApp` 注入 `GuidePluginRegistration`；`CompanionViewModel`（入口门闸 + 位置增强 + handoff）；  
`ChatSendToolFilterLogic` → `GuideGate.filterTools(pluginEnabled=smart.guideEnabled, …)`。  
单测：`GuideDomainTest` + `ChatSendFailureLogicTest`（默认 OFF 无入口/无 tool）  
CI：`.github/workflows/guide-verify.yml`

## 7. 与导航

| | 导航 Navigate | 导游 Guide |
|--|---------------|------------|
| 用户说法 | 出口/厕所/酒店价/带我去 | 这是什么/讲讲景点/看世界 |
| 默认 | **OFF** | **OFF** |
| 相机 | 一般不需要 | 「看世界」+ consent |
| 包路径 | `builtin/navigate` | `builtin/guide` |
| 跳转 | 到景点后可提示开导游 | 讲完「去这里」→ 提示 `open_navigation` |

## 8. 隐私

- 默认关「看世界」入口与导游插件
- 帧**不落盘**；位置仅用时读 last known
- 本地脑默认 OFF 不动
