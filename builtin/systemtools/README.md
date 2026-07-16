# builtin/systemtools — 系统能力（Phase 7）

统一设备工具：日历 / 精确闹钟 / 内置笔记 / 用户文件。

与桌宠 VoiceSession、Chat、MCP **共用** `DeviceTool` 契约——**陪伴操控一体**。

详见 [docs/system-tools.md](../../docs/system-tools.md)。

## 7.3 工具

| 工具 | 说明 |
|------|------|
| `alarm_set` | 默认 `setAlarmClock`；`mode=intent` → **startActivity SET_ALARM** |
| `alarm_show` | **startActivity SHOW_ALARMS** |
| `calendar_list_upcoming` | `CalendarContract.Instances`（无 READ_CALENDAR → Denied） |
| `calendar_create_event` | 默认 **INSERT Intent**；`mode=stub` 内存（需 confirmed） |
| `note_create` / `note_list` / `note_append` | Room 持久化 |
| `note_update` / `note_delete` | 更新 / 删除（delete 始终需 confirmed） |
| `note_export` / `note_import` | SAF + 分享；format=json\|markdown |

## 代码

实现在 `app/src/main/kotlin/com/lanxin/android/builtin/systemtools/`：

- `domain/DeviceTool` + `DeviceToolGate` + `CalendarGateway` + `AlarmClockGateway`
- `domain/NotesStore` + `NotesCodec` + `NotesSafGateway`
- `domain/SystemToolsIntentLauncher` + `CalendarIntentBuilder` + `AlarmIntentBuilder`
- `data/AndroidCalendarReader` + `AndroidAlarmSetter` + `AndroidSystemToolsIntentLauncher`
- `data/notes/RoomNotesStore` + `NotesDatabase` + `AndroidNotesSafGateway`
- `receiver/SystemToolsAlarmReceiver`
- `SystemToolsPlugin`（MCP 注册，经 Gate）
- 设置页 `SystemToolsScreen`（默认全关 + 权限引导 + 笔记导入导出）

写操作默认需 `confirmed=true`；删除为 `EXPLICIT_APPROVE`。

## 测试

```bash
./gradlew :app:testDebugUnitTest --tests "com.lanxin.android.builtin.systemtools.*"
```
