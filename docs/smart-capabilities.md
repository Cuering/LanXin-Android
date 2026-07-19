# 智能能力模块（Smart Capabilities）

> 状态：**配置 + 主开关 + v1/v2 迁移 + 聚合 Gate + 位置 ✅**（`feat/smart-capabilities-simplify`）  
> 模块：`builtin/capabilities` · DataStore 键前缀 `smart_capabilities_`  
> 主开关：`smart_capabilities_master`（默认 **true**）

## 1. 目标

把设置页碎开关收成 **「智能能力」** 一页，**主 UI 主 + 4 子 ≈ 5 开关**（本地模型硬约束独立）：

```
设置
└─ 智能能力（主入口）
   ├─ [主开关] 默认 ON；关则子能力一律拒
   ├─ 状态摘要
   ├─ 本地模型 [OFF]     ← 独立保留（#106 硬约束）
   ├─ 语音 [ON]          ← ASR+TTS 合一
   ├─ 助手工具 [ON]      ← 系统工具 + 联网搜索 + 设备感知
   ├─ 位置与周边 [ON]    ← 位置（导航/周边可复用）；不含看世界
   ├─ 看世界 [OFF]       ← 原场景视觉；隐私敏感独立
   └─ ▸ 高级（可选）：细项入口（系统工具/搜索/设备感知/语音模型等）
```

**不**纳入本页默认 ON 列表：Claw（仍在设置根）、桌宠悬浮（桌宠页，默认 OFF）。

## 2. 合并映射

| 新开关 | 覆盖旧 id / 模块 |
|--------|------------------|
| `LOCAL_INFERENCE` | 本地推理（不变，默认 OFF） |
| `VOICE` | ASR + TTS |
| `ASSISTANT_TOOLS` | `SYSTEM_TOOLS` + `WEB_SEARCH` + `DEVICE_SENSING` |
| `LOCATION_AROUND` | `LOCATION`（Navigate 门闸复用） |
| `SCENE_VISION` | 场景视觉 / 看世界（不变，默认 OFF） |

兼容：`SmartCapabilitiesConfig.webSearchEnabled` 等属性 **get** 映射到对应聚合开关；`SmartCapabilityId` 保留旧枚举别名，`isChildEnabled` / Gate 走聚合。

## 3. 默认与迁移

| 子能力 | 新默认 | 迁移 |
|--------|--------|------|
| 主开关 | ON | 固定 DEFAULT_MASTER |
| 本地模型 | **OFF** | 从未配置不抬 ON；显式 true 保留 |
| 语音 | ON | 从未配置 → ON；显式 false 保留 |
| 助手工具 | ON | **任一项**（系统工具/搜索/设备感知）曾显式关 → 组关；从未配置 → ON |
| 位置与周边 | ON | 位置曾显式关 → 关；从未配置 → ON |
| 看世界 | **OFF** | 不抬 ON；显式 true 保留 |

标记：

- `smart_capabilities_migrated_v1`：旧模块键 → 智能能力键
- `smart_capabilities_migrated_v2`：细项 → 聚合键（`assistant_tools` / `location_around`）

应用启动（`LanXinApp`）与设置页进入时 `ensureMigrated()`（v1 后自动跑 v2）。

聚合键：

- `smart_capabilities_assistant_tools`
- `smart_capabilities_location_around`

公式：

```
effective(child) = master && child.enabled && runtime_ready
// ASSISTANT_TOOLS 同时门控 system tools / web_search / device_sensing
// LOCATION_AROUND 门控 get_location（及导航位置侧）
```

## 4. 代码位置

```
app/src/main/kotlin/com/lanxin/android/builtin/capabilities/
├── data/
│   ├── SmartCapabilitiesPreferences.kt
│   └── LocationPreferences.kt
├── domain/
│   ├── SmartCapabilitiesConfig.kt
│   ├── SmartCapabilitiesGate.kt
│   ├── SmartCapabilitiesMigration.kt
│   ├── SmartCapabilitiesSettings.kt
│   ├── LocationConfig.kt / LocationGate.kt / LocationSettings.kt
├── di/CapabilitiesModule.kt
├── presentation/SmartCapabilitiesScreen.kt / ViewModel
└── tools/LocationTool.kt   # last known，不持续定位
```

门闸联调：

- `WebSearchGate` / `DeviceSensingGate`：`masterEnabled` 传入 `master && assistantTools`
- `DeviceToolGate.smartMasterProvider`：`master && assistantTools`
- `ChatViewModel.resolvePersonaFilteredTools`：助手工具组 + 位置与周边
- `PlatformPlugin`：注册 `get_location`；导航 POI/酒店价复用位置与周边 + 助手工具（搜索）

## 5. 位置

- 工具名：`get_location`
- Manifest：`ACCESS_COARSE/FINE_LOCATION`（按需）
- **不** `requestLocationUpdates`；只读 `getLastKnownLocation`
- prefs ON ≠ 已授权；执行时无权限返回 `location_permission_denied`
- UI 文案：**位置与周边**（不并场景视觉）

## 6. 单测

`SmartCapabilitiesTest`：默认值、v1/v2 迁移、组 OR 关策略、master 级联、本地脑默认 false、别名门闸、位置 Gate。

## 7. 路由

- 新：`Route.SMART_CAPABILITIES`
- 旧细页（本地推理 / ASR / 系统工具 / 搜索 / 设备感知 / 场景）仍可从 **高级** 入口进入

## 8. 与导游 / 导航（拆开）

| 模块 | 文档 | 与本页关系 |
|------|------|------------|
| **导航 Navigate** | `docs/navigate.md` | 复用 **位置与周边** + **助手工具（联网搜索）** + 外链 Intent；**不要**并进看世界 |
| **导游 Guide** | `docs/guide.md` | 复用 **看世界** / 陪伴；位置仅作讲解增强 |

设置、默认开关、PR 描述均按两模块拆开，禁止 monolithic「实时向导」大开关。
