# 智能能力模块（Smart Capabilities）

> 状态：**配置 + 主开关 + 迁移 + 位置 Gate ✅**（`feat/capability-module-defaults`）  
> 模块：`builtin/capabilities` · DataStore 键前缀 `smart_capabilities_`  
> 主开关：`smart_capabilities_master`（默认 **true**）

## 1. 目标

把设置页碎开关收成 **「智能能力」** 一页，统一默认与主开关级联：

```
设置
└─ 智能能力（主入口）
   ├─ [主开关] 默认 ON；关则子能力一律拒
   ├─ 状态摘要
   ├─ 本地推理 [OFF]
   ├─ 语音能力 [ON]
   ├─ 系统工具 [ON]   ← 写操作仍确认
   ├─ 联网搜索 [ON]
   ├─ 设备感知 [ON]
   ├─ 位置     [ON]   ← 新建 get_location；用时权限
   ├─ 场景视觉 [OFF]  ← consent + Gate
   └─ ▸ 高级（细页入口）
```

**不**纳入本页默认 ON 列表：Claw（仍在设置根）、桌宠悬浮（桌宠页，默认 OFF）。

## 2. 默认与迁移

| 子能力 | 新默认 | 迁移 |
|--------|--------|------|
| 主开关 | ON | 固定 DEFAULT_MASTER |
| 本地推理 | **OFF** | 从未配置不抬 ON；显式 true 保留 |
| 语音 | ON | 从未配置 → ON；显式 false 保留 |
| 系统工具 | ON | 同上；子项从未写则随 master 抬 ON |
| 联网搜索 | ON | 同上 |
| 设备感知 | ON | 同上 |
| 位置 | ON | 新建 |
| 场景视觉 | **OFF** | 不抬 ON；显式 true 保留 |

标记：`smart_capabilities_migrated_v1`。  
应用启动（`LanXinApp`）与设置页进入时 `ensureMigrated()`。

公式：

```
effective(child) = master && child.enabled && runtime_ready
```

## 3. 代码位置

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

- `WebSearchGate` / `DeviceSensingGate`：`masterEnabled` 参数
- `DeviceToolGate`：`smartMasterProvider`
- `ChatViewModel.resolvePersonaFilteredTools`：主开关 + 位置 filter
- `PlatformPlugin`：注册 `get_location`

## 4. 位置

- 工具名：`get_location`
- Manifest：`ACCESS_COARSE/FINE_LOCATION`（按需）
- **不** `requestLocationUpdates`；只读 `getLastKnownLocation`
- prefs ON ≠ 已授权；执行时无权限返回 `location_permission_denied`

## 5. 单测

`SmartCapabilitiesTest`：默认值、迁移、master 级联、本地脑默认 false、位置 Gate。

## 6. 路由

- 新：`Route.SMART_CAPABILITIES`
- 旧细页（本地推理 / ASR / 系统工具 / 搜索 / 设备感知 / 场景）仍可从高级入口进入
