# Debug 资源方案：免费可爱 Live2D + 开源中文语音

> 状态：**M1 已合 main** · **M2a 路径闭环 + 设置就绪**  
> 相关：[`meiju-style-pet.md`](./meiju-style-pet.md) · [`voice-asr.md`](./voice-asr.md) · [`local-inference.md`](./local-inference.md)

## 0. 原则

| 原则 | 说明 |
|------|------|
| 默认走开源 | Live2D 官方 Sample + sherpa-onnx ASR/TTS |
| 大文件不进 git | 脚本拉到 `debug-assets/`（已 gitignore） |
| **下载位置** | **仅开发者机器**，或按需的 GitHub Actions；**禁止** AstrBot 服务器缓存模型当交付 |
| CI 无模型仍绿 | stub / 占位 HTML；可选检查脚本与文档存在 |
| 妹居仅 fallback | **禁止**上传妹居 so / moc3 / mnn / wav / 商业人设到 GitHub |
| 分发合规 | App 发布时用户自备合规 Live2D；Debug 可脚本拉官方 sample |

一键（推荐 M2 入口）：

```bash
bash scripts/fetch-debug-assets.sh
# 等价：
bash scripts/download-debug-assets.sh
# 分项：
bash scripts/download-debug-live2d.sh
bash scripts/download-debug-asr.sh
bash scripts/download-debug-tts.sh
```

设置页缺失时文案指向上述脚本（**App 内不执行下载**）。

---

## 1. 推荐资源表

### A. Live2D

| 优先级 | 模型 | 来源 | 许可 |
|:------:|------|------|------|
| **1** | **Niziiro Mao** | [CubismWebSamples Mao](https://github.com/Live2D/CubismWebSamples/tree/develop/Samples/Resources/Mao) | [Sample Terms](https://www.live2d.com/en/learn/sample/model-terms) |
| 2 | Haru | 同上仓库 | 同上 |

| Key | Debug 默认相对路径 |
|-----|-------------------|
| `live2d_model_path` | `debug-assets/live2d/Mao/Mao.model3.json` |

### B. ASR（sherpa-onnx）

| 优先级 | 模型包 | 约体积 |
|:------:|--------|--------|
| **1** | `sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23` | ~70MB |
| 2 | `sherpa-onnx-paraformer-zh-small-2024-03-09` | ~74MB |

| Key | Debug 默认 |
|-----|-----------|
| `offline_asr_model_path` | `debug-assets/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23` |
| `offline_asr_enabled` | **默认 false** |

### C. TTS（sherpa-onnx）

| 优先级 | 模型 |
|:------:|------|
| **1** | matcha-icefall-zh-baker |
| 2 | vits-melo-tts-zh_en |

| Key | Debug 默认 |
|-----|-----------|
| `tts_model_dir` | `debug-assets/tts/<解压目录名>` |
| `tts_enabled` | **默认 false** |

### D. 本地脑（路径预留，M2a 设置页展示）

| Key | 说明 |
|-----|------|
| `local_inference_model_path` | 默认选型 **Qwen2.5-1.5B-Instruct**（MNN 量化，用户自备） |
| 放置建议 | `{filesDir}/models/local-llm/light/` |

本阶段**可不下载权重**。详见 [`local-inference.md`](./local-inference.md)。

### E. 妹居（仅文档 fallback）

| 项 | 说明 |
|----|------|
| 允许 | 本机 `妹居*.apk` 架构对照 |
| 禁止 | 上传 so / moc3 / mnn / wav / 商业人设到 GitHub |
| 设备旁路 | `filesDir/meiju-ref/`（优先级低于开源 `debug-assets`） |

---

## 2. 目录树

```
LanXin-Android/
├── debug-assets/                 # gitignore 大文件；保留 README + .gitkeep
│   ├── live2d/Mao/
│   ├── asr/<sherpa-...>/
│   └── tts/<matcha-or-melo>/
├── app/src/main/assets/pet/
│   └── desktop-pet.html          # 占位（无 moc3）
├── scripts/
│   ├── fetch-debug-assets.sh     # M2 推荐入口
│   ├── download-debug-assets.sh
│   ├── download-debug-live2d.sh
│   ├── download-debug-asr.sh
│   └── download-debug-tts.sh
└── docs/debug-assets.md
```

真机：

```bash
adb push debug-assets/ /sdcard/Android/data/<pkg>/files/debug-assets/
# 或 run-as 拷到 filesDir/debug-assets/
```

---

## 3. 设置页就绪语义（M2a）

| 状态 | 条件 | UI |
|------|------|-----|
| **已就绪** | 路径存在（文件或非空目录）；或 `stub://` | 绿色/✓ 短标签 |
| **未就绪** | 空路径 | 引导 `fetch-debug-assets.sh` |
| **路径无效** | 配置了但文件不存在 | 提示检查路径 |

引擎 so 未接入时仍可标「路径已就绪（待引擎）」——不阻塞路径闭环。

---

## 4. Git / Release

| 做法 | 推荐 |
|------|------|
| 主仓 commit 数百 MB 模型 | ❌ |
| 脚本 + gitignore | ✅ |
| GitHub Release 附件镜像 | ✅ 可选 |
| 妹居资源进仓 | ❌ |
| AstrBot 服务器下载当交付 | ❌ |

---

## 5. CI

- 无模型：pet / ASR / TTS **stub 单测**绿
- 检查：`docs/debug-assets.md`、`scripts/fetch-debug-assets.sh`、`download-debug-*.sh`、`debug-assets/README.md`
- **不**在 CI 下载数百 MB 模型

---

## 6. 与里程碑关系

| 阶段 | 资源用法 |
|------|----------|
| **M1** | HTML 占位 + Stub；路径约定 |
| **M2a** | 路径校验 + 设置就绪 + fetch 文案 |
| **M2b** | Live2D 真显示 |
| **M2c** | 能 load 模型文件则引擎 READY |
| **M3/M4** | 真 TTS / 合规 Live2D |

---

## 7. 链接

| 资源 | URL |
|------|-----|
| Live2D Mao | https://www.live2d.com/en/learn/sample/niziiro-mao |
| CubismWebSamples | https://github.com/Live2D/CubismWebSamples |
| Sample 许可 | https://www.live2d.com/en/learn/sample/model-terms |
| sherpa ASR | https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models |
| sherpa TTS | https://k2-fsa.github.io/sherpa/onnx/tts/ |
