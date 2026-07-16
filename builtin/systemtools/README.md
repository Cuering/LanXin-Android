# builtin/systemtools — 系统能力（Phase 7）

统一设备工具：日历 / 系统闹钟 Intent / 内置笔记 / 用户文件。

与桌宠 VoiceSession、Chat、MCP **共用** `DeviceTool` 契约——**陪伴操控一体**。

详见 [docs/system-tools.md](../../docs/system-tools.md)。

## 7.1 工具（stub）

| 工具 | 说明 |
|------|------|
| `alarm_set` / `alarm_show` | AlarmClock Intent 规格 stub（不 startActivity） |
| `calendar_list_upcoming` / `calendar_create_event` | 内存日历 stub |
| `note_create` / `note_list` / `note_append` | 内存笔记 stub |

## 代码

实现在 `app/src/main/kotlin/com/lanxin/android/builtin/systemtools/`：

- `domain/DeviceTool` + `DeviceToolGate` + `AlarmIntentBuilder`
- `data/StubDeviceTools` + `DeviceToolRegistry`
- `SystemToolsPlugin`（MCP 注册）
- 设置页 `SystemToolsScreen`（默认全关）

写操作默认需 `confirmed=true`。

## 测试

```bash
./gradlew :app:testDebugUnitTest --tests "com.lanxin.android.builtin.systemtools.*"
```
