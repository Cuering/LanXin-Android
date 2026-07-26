# 兰心可选插件（已从主程序剥离）

以下能力**不再默认注册/安装到主程序**，请从插件市场安装动态包后加载：

| 插件 ID | 名称 | 说明 |
|---------|------|------|
| `lanxin.local_inference` | 本地脑 | 端侧 LLM |
| `lanxin.asr` | 离线 ASR | 语音识别 |
| `lanxin.tts` | TTS | 语音合成（引擎源码仍在主包，默认不注册为插件） |
| `lanxin.guide` | 导游 | 看世界讲解 |
| `lanxin.navigate` | 导航 | 调起系统导航 |

## 安装

1. 设置 → **插件市场**（索引：`docs/plugin-market-index.json`）
2. 或将签名 `.apk` 放入 `filesDir/plugin-packages/` 后刷新
3. 清单格式见 [docs/dynamic-plugins.md](../dynamic-plugins.md)

## 主程序默认

- 对话：云端 / **MNNChat** API
- 桌宠 / 陪伴：保留
- 麦克风听写默认关闭
