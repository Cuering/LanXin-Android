# 插件市场（Phase 5.5）

> 状态：✅ 实现于 `feat/phase5-5-plugin-market`  
> 依赖：5.3 动态加载、5.4 管理 UI / `PluginCatalog`

---

## 1. 目标

从 **远程 JSON 索引** 浏览插件元数据，下载 `.apk` 到 `filesDir/plugin-packages/`，并调用 `PluginCatalog.loadDynamicPlugin` 完成加载。

非目标：应用商店账号体系、付费、完整 APK 签名白名单（5.6）。

---

## 2. 默认市场 URL

| 键 | 值 |
|----|-----|
| **默认 Catalog URL** | `https://raw.githubusercontent.com/Cuering/LanXin-Android/main/docs/plugin-market-index.sample.json` |
| 代码常量 | `MarketDefaults.DEFAULT_CATALOG_URL` |
| DataStore 键 | `plugin_market_catalog_url`（`MarketDefaults.PREF_CATALOG_URL`） |
| 覆盖方式 | 市场页「索引 URL」对话框；留空恢复默认 |
| 远程失败 | `fallbackToSample = true` → 使用内存 `SampleMarketCatalog` |

> 注意：默认 URL 指向 **main** 分支 sample 文件。在 sample 合入 main 前，远程会 404，客户端应回退 sample 目录。

---

## 3. 索引 schema

```json
{
  "schema_version": 1,
  "updated_at": "2026-07-16T00:00:00Z",
  "plugins": [
    {
      "id": "example.hello",
      "name": "Hello Plugin",
      "version": "1.0.0",
      "description": "示例",
      "author": "LanXin",
      "download_url": "https://example.invalid/plugins/hello-1.0.0.apk",
      "min_app_version": "0.7.0",
      "checksum": "",
      "size": 0
    }
  ]
}
```

| 字段 | 说明 |
|------|------|
| `download_url` | APK 直链（HTTP GET） |
| `checksum` | 可选小写 hex SHA-256；空则跳过哈希校验 |
| `size` | 期望字节数；`<= 0` 不校验大小 |

解析：`MarketCatalogParser`（kotlinx.serialization，`ignoreUnknownKeys`）。

---

## 4. 组件

| 组件 | 路径 | 职责 |
|------|------|------|
| Models | `plugin/market/MarketModels.kt` | Entry / Catalog / 安装状态 |
| Settings | `MarketSettings` / `MarketPreferences` | Catalog URL |
| Repository | `PluginMarketRepository` + Remote / Sample / Composite | 拉索引 |
| HTTP | `MarketHttpFetcher` / `KtorMarketHttpFetcher` | 文本 + 文件下载 |
| Verifier | `PluginPackageVerifier` | size + sha256 |
| Installer | `DefaultPluginInstaller` | 下载→校验→落盘→load |
| UI | `PluginMarketScreen` / `PluginMarketViewModel` | 列表/搜索/安装/改 URL |
| DI | `plugin/market/di/PluginMarketModule.kt` | Hilt 绑定 |

---

## 5. 安装管线

```
用户点「安装」
  → MarketHttpFetcher.downloadToFile(download_url, *.apk.downloading)
  → PluginPackageVerifier.verify(size, checksum)
  → rename 到 plugin-packages/<safeId>.apk
  → PluginCatalog.loadDynamicPlugin(apk)
  → UI 刷新本地安装状态（NOT_INSTALLED / INSTALLED / UPDATE_AVAILABLE）
```

版本比较：`VersionComparator.isNewer`（与宿主更新共用）。

---

## 6. 导航入口

| 入口 | 路由 |
|------|------|
| 设置 →「插件市场」 | `Route.PLUGIN_MARKET` |
| 插件管理 →「插件市场」 | 同上 |
| 设置 →「插件管理」 | `Route.PLUGIN_MANAGER` |

---

## 7. 单测与 CI

- `MarketCatalogParserTest` / `PluginPackageVerifierTest` / `DefaultPluginInstallerTest`
- `PluginMarketRepositoryTest` / `PluginMarketViewModelTest`
- Workflow：`phase5-plugin-market-verify.yml`（unit + 表面 grep）

---

## 8. 安全说明

- 完整性：可选 checksum/size（市场层）
- 签名：仍走 5.3 `PluginSignatureVerifier`（MVP AllowAll → 5.6）
- 不信任索引中的任意代码：加载前仍需清单 / 冲突 / minAppVersion 检查
