# Phase 7 — System Tools / Device Skills

> 状态：**7.1 骨架 🚧**（`feat/phase7-system-tools-skeleton`）  
> 模块：`builtin/systemtools` + `app/.../builtin/systemtools/*`  
> 产品线：**桌宠 + 操控手机 = 陪伴操控一体**（与 Phase 6 VoiceSession 同一会话，非旁路 App）  
> 与 ChatRouter `needsTools`、桌宠 VoiceSession、MCP 共用统一 `DeviceTool` 契约。

## 1. 目标与范围

让兰心通过**统一 Tool/Skill 接口**调用手机能力——系统日历、闹钟、笔记、用户文件。

| 域 | 能力 | 优先策略 |
|----|------|----------|
| **Calendar** | 读即将到来的事件 / 写简单事件 | 读：`CalendarContract`（`READ_CALENDAR`）；写：**优先 Intent** 少权限，或 `WRITE_CALENDAR` |
| **Alarm** | 设置闹钟 / 打开闹钟列表 | **`AlarmClock.ACTION_SET_ALARM` / `ACTION_SHOW_ALARMS` Intent**，不抢系统时钟实现；应用内提醒继续 `builtin/scheduler` |
| **Notes** | 创建 / 列表 / 追加 | Android **无统一系统笔记 API** → 内置轻量笔记 + 可选 `CREATE_DOCUMENT` / 分享 Intent |
| **User File** | 列表 / 读文本 / 写 / 分享 / 删 | **仅用户可访问文件**：SAF + MediaStore + `getExternalFilesDir`；禁止 root / 改 `/system` |

### 一体工作流（听 → 想 → 办 → 说）

```
用户对桌宠说话 / 聊天输入 / MCP
        │
        ▼
  听（ASR / 文本）→ 想（ChatRouter / PetChatResponder）
        │
        ├─ 纯闲聊 → 回复 → 说（TTS / 字幕）
        │
        └─ 要办事 → DeviceToolGate（开关 + 确认）
                      ├─ DeviceToolRegistry（alarm / calendar / notes / files）
                      └─ 结果回灌 → 想（总结）→ 说
```

工具给 **Chat 与 VoiceSession 共用**，不是旁路独立 App。

## 2. 架构

```
DeviceTool (interface)
    ├── name / capability / permissions / sideEffect / confirmationLevel
    └── invoke(args, confirmed)

DeviceToolGate
    ├── master switch
    ├── per-capability switch
    └── write/delete confirmation

SystemToolsPlugin (LanXinPlugin)
    └── registerTool → MCP / Chat tool_call

ChatRouter.needsTools ──► 云端（本地暂无 tool_call）
VoiceSession 预留 tool 钩子（后续接同一 Registry）
```

### 代码位置

```
app/src/main/kotlin/com/lanxin/android/builtin/systemtools/
├── SystemToolsPlugin.kt
├── domain/
│   ├── DeviceModels.kt          # Config / Permission / Outcome
│   ├── DeviceTool.kt            # 接口 + DeviceToolIds
│   ├── DeviceToolGate.kt        # 门闸纯逻辑
│   ├── AlarmIntentBuilder.kt    # AlarmClock Intent 规格（无 Android Runtime）
│   └── SystemToolsSettings.kt
├── data/
│   ├── SystemToolsPreferences.kt
│   ├── StubCalendarGateway.kt
│   ├── StubNotesStore.kt
│   └── StubDeviceTools.kt       # M1 stub 工具 + DeviceToolRegistry
├── di/
│   └── SystemToolsModule.kt
└── presentation/
    ├── SystemToolsScreen.kt     # 设置 → 系统能力
    └── SystemToolsViewModel.kt

docs/system-tools.md             # 本文
builtin/systemtools/README.md
```

## 3. 工具清单

### M1（本 PR，stub 可跑通）

| 工具 | 副作用 | 确认 | 说明 |
|------|--------|------|------|
| `alarm_set` | LAUNCH_INTENT | 是* | 构建 `SET_ALARM` Intent 规格；**不** `startActivity` |
| `alarm_show` | LAUNCH_INTENT | 否 | `SHOW_ALARMS` 规格 |
| `calendar_list_upcoming` | READ | 否 | 内存 stub 事件列表 |
| `calendar_create_event` | WRITE | 是* | stub 写入 |
| `note_create` | WRITE | 是* | 内存笔记 |
| `note_list` | READ | 否 | 列表 |
| `note_append` | WRITE | 是* | 追加 |

\* 当设置「写操作需确认」开启（默认）时，须 `confirmed=true`。

### 规划（M2+，本 PR 仅 ID 预留）

| 工具 | 阶段 | 说明 |
|------|------|------|
| `file_pick` | M2 | SAF 选文件 / 树 |
| `file_list` | M2 | persistable URI 列表 |
| `file_read_text` | M2 | 文本摘要（限字节） |
| `file_write` | M2 | 写用户选中目录 / 私有 |
| `file_share` | M2 | 分享 Intent |
| （删除） | M3 | 必须 `EXPLICIT_APPROVE` |

完整 ID 见 `DeviceToolIds.ALL`。

## 4. 里程碑（对齐 ARCHITECTURE Phase 7.1–7.6）

| 阶段 | 内容 | 状态 |
|------|------|------|
| **7.1 骨架** | `DeviceTool` + 门闸 + Fake/Stub + 单测 + 设置总开关 + 本文档 + CI | 🚧 本 PR |
| **7.2 闹钟 + 日历** | Intent 设闹钟；日历读列表 + 创建（确认流） | 🔜 |
| **7.3 笔记** | 应用内笔记 CRUD + 导出/分享 | 🔜 |
| **7.4 文件** | SAF 授权目录列表、读文本、写/分享/删（确认） | 🔜 |
| **7.5 对话/桌宠一体接入** | ChatRouter `needsTools` + VoiceSession 听→想→**办**→说 | 🔜 |
| **7.6 打磨** | 权限引导 UX、隐私文案、失败降级、审计日志（可选） | 🔜 |

### 7.1 交付清单（本 PR）

- [x] 设计文档 `docs/system-tools.md`（范围 / 原则 / 工具 ID / 权限表 / 一体工作流）
- [x] `DeviceTool` + 权限枚举 + `DeviceToolGate`（写操作需确认策略）
- [x] Fake/Stub：`alarm_set` Intent builder、`calendar_list_upcoming` stub 列表
- [x] 单元测试（AlarmIntentBuilder / Calendar stub / Gate / Config / Ids）
- [x] 设置页「系统能力」总览（默认全关）
- [x] ARCHITECTURE / README 交叉链接
- [x] CI workflow 轻量 verify（不 curl 模型）
- [x] VoiceSession / ChatRouter：**仅预留 hook 注释**，不重做会话

## 5. 权限表

| 能力 | Manifest / 运行时 | M1 | 备注 |
|------|-------------------|----|------|
| 闹钟 Intent | 无 | stub | 不申请 `SET_ALARM` 特殊权限路径外能力 |
| 读日历 | `READ_CALENDAR` | 不申请 | M2 再声明 |
| 写日历 | `WRITE_CALENDAR` 或 Intent | 不申请 | **优先 Intent** |
| 笔记 | 无 / 私有存储 | stub 内存 | |
| 用户文件 | SAF（用户授权） | 未实现 | 禁止静默全盘扫描 |
| 悬浮窗等 | 无关 | — | |

## 6. 隐私与安全

1. **默认全关**：总开关 + 分项；用户主动开启。
2. **写/删需确认**：`DeviceToolGate` + `confirmed` 参数。
3. **禁止**：root、改系统分区、无障碍乱点第三方、静默扫敏感路径。
4. **不做**：真机厂商笔记深度集成（本 Phase 范围外）。
5. **不下模型**：本模块无网络模型下载。

## 7. 与现有模块关系

| 模块 | 关系 |
|------|------|
| `builtin/platform` | 剪贴板 / 已装应用 / 系统信息 / 私有 file_ops / 通用 app_intent；**不**替代日历闹钟 |
| `builtin/scheduler` | 应用内精确闹钟 / WorkManager；系统闹钟 App 走本模块 Intent |
| `builtin/local_inference` ChatRouter | `needsTools=true` 时走云端 tool 链，可调用本插件工具（7.5 完整批准 UI） |
| `builtin/pet` VoiceSession | 预留 tool 钩子注释（`VoiceSessionCoordinator` TODO phase7.5），接 `DeviceToolRegistry` |

## 8. 配置键（DataStore）

| Key | 默认 |
|-----|------|
| `system_tools_master_enabled` | `false` |
| `system_tools_calendar_enabled` | `false` |
| `system_tools_alarm_enabled` | `false` |
| `system_tools_notes_enabled` | `false` |
| `system_tools_user_file_enabled` | `false` |
| `system_tools_require_confirm_on_write` | `true` |

## 9. 单测

```bash
./gradlew :app:testDebugUnitTest --tests "com.lanxin.android.builtin.systemtools.*"
```

- `AlarmIntentBuilderTest` — Intent 规格 / 边界
- `CalendarListUpcomingStubTest` — list / create
- `DeviceToolGateTest` — 开关与确认
- `DeviceToolIdsTest` / `SystemToolsConfigTest`

## 10. 明确不做（本 PR）

- 真机厂商笔记深度集成
- 无障碍乱点第三方 App
- 系统目录修改
- 服务器下载任何东西
- auto-merge
