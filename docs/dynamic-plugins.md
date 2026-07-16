# 动态插件加载设计（Phase 5.3–5.5）

> 状态：5.3 加载 ✅；5.4 管理 UI ✅；5.5 插件市场 ✅（`feat/phase5-5-plugin-market`）  
> 范围：从 `filesDir` 发现 / 解析 / 加载 `.apk` 插件包 + 管理界面 + 远程市场索引安装  
> 非目标：5.6 完整签名校验（钩子已预留）

---

## 1. 路径约定

| 路径 | 用途 |
|------|------|
| `context.filesDir/plugin-packages/` | **外部插件包目录**（放置 `*.apk`；市场下载目标） |
| `context.filesDir/plugins/<pluginId>/` | 插件运行时私有数据（既有 `PluginContext.filesDir`） |
| `context.filesDir/plugin-state.json` | enable / disable 持久化（简单 JSON） |

**为何不把 .apk 放在 `filesDir/plugins/`？**  
该路径已被 `PluginManager.createContext` 用作各插件的数据目录（`plugins/<id>/`），与包文件混放会冲突。

---

## 2. 插件包格式

插件包是标准 **Android APK**（可含 dex / resources），并在包内提供清单：

```
plugin.apk
├── classes.dex            # 实现 LanXinPlugin 的代码
├── AndroidManifest.xml    # 可选；用于 PackageManager 元数据回退
└── assets/
    └── lanxin-plugin.json # 必需（首选）
```

### 2.1 `assets/lanxin-plugin.json`

```json
{
  "id": "example.hello",
  "name": "Hello Plugin",
  "version": "1.0.0",
  "description": "示例外部插件",
  "entryClass": "com.example.hello.HelloPlugin",
  "author": "someone",
  "minAppVersion": "0.7.0",
  "removable": true
}
```

| 字段 | 必填 | 说明 |
|------|:----:|------|
| `id` | ✅ | 全局唯一；与内置插件 id 冲突时 **拒绝加载** |
| `name` | ✅ | 展示名 |
| `version` | ✅ | 插件自身版本（与 App 版本独立） |
| `entryClass` | ✅ | 实现 `LanXinPlugin` 的全限定类名；无参构造 |
| `description` | | 描述 |
| `author` | | 作者 |
| `minAppVersion` | | 最低宿主版本；不满足则跳过 |
| `removable` | | 默认 true；动态包恒为可移除 |

清单解析优先读 APK 内 zip entry `assets/lanxin-plugin.json`（纯 JVM `ZipFile` 可测）。

---

## 3. 加载流程

```
App 启动 / 手动 refresh / 市场安装后
  │
  ├─ 1. PluginPackageScanner.scan(plugin-packages/)
  │       → 列出 *.apk（忽略隐藏与非文件）
  │
  ├─ 2. 对每个 apk（或市场单包 loadDynamicPlugin）:
  │     ├─ PluginManifestParser.parseFromApk(apk)
  │     ├─ PluginSignatureVerifier.verify(apk)   // 5.6 钩子；默认 AllowAll
  │     ├─ minAppVersion 检查（VersionComparator）
  │     ├─ id 与已注册 builtin/compiled 冲突 → 失败不崩溃
  │     ├─ enable 状态（默认 true，可读 plugin-state.json）
  │     ├─ DynamicPluginClassLoaderFactory.create(apk)
  │     │     真机：dalvik.system.PathClassLoader(apk, parent)
  │     │     单测：可注入 Fake / 跳过实例化
  │     ├─ Class.forName(entryClass, true, cl).newInstance() as LanXinPlugin
  │     └─ PluginManager.registerDynamic(...) + 若 enabled 则 onLoad
  │
  └─ 失败项记入 PluginLoadResult.Failure，宿主继续运行
```

### 3.1 与 builtin / 编译期插件关系

| 来源 | 注册方式 | 可卸载 | ClassLoader |
|------|----------|:------:|-------------|
| builtin / plugins（源码内） | Hilt `@Provides` + `register()` | 否（removable=false 语义） | App ClassLoader |
| 动态 .apk | `discoverAndLoadDynamicPlugins()` / `loadDynamicPlugin` | 是 | 独立 PathClassLoader |

- **同一 `PluginManager`**，不另起冲突体系。
- 动态插件 id **不得**覆盖已注册 id。
- `getPlugins()` 同时返回编译期与动态插件；`PluginRecord.source` 区分来源。

---

## 4. 生命周期与状态

| 状态 | 含义 |
|------|------|
| DISCOVERED | 已扫描到包 + 清单合法，尚未实例化 |
| LOADED | 已 `register` + `onLoad`，工具可用 |
| DISABLED | 已登记但未 load / 已 unload 工具；包仍在磁盘 |
| FAILED | 解析 / 校验 / 实例化失败 |

### 4.1 API（`PluginManager` / `PluginCatalog`）

| 方法 | 说明 |
|------|------|
| `setEnabled(id, enabled)` | 内存 + 持久化；disable 时 unload 工具并 `onUnload` |
| `isEnabled(id)` | 默认 true（未写入状态时） |
| `discoverAndLoadDynamicPlugins()` | 扫描并加载 |
| `loadDynamicPlugin(file)` | 单包加载（5.5 市场安装后） |
| `unloadPlugin(id)` | 仅动态插件；编译期插件返回 false |
| `getPluginRecords()` | 含 source / enabled / apkPath 的列表 |
| `packagesDirectory()` | 动态包目录 |

### 4.2 隔离边界（MVP）

- **类加载隔离**：动态插件使用独立 ClassLoader，parent 为 App ClassLoader（可访问 `LanXinPlugin` 等宿主 API）。
- **数据隔离**：`PluginContext.filesDir` 仍为 `filesDir/plugins/<id>/`。
- **进程隔离**：不做；插件与宿主同进程（手机端性能优先）。
- **权限**：插件代码与宿主同 UID；5.6 签名 + 后续能力声明再收紧。

---

## 5. 安全边界（5.6 预留）

```kotlin
interface PluginSignatureVerifier {
    fun verify(apkFile: File): PluginSignatureResult
}

// MVP：AllowAllPluginSignatureVerifier — 始终 TRUSTED
// 5.6：校验 APK 签名证书是否在信任列表
```

加载管线在 ClassLoader 创建 **之前** 调用 verifier；`REJECTED` 则不加载并记 Failure。

市场侧另有 **文件完整性** 钩子：`PluginPackageVerifier`（size + 可选 sha256），与签名校验分离。

---

## 6. 单元测试策略

真机 Dex 加载难以在 JVM unit test 覆盖，本轮保证：

| 层 | 覆盖 |
|----|------|
| 路径约定 | `PluginPackagePaths` |
| 清单解析 | 内存 JSON + 临时 zip 内 `assets/lanxin-plugin.json` |
| 扫描 | 临时目录放置假 .apk / 非 apk |
| enable 状态机 | `PluginStateStore` load/save/setEnabled |
| 签名钩子 | AllowAll / RejectAll |
| PluginManager | mock loader 注入、冲突 id、disable 后 callTool 不可用 |
| 失败不崩溃 | 坏 JSON / 缺 entry / verifier reject |
| 管理 UI | `PluginManagerViewModelTest` + Fake `PluginCatalog`（含 `loadDynamicPlugin` stub） |
| 市场 | parser / verifier / installer / repository / ViewModel |

---

## 7. 后续

| 阶段 | 内容 |
|------|------|
| 5.4 | 管理 UI：列表 / 启用 / 停用 / 卸载文件 ✅ |
| 5.5 | 市场：GitHub 索引下载到 `plugin-packages/` ✅ |
| 5.6 | 签名证书白名单 + 用户确认 |

---

## 9. 管理 UI（Phase 5.4）

| 项 | 说明 |
|----|------|
| 路由 | `Route.PLUGIN_MANAGER` = `plugin_manager` |
| 入口 | 设置页 →「插件管理」；页内可进「插件市场」 |
| Screen | `presentation/ui/plugin/PluginManagerScreen` |
| ViewModel | `PluginManagerViewModel` |
| 门面 | `PluginCatalog`（`PluginManager` 实现，Hilt 绑定） |

### 9.1 能力

- 列表：编译期 + 动态插件（id / name / version / source / enabled / author）
- 启用/停用：`PluginCatalog.setEnabled`
- 重新扫描：`discoverAndLoadDynamicPlugins`，并展示 `getLastDynamicFailures`
- 动态插件卸载：`unloadPlugin`（APK 保留）
- 删除 APK：unload + 删除 `apkPath` 文件（二次确认）
- 提示：签名 MVP 为 AllowAll（5.6 换实现）

### 9.2 单测

- `PluginManagerViewModelTest`：Fake `PluginCatalog`，覆盖 refresh / setEnabled / unload / deleteApk；Fake 实现 `loadDynamicPlugin`

---

## 10. 插件市场（Phase 5.5）

详见 **`docs/plugin-market.md`**。

| 项 | 说明 |
|----|------|
| 路由 | `Route.PLUGIN_MARKET` = `plugin_market` |
| 入口 | 设置 →「插件市场」；插件管理页 →「插件市场」 |
| 默认索引 URL | `https://raw.githubusercontent.com/Cuering/LanXin-Android/main/docs/plugin-market-index.sample.json` |
| 配置覆盖 | DataStore 键 `plugin_market_catalog_url`（`MarketPreferences` / `MarketSettings`） |
| 安装管线 | 下载 → size/sha256 校验 → 落入 `plugin-packages/` → `PluginCatalog.loadDynamicPlugin` |
| 回退 | 远程失败时 `CompositePluginMarketRepository` 回退内置 sample |

---

## 11. 版本

| 字段 | 值 |
|------|-----|
| design_version | 3 |
| phase | 5.5 |
