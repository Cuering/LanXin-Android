# 设备感知（Device Sensing / system_info）

> 状态：**配置 + 门闸 ✅**（`feat/device-sensing-gate`）  
> 模块：`builtin/platform` · 工具 `system_info` · DataStore 键 `device_sensing_enabled`  
> 与 ChatRouter `needsTools`：**不打架**——有工具 ≠ 首轮强制云端；仅 tool_call 循环才 `needsTools=true`。

## 1. 目标

补齐「**可配置 + 可被 Agent 安全调用**」：

| 层 | 行为 |
|----|------|
| **设置** | 设置 → 设备感知：总开关；**默认关** |
| **Agent 可见性** | 关：`DeviceSensingGate.filterTools` 从 prompt 工具列表移除 `system_info` |
| **执行** | 关：handler 返回 `code=system_info_disabled`；开：`SystemInfoTool.collect()` |
| **路由** | 不改 ChatRouter；不在首轮因注册了 system_info 而 `needsTools=true` |

## 2. 能力边界（只读）

| 区块 | 字段示例 | 权限 |
|------|----------|------|
| device | manufacturer / brand / model / is_emulator | 无 |
| android | release / sdk_int / security_patch | 无 |
| app | package_name / version / android_id | 无（Secure.ANDROID_ID） |
| screen | width/height px·dp / density / orientation | 无 |
| network | available / type / metered | 既有 ACCESS_NETWORK_STATE |
| battery | percent / status / charging / temperature | 粘性广播，无额外权限 |

**明确不含**：位置、通讯录、短信、麦克风、摄像头、无障碍、UsageStats、系统分区读写。  
摄像头场景识别见独立能力 [`docs/scene-sensing.md`](./scene-sensing.md)（默认关 + 确认 Gate）。

## 3. 架构

```
设置页 DeviceSensingScreen
        │ DataStore (device_sensing_enabled)
        ▼
  DeviceSensingSettings / DeviceSensingPreferences
        │
        ├─ ChatViewModel.resolvePersonaFilteredTools
        │       └─ WebSearchGate.filterTools
        │       └─ DeviceSensingGate.filterTools  → 系统提示工具列表
        │
        └─ PlatformPlugin.system_info handler
                └─ DeviceSensingGate.denyIfDisabled → SystemInfoTool.collect
```

### 代码位置

```
app/src/main/kotlin/com/lanxin/android/builtin/platform/
├── PlatformPlugin.kt              # 注册 system_info + 门闸
├── data/DeviceSensingPreferences.kt
├── domain/
│   ├── DeviceSensingConfig.kt
│   ├── DeviceSensingSettings.kt
│   └── DeviceSensingGate.kt
├── presentation/
│   ├── DeviceSensingScreen.kt
│   └── DeviceSensingViewModel.kt
├── di/PlatformModule.kt           # Binds DeviceSensingSettings
└── tools/SystemInfoTool.kt        # 既有采集实现
```

## 4. 默认与隐私

- **默认关**（安全默认；改变原先「始终对 Agent 可见」行为）
- 只读、无危险权限；不外发到服务器（结果仅回灌本轮 tool_call）
- 不下载大模型、不改 needsTools 产品规则
- 与系统能力（Phase 7 DeviceTool）分离：本能力是轻量平台上下文，不是写日历/闹钟

## 5. 单测 / CI

- `DeviceSensingGateTest`：开关过滤 + deny + 与 WebSearchGate 链式
- `PlatformToolHelpersTest`：工具名含 `system_info`
- Workflow：`.github/workflows/device-sensing-verify.yml`

## 6. 非目标（本 PR）

- UsageStats / 截屏场景感知（桌宠 M5 扩展；摄像头最小场景见 `docs/scene-sensing.md`）
- 精确定位、传感器流、后台持续采样
- 本机全量 Gradle 编译（验证走 CI）
