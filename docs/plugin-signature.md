# 插件签名验证（Phase 5.6）

> 状态：✅ MVP（`feat/phase5-6-plugin-signature`）  
> 依赖：5.3 动态加载钩子、5.4 管理 UI、5.5 市场 `loadDynamicPlugin`  
> 非目标：证书链在线吊销、商店级审核、付费签名服务

---

## 1. 目标

替换 5.3 默认「全部信任」为**可配置策略**，在加载 / 市场安装路径上失败时返回可读错误，并在管理 UI 展示策略与校验状态。

---

## 2. 策略

| wire 名 | 类型 | 行为 |
|---------|------|------|
| `allow_all` | AllowAll | 始终信任（**debug 默认**） |
| `deny_all` | DenyAll | 始终拒绝 |
| `allowlist` | Allowlist | 证书 SHA-256 命中白名单才信任；**名单为空 = 失败关闭** |

默认策略选择（无配置文件时）：

- `ApplicationInfo.FLAG_DEBUGGABLE` → `allow_all`
- 非 debuggable（典型 release）→ `allowlist`（空名单则全部拒绝动态加载）

工程当前无 product flavor 区分 release 策略开关；以 debuggable 标志 + 配置文件覆盖为准。

---

## 3. 配置文件

路径：`context.filesDir/plugin-signature.json`

```json
{
  "policy": "allowlist",
  "allowlist": [
    "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
  ]
}
```

| 字段 | 说明 |
|------|------|
| `policy` | `allow_all` / `deny_all` / `allowlist` |
| `allowlist` | 证书 SHA-256 小写 hex 数组（可带冒号，加载时规范化） |
| `allowlist_csv` | 可选兼容字段，逗号/换行分隔 |

API：

- `PluginManager.getSignatureConfig()` / `setSignatureConfig(...)`
- `PluginCatalog.currentSignaturePolicy()` → wire 名（UI 页眉）

---

## 4. 证书摘要提取

| 组件 | 说明 |
|------|------|
| `ApkCertDigestProvider` | 接口：`digests(apk): Result<List<String>>` |
| `JarApkCertDigestProvider` | JVM `JarFile` 读 V1/JAR 签名证书 → SHA-256 |
| `FixedApkCertDigestProvider` | 单测 fixture |

说明：纯 V2/V3、无 V1 签名的包可能提取为空列表；allowlist 下按失败关闭拒绝。真机后续可换 `PackageManager` 实现而不改策略层。

市场侧 **size/sha256 文件完整性**（`PluginPackageVerifier`）与签名校验分离：完整性先过，再走 `PluginSignatureVerifier`。

---

## 5. 加载路径

```
loadDynamicPlugin / discoverAndLoadDynamicPlugins
  → DynamicPluginLoader.loadPackage
      → PluginSignatureVerifier.verify(apk)
      → Trusted：继续 ClassLoader / 实例化
      → Rejected：PluginLoadResult.Failure(reason 含「签名校验失败」与策略名)
```

- 不抛异常、不崩溃宿主
- 市场安装：`DefaultPluginInstaller` 在 load 失败时返回 `PluginInstallResult.Failure`，文案含签名原因

---

## 6. UI

| 位置 | 展示 |
|------|------|
| 插件管理页眉 | `签名策略：allow_all|deny_all|allowlist` |
| 动态插件卡片 | `签名: 已校验 (policy)` |
| 失败列表 | 含「签名问题」标签 + reason |
| 市场安装 | snackbar / Failure 文案透传 load 失败原因 |

编译期插件：`PluginSignatureStatus.NOT_APPLICABLE`。

---

## 7. 如何加白名单

1. 取得插件签名证书 SHA-256（小写 hex，可有可无冒号）。
2. 写入 `filesDir/plugin-signature.json`：
   - `"policy": "allowlist"`
   - `allowlist` 数组加入摘要
3. 或代码：`pluginManager.setSignatureConfig(PluginSignatureConfig(ALLOWLIST, setOf("...")))`
4. 重新扫描 / 市场重装。

Debug 开发可继续用 `allow_all`；发版前建议切 `allowlist` 并写入信任证书。

---

## 8. 单测

| 测试 | 覆盖 |
|------|------|
| `PluginSignatureVerifierTest` | AllowAll / DenyAll / allowlist 命中与拒绝 / 空名单 / 提取失败 / 策略切换 / normalize |
| `PluginSignatureConfigStoreTest` | 默认 / 持久化 / setPolicy |
| `DynamicPluginLoaderTest` | RejectAll 失败路径 |
| `PluginManagerViewModelTest` | 策略名进 UI state；签名失败文案 |

---

## 9. 版本

| 字段 | 值 |
|------|-----|
| design_version | 1 |
| phase | 5.6 |
