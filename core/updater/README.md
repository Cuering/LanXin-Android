# core/updater — 更新与备份

参考 AstrBot 更新系统，Android 侧通过 GitHub Releases + APK Intent 安装。

## 组件

| 类 | 职责 |
|----|------|
| `UpdateChecker` | 拉取 GitHub Releases，Semver 比较 |
| `VersionComparator` | Semver 比较（含预发布标签） |
| `ApkDownloader` | APK 下载（Flow 进度）+ 调起安装器 |
| `DataBackupManager` | 打包 zip 备份 |
| `DataRestoreManager` | 从 zip 还原 |
| `ui/*` | Compose 进度/版本选择弹窗 |

## 备份内容（Phase 1 硬编码）

```
lanxin_backup_<time>.zip
├── manifest.json
├── databases/
│   ├── lanxin_memory.db
│   ├── chat / chat_v2 (+ wal/shm)
└── datastore/
```

## 流程

检查更新 → 选择版本 → 自动备份 → 下载 APK → 系统安装器

入口：设置页「检查更新」。

默认仓库：`lanxin-ai/LanXin-Android`（可在 `UpdateChecker.owner/repo` 覆盖）。
