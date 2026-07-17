# 机器人 / Claw 动态插件宿主

> 状态：**MVP ✅**（`feat/claw-host-dynamic-plugins`）  
> 依赖：Phase 5.3–5.6 动态加载 / 管理 UI / 市场 / 签名  
> 非目标：具体微信/Telegram 协议实现、VPN 换 IP、真扫码 Activity、商店级审核

---

## 1. 目标

在 **既有动态插件链路**（`plugin-packages` → 签名 → enable → tools）之上，补齐手机侧「常驻宿主」产品路径：

| 能力 | 说明 |
|------|------|
| **配置门闸** | DataStore `claw_host_enabled` / `claw_host_resident_requested`；**默认关** |
| **PlatformHost** | keep-alive 引用、状态通知、扫码请求桩；关则 `NoOpPlatformHost` |
| **常驻 FGS** | `ClawResidentService`（`dataSync`）；仅双开关均为 true 时启动 |
| **生命周期钩子** | 插件可选实现 `ResidentCapablePlugin` |
| **设置页** | 设置 →「机器人 / Claw 宿主」；跳转插件管理 / 市场 |

**不重复** 5.3–5.6：扫描、ClassLoader、签名策略、市场安装仍走 `PluginCatalog`。

---

## 2. 安全默认

| 项 | 行为 |
|----|------|
| 总开关默认 | **关** → Host 能力 NoOp，不启动 FGS |
| 常驻默认 | **关**；需总开关 + 常驻二次确认 |
| 动态包安装 | 仍须市场下载 + size/sha256 + **签名策略**（release 默认 allowlist） |
| 不静默装包 | 无后台静默安装路径；用户经市场/管理 UI |
| 关总开关 | 自动取消常驻请求并 stopService |

---

## 3. 架构

```
设置页 ClawHostScreen
        │ DataStore (claw_host_*)
        ▼
  ClawHostSettings / ClawHostPreferences
        │
        ├─ ClawHostGate（纯逻辑）
        │
        ├─ DefaultPlatformHost  ← PlatformHost 绑定
        │       └─ keepAlive / status / qrRequests（内存）
        │
        └─ ClawResidentController.syncFromSettings()
                └─ start/stop ClawResidentService
                        └─ ResidentCapablePlugin.onResidentStart/Stop
```

### 与动态插件关系

```
LanXinApp.onCreate
  → pluginManager.loadAll()
  → discoverAndLoadDynamicPlugins()   // 既有；签名/enable
  → clawResidentController.syncFromSettings()  // 默认 no-op
```

| 来源 | 注册 | 常驻 |
|------|------|------|
| builtin / 编译期 plugins | Hilt register | 可实现 ResidentCapablePlugin |
| 动态 .apk | PathClassLoader + PluginCatalog | 同上；id 不冲突 |

---

## 4. PlatformHost API（MVP）

| 方法 | 关 | 开 |
|------|----|----|
| `isCapabilityOpen` | false | true |
| `requestKeepAlive` | false | 登记引用 |
| `showStatusNotification` | no-op | 更新内存状态（Service 刷新通知） |
| `postQrScanRequest` | null | 返回 requestId（无真 UI） |
| `isResidentRunning` | Service 同步标志 | |

---

## 5. 代码位置

```
app/src/main/kotlin/com/lanxin/android/plugin/claw/
├── domain/
│   ├── ClawHostConfig.kt
│   ├── ClawHostSettings.kt
│   ├── ClawHostGate.kt
│   └── PlatformHost.kt          # + ResidentCapablePlugin + NoOp
├── data/
│   ├── ClawHostPreferences.kt
│   ├── DefaultPlatformHost.kt
│   ├── ClawResidentController.kt
│   └── ClawResidentService.kt
└── presentation/
    ├── ClawHostScreen.kt
    └── ClawHostViewModel.kt
```

DI：`plugin/di/PluginModule` 绑定 `ClawHostSettings` / `PlatformHost`。  
Manifest：`ClawResidentService` + `foregroundServiceType=dataSync`。

---

## 6. 单测 / CI

- `ClawHostGateTest`：默认关、双开关、NoOp
- `DefaultPlatformHostTest`：Fake settings 下 keepAlive / qr / status
- Workflow：`.github/workflows/claw-host-verify.yml`

---

## 7. 非目标（本 PR）

- 具体 IM 协议 / 登录态 / VPN
- 真实 CameraX 扫码页
- 重做插件市场或签名策略
- 本机 `./gradlew`（验证走 GitHub Actions）

---

## 8. 版本

| 字段 | 值 |
|------|-----|
| design_version | 1 |
| phase | claw-host-mvp |
