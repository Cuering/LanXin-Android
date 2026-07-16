# Debug 资源方案：免费可爱 Live2D + 开源中文语音

> 分支：`feat/phase6-pet-voice-session`（追加 debug assets 封装）  
> 状态：**文档 + 下载脚本 + 目录约定**（**不**把数百 MB 模型 commit 进主仓）  
> 相关：[`meiju-style-pet.md`](./meiju-style-pet.md) · [`voice-asr.md`](./voice-asr.md) · `builtin/pet`

## 0. 原则

| 原则 | 说明 |
|------|------|
| 默认走开源 | Live2D 官方 Sample + sherpa-onnx ASR/TTS |
| 大文件不进 git | 用 `scripts/download-debug-*.sh` 拉到 `debug-assets/`（已 gitignore） |
| CI 无模型仍绿 | stub / 占位 HTML；可选 job 只检查脚本与文档存在 |
| 妹居仅 fallback | **禁止**上传妹居 so / moc3 / mnn / wav / 商业人设到 GitHub |
| 分发合规 | App 发布时用户需自备合规 Live2D；Debug 可脚本拉官方 sample |

一键：

```bash
bash scripts/download-debug-assets.sh
# 或分项：
bash scripts/download-debug-live2d.sh
bash scripts/download-debug-asr.sh
bash scripts/download-debug-tts.sh
```

---

## 1. 推荐资源表（最终版）

### A. Live2D（可爱 / 官方样例）

| 优先级 | 模型 | 来源 | 体积（约） | 许可 | 用途 |
|:------:|------|------|-----------|------|------|
| **1 推荐** | **Niziiro Mao（虹色まお）** | [Live2D Sample · Mao](https://www.live2d.com/en/learn/sample/niziiro-mao) · 仓库 [CubismWebSamples `Samples/Resources/Mao`](https://github.com/Live2D/CubismWebSamples/tree/develop/Samples/Resources/Mao) | ~5–15MB | [Sample Data Terms / Free Material License](https://www.live2d.com/en/learn/sample/model-terms) | 可爱向默认 debug |
| **2** | **Haru** | [Sample · Haru](https://www.live2d.com/en/learn/sample/haru) · `CubismWebSamples/.../Haru` | ~10–20MB | 同上 | 经典样例 |
| 3 | Cubism Web Samples 其它（Hiyori / Rice 等） | 同上仓库 | 视模型 | 同上 | 换皮测试 |

**许可要点（务必读原文）：**

- 样例用于 **学习与 SDK 集成测试**
- **分发正式 App 时**用户需自备合规模型；不得把官方 sample 当商业人设默认打包售卖
- 文档必须保留条款链接：https://www.live2d.com/en/learn/sample/model-terms

**仓库内只放：**

- 极简占位：`app/src/main/assets/pet/desktop-pet.html`（M1 已有，无 moc3）
- 下载脚本 + 本目录约定；**不** commit `*.moc3` / 纹理大图

**设置键（约定，M4 接入时沿用）：**

| Key | 说明 | Debug 默认相对路径 |
|-----|------|-------------------|
| `live2d_model_path` | 指向 `*.model3.json` | `debug-assets/live2d/Mao/Mao.model3.json` |

### B. ASR（中文，sherpa-onnx，手机友好优先）

索引：https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html  
Release：https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models

| 优先级 | 模型包 | 体积（约） | 说明 |
|:------:|--------|-----------|------|
| **1 推荐 debug** | `sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23` | **~70MB** | 流式中文小模型，手机友好 |
| **2** | `sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16` | ~436MB | 中英 bilingual（体积大，按需） |
| **3 备选** | `sherpa-onnx-paraformer-zh-small-2024-03-09` | **~74MB** | 离线 paraformer 小中文 |

下载示例（脚本内固化）：

```
https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23.tar.bz2
https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2
```

| Key | 说明 | Debug 默认 |
|-----|------|-----------|
| `offline_asr_model_path` | 模型目录或入口文件 | `debug-assets/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23` |
| `offline_asr_enabled` | 总开关 | **默认 false**（产品规则不变） |

### C. TTS（中文女声向，sherpa-onnx）

索引：https://k2-fsa.github.io/sherpa/onnx/tts/  
Release：https://github.com/k2-fsa/sherpa-onnx/releases （tag `tts-models` 等）

| 优先级 | 模型 | 说明 |
|:------:|------|------|
| **1 推荐** | **matcha-icefall-zh-baker** | 中文女声 Baker，清晰；与 sherpa 同栈 |
| **2** | **vits-melo-tts-zh_en** | 中英 1 speaker，体积通常更友好 |
| **3 备选** | `csukuangfj/sherpa-onnx-vits-zh-ll` 等多音色 VITS | 多 speaker 调试 |

> 脚本默认尝试官方 release 上的 **matcha / melo / baker** 命名包；若 URL 变更，以 [sherpa TTS 文档](https://k2-fsa.github.io/sherpa/onnx/tts/) 与 release 页为准，改脚本顶部变量即可。

| Key | 说明 | Debug 默认 |
|-----|------|-----------|
| `tts_model_dir` | TTS 模型目录 | `debug-assets/tts/<解压目录名>` |
| `tts_enabled`（M1 `TtsPreferences`） | 总开关 | **默认 false** |

### D. 妹居（仅文档 fallback）

| 项 | 说明 |
|----|------|
| 允许 | 本机若有 `妹居*.apk`，可作**架构/路径对照**（见 `meiju-style-pet.md`） |
| 禁止 | 上传 so / moc3 / mnn / wav / 商业人设 / 解包目录到 GitHub |
| 默认路径 | **全部走开源**；设置页与脚本不默认指向妹居 |

本机参考路径（示例，不入库）：

```
/AstrBot/data/workspaces/妹居2.2.2版本.apk
```

---

## 2. 目录树（约定）

```
LanXin-Android/
├── debug-assets/                    # gitignore 大文件；保留 README + .gitkeep
│   ├── README.md
│   ├── live2d/
│   │   ├── Mao/                     # Mao.model3.json + textures…
│   │   └── Haru/
│   ├── asr/
│   │   └── sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/
│   └── tts/
│       └── <matcha-or-melo-dir>/
├── app/src/main/assets/pet/
│   └── desktop-pet.html             # M1 占位（无 moc3）
├── app/src/debug/assets/vendor/     # 可选 debug 变体占位（.gitkeep）
├── scripts/
│   ├── download-debug-assets.sh
│   ├── download-debug-live2d.sh
│   ├── download-debug-asr.sh
│   └── download-debug-tts.sh
└── docs/debug-assets.md             # 本文
```

真机推送示例：

```bash
adb push debug-assets/asr/... /sdcard/Android/data/<pkg>/files/models/offline-asr/
# 设置页填写 filesDir 绝对路径，或 adb shell run-as 拷到 filesDir
```

---

## 3. 设置页应填路径（脚本结束会打印）

| 能力 | 配置键 | 示例值 |
|------|--------|--------|
| Live2D | `live2d_model_path` | `<repo>/debug-assets/live2d/Mao/Mao.model3.json` |
| ASR | `offline_asr_model_path` | `<repo>/debug-assets/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23` |
| TTS | `tts_model_dir` | `<repo>/debug-assets/tts/<dir>` |

缺失时：App 保持 stub / 占位 HTML，并提示「请运行 `scripts/download-debug-assets.sh`」。

---

## 4. Git / Release 策略

| 做法 | 是否推荐 |
|------|----------|
| 主仓 commit 模型几百 MB | ❌ 禁止 |
| `debug-assets/**` + `*.onnx` gitignore，脚本下载 | ✅ 默认 |
| GitHub **Release 附件** 或独立 `LanXin-debug-assets` 仓 | ✅ 若需「一键镜像」 |
| git-lfs | 仅当体积可控且团队同意时可选 |
| 妹居资源进仓 | ❌ 绝对禁止 |

---

## 5. CI

- 无模型：既有 pet / ASR / TTS **stub 单测**仍绿
- 可选：workflow 检查下列文件存在（见 `phase6-debug-assets-verify.yml`）
  - `docs/debug-assets.md`
  - `scripts/download-debug-assets.sh`
  - `scripts/download-debug-live2d.sh`
  - `scripts/download-debug-asr.sh`
  - `scripts/download-debug-tts.sh`
  - `debug-assets/README.md`

---

## 6. 与 M1–M4 关系

| 阶段 | 资源用法 |
|------|----------|
| **M1** | HTML 占位 + Stub ASR/TTS；本封装提供**可替换路径约定** |
| **M2** | `offline_asr_model_path` → 真 Sherpa |
| **M3** | `tts_model_dir` → 真 sherpa TTS（优先开源女声） |
| **M4** | `live2d_model_path` → 官方 sample / 自有合规模型 |

---

## 7. 快速对照链接

| 资源 | 链接 |
|------|------|
| Live2D Mao | https://www.live2d.com/en/learn/sample/niziiro-mao |
| Live2D Haru | https://www.live2d.com/en/learn/sample/haru |
| CubismWebSamples | https://github.com/Live2D/CubismWebSamples |
| Sample 许可 | https://www.live2d.com/en/learn/sample/model-terms |
| sherpa ASR models | https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models |
| sherpa 预训练索引 | https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html |
| sherpa TTS | https://k2-fsa.github.io/sherpa/onnx/tts/ |
