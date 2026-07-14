# builtin/platform — 手机平台专属工具

封装**必须在 Android 端执行**的 MCP 工具。服务端已可完成的能力（shell、文件、web_search 等）不在此列。

## MCP 工具

| 工具 | 说明 | Android API |
|------|------|-------------|
| `clipboard_get` | 读取系统剪贴板 | `ClipboardManager` |
| `clipboard_set` | 写入系统剪贴板 | `ClipboardManager` |
| `app_install_check` | 查询已安装应用 / 精确查包 | `PackageManager` |
| `system_info` | 设备型号、Android 版本、屏幕、网络、电量 | `Build` / `ConnectivityManager` / `BatteryManager` |

## 参数速览

### clipboard_set
- `text`（必填）：写入文本
- `label`：ClipData 标签，默认 `lanxin`

### app_install_check
- `package_name`：精确包名；非空时只查是否安装（**不依赖** `QUERY_ALL_PACKAGES`）
- `query`：模糊过滤（包名或应用名包含）
- `include_system`：是否含系统应用，默认 `false`
- `limit`：返回上限，默认 50，最大 500

### system_info
无参数。返回 `device` / `android` / `app` / `screen` / `network` / `battery` 分组 JSON。

## 权限

| 能力 | 权限 |
|------|------|
| 剪贴板 | 无需额外权限 |
| 精确包名查询 | 无需；`getApplicationInfo` 可直接用 |
| 枚举全部应用 | `QUERY_ALL_PACKAGES`（Android 11+，已声明） |
| 网络状态 | 无需（使用系统 ConnectivityManager） |
| 电量 | 无需（`ACTION_BATTERY_CHANGED` 粘性广播） |

## 代码位置

```
app/src/main/kotlin/com/lanxin/android/builtin/platform/
├── PlatformPlugin.kt      # 插件入口，注册 4 个工具
├── di/PlatformModule.kt   # Hilt DI + PluginManager 注册
└── tools/
    ├── ClipboardTool.kt
    ├── AppInstallCheckTool.kt
    └── SystemInfoTool.kt
```
