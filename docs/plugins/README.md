# 兰心可选插件（GitHub 市场）

本目录与 [plugin-market-index.json](../plugin-market-index.json) 对应。

## 产品策略

| 插件 ID | 名称 | 默认 |
|---------|------|------|
| `lanxin.local_inference` | 本地脑 | **不启用**（主程序走 MNNChat/云端） |
| `lanxin.asr` | 离线 ASR | **不启用** |
| `lanxin.tts` | TTS | **默认启用**，可关 |
| `lanxin.guide` | 导游 | **不启用** |

## 安装 / 启用方式

1. **编译期内置（当前）**：上述能力仍打进主 APK，但以 `PluginManager.register(..., defaultEnabled=…)` 控制；
   打开 App → **设置 → 插件管理**，对「本地脑 / ASR / 导游」点启用，对 TTS 可关闭。
2. **市场索引**：默认 URL  
   `https://raw.githubusercontent.com/Cuering/LanXin-Android/main/docs/plugin-market-index.json`
3. **动态 APK**（后续）：将签名插件包放到 Release 附件或 `docs/plugins/*.apk`，在索引里填 `download_url` + checksum，即可在插件市场下载安装加载。

## 与 MNNChat

日常对话请配置平台 **MNNChat**（OpenAI 兼容 `http://<手机IP>:8080/v1/`）。本地脑插件仅在需要完全离线端侧推理时启用。
