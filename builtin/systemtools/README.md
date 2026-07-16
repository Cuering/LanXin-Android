# builtin/systemtools — 系统能力（Phase 7）

统一设备工具：日历 / 精确闹钟 / 内置笔记 / 用户文件。

与桌宠 VoiceSession、Chat、MCP **共用** `DeviceTool` 契约——**陪伴操控一体**。

详见 [docs/system-tools.md](../../docs/system-tools.md)。

## 7.2 工具

| 工具 | 说明 |
|------|------|
| `alarm_set` | 默认 `AlarmManager.setAlarmClock`；`mode=intent` → AlarmClock Intent 规格 |
| `alarm_show` | `SHOW_ALARMS` Intent 规格 |
| `calendar_list_upcoming` | `CalendarContract.Instances`（无 READ_CALENDAR 返回 Denied 提示） |
| `calendar_create_event` | stub 写入（需 confirmed） |
| `note_*` | 内存笔记 stub（7.3 再持久化） |

## 代码

实现在 `app/src/main/kotlin/com/lanxin/android/builtin/systemtools/`：

- `domain/DeviceTool` + `DeviceToolGate` + `CalendarGateway` + `AlarmClockGateway`
- `data/AndroidCalendarReader` + `AndroidAlarmSetter` + `DeviceToolRegistry`
- `receiver/SystemToolsAlarmReceiver`
- `SystemToolsPlugin`（MCP 注册）
- 设置页 `SystemToolsScreen`（默认全关 + 权限引导）

写操作默认需 `confirmed=true`。

## 测试

```bash
./gradlew :app:testDebugUnitTest --tests "com.lanxin.android.builtin.systemtools.*"
```
