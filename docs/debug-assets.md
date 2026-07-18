# Debug 资源方案：免费可爱 Live2D + 开源中文语音

> 状态：**M1** · **M2a 路径闭环** · **M2b 壳** · **M2b 打磨** · **App 内一键下载 + 可达 CDN**  
> 相关：[`meiju-style-pet.md`](./meiju-style-pet.md) · [`voice-asr.md`](./voice-asr.md) · [`local-inference.md`](./local-inference.md) · [`live2d-mao-sample.md`](./live2d-mao-sample.md)

## 0. 原则

| 原则 | 说明 |
|------|------|
| 默认走开源 | **Live2D 仓内官方 Sample Mao** + sherpa-onnx ASR/TTS |
| Live2D 进仓 | `app/src/main/assets/pet/live2d/Mao/`（~4MB，白名单）见 [`live2d-mao-sample.md`](./live2d-mao-sample.md) |
| **主路径：App 内下载** | 设置页「一键下载」→ 用户可访问的 `LanXin/`（公共存储优先；失败回退 `Android/data/.../files/LanXin/`） |
| 下载源 | **jsDelivr / HF / hf-mirror 优先**；官方 GitHub raw/releases 仅回退（旧 ghproxy 已弃用） |
| ASR/TTS 大文件不进 git | 仅本机 `LanXin/`；**禁止** commit 权重 |
| **禁止** | AstrBot 服务器缓存模型当交付；妹居商业资源入库 |
| 脚本可选 | `fetch-debug-assets.sh` + adb push 为开发者备用 |
| CI 无大包仍绿 | mock transport 单测；不 curl 真实数百 MB |
| 分发合规 | Live2D Sample Terms；ASR/TTS Apache-2.0 |

### App 内一键下载（推荐）

桌宠设置页（`DesktopPetScreen`）：

1. 选择源：**CDN（推荐）** 或 **官方源**
2. 分项按钮：Live2D（可选覆盖）/ ASR / TTS / 本地脑（MNN 1.5B）
3. 进度条、取消、失败短文案（含真实尝试过的源）
4. 成功写回：`live2d_model_path` / `offline_asr_model_path` / `tts_model_dir` / `local_inference_model_path`，并展示绝对路径 + 成功源
5. Live2D 仓内 Mao 仍为默认兜底；下载可覆盖到 `LanXin/live2d/Mao/`
6. 本地脑：一键下载到 `LanXin/models/local-llm/light/`（~880MB，请 Wi‑Fi）；也可自备路径导入

### 下载源优先级

| 资源 | 优先 | 备选 | 回退 |
|------|------|------|------|
| Live2D Mao | `cdn.jsdelivr.net/gh/Live2D/CubismWebSamples@develop/...` | `fastly.jsdelivr.net/...` | `raw.githubusercontent.com/...` |
| ASR zipformer-14M | `https://hf-mirror.com/csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23` 目录文件 | `https://huggingface.co/csukuangfj/...` 同仓 | GitHub release `*.tar.bz2`（最后回退） |
| TTS matcha-baker | `https://hf-mirror.com/csukuangfj/matcha-icefall-zh-baker` 目录文件 | `https://huggingface.co/csukuangfj/matcha-icefall-zh-baker` | GitHub release `matcha-icefall-zh-baker.tar.bz2`（最后回退） |
| 本地脑 Qwen2.5-1.5B MNN | ModelScope `MNN/Qwen2.5-1.5B-Instruct-MNN` | `hf-mirror.com/taobao-mnn/...` | HuggingFace `taobao-mnn/Qwen2.5-1.5B-Instruct-MNN` |

> 说明：HF 模型仓多为目录而非单 tar；App / 脚本按必要文件列表用 `…/resolve/main/<file>` 直链下载到 `LanXin/asr|tts/<model-dir>/`。旧 ghproxy 与「仅 GitHub tar.bz2」主路径已弃用。Live2D 默认 APK 内置，在线源非硬依赖。

### 落盘路径（用户可访问）

| 优先级 | 路径 | 说明 |
|:------:|------|------|
| **1** | `{外部存储}/LanXin/` | 如 `/storage/emulated/0/LanXin/`；文件管理器易见 |
| 2 | `Android/data/com.lanxin.android/files/LanXin/` | 公共目录不可写时回退（`getExternalFilesDir`） |
| 子目录 | `live2d/Mao/` · `asr/…` · `tts/…` · `models/local-llm/light/` | 相对 `LanXin/` |
| 兼容 | 历史 `filesDir/debug-assets/` | 仍可被路径解析识别 |

成功下载后 UI 展示「已保存到 <绝对路径>」。


实现要点：

| 类型 | 类 |
|------|-----|
| Catalog / 镜像 | `DebugAssetCatalog` · `DebugAssetMirror` |
| 下载 | `DebugAssetDownloader` + `AssetDownloadTransport` |
| 落盘根 | `DebugAssetStorage`（公共 `LanXin/` → externalFiles 回退） |
| 传输 | `KtorAssetDownloadTransport` |
| 解压 | `ArchiveExtractor`（zip / tar.bz2 / tar.gz，防 zip-slip） |
| DI | `PetModule` binds transport |

### Live2D（优先仓内）

开箱使用仓内 Mao，**无需**下载。可选 App 内「更新」或脚本覆盖：

```bash
bash scripts/download-debug-live2d.sh   # 可选：覆盖到 debug-assets
bash scripts/vendor-live2d-mao.sh       # 从上游重同步仓内 assets
```

### ASR / TTS（App 内优先；脚本备用）

```bash
# 开发者机备用
bash scripts/fetch-debug-assets.sh
# 等价：
bash scripts/download-debug-assets.sh
bash scripts/download-debug-asr.sh
bash scripts/download-debug-tts.sh
```

---

## 1. 推荐资源表

### A. Live2D

| 优先级 | 模型 | 来源 | 许可 |
|:------:|------|------|------|
| **1** | **Niziiro Mao** | 仓内 assets / [CubismWebSamples Mao](https://github.com/Live2D/CubismWebSamples/tree/develop/Samples/Resources/Mao) | [Sample Terms](https://www.live2d.com/en/learn/sample/model-terms) |
| 2 | Haru | 同上仓库 | 同上 |

| Key | 默认 |
|-----|------|
| `live2d_model_path` | **可空** → 仓内 Sample（`BuiltInLive2dAssets`）；可选覆盖 `LanXin/live2d/Mao/...` |

### B. ASR（sherpa-onnx）

| 优先级 | 模型包 | 约体积 |
|:------:|--------|--------|
| **1** | `sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23` | ~70MB |
| 2 | `sherpa-onnx-paraformer-zh-small-2024-03-09` | ~74MB |

| Key | Debug 默认 |
|-----|-----------|
| `offline_asr_model_path` | `LanXin/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23` |
| `offline_asr_enabled` | **默认 false** |

### C. TTS（sherpa-onnx）

| 优先级 | 模型 |
|:------:|------|
| **1** | matcha-icefall-zh-baker |
| 2 | vits-melo-tts-zh_en |

| Key | Debug 默认 |
|-----|-----------|
| `tts_model_dir` | `LanXin/tts/<解压目录名>` |
| `tts_enabled` | **默认 false** |

### D. 本地脑（App 内一键下载 + 路径导入）

| Key | 说明 |
|-----|------|
| `local_inference_model_path` | 默认选型 **Qwen2.5-1.5B-Instruct**（MNN 量化） |
| 一键下载落盘 | `LanXin/models/local-llm/light/`（含 `llm.mnn` / `llm.mnn.weight` / tokenizer 等） |
| 源序 | ModelScope → hf-mirror → HuggingFace |

详见 [`local-inference.md`](./local-inference.md)。

### E. 妹居（仅文档 fallback）

| 项 | 说明 |
|----|------|
| 允许 | 本机 `妹居*.apk` 架构对照 |
| 禁止 | 上传 so / moc3 / mnn / wav / 商业人设到 GitHub |
| 设备旁路 | `filesDir/meiju-ref/`（优先级低于开源 `LanXin`） |

---

## 2. 目录树

```
LanXin-Android/
├── debug-assets/                 # 开发机脚本目录（gitignore 大文件）；真机 App 内改为 LanXin/
│   ├── live2d/Mao/
│   ├── asr/<sherpa-...>/
│   └── tts/<matcha-or-melo>/
# 真机：
#   /storage/emulated/0/LanXin/{live2d,asr,tts}/
#   或 Android/data/<pkg>/files/LanXin/...
├── app/src/main/assets/pet/
│   ├── desktop-pet.html
│   └── live2d/Mao/               # 官方 Sample（可 commit）
├── scripts/
│   ├── vendor-live2d-mao.sh
│   ├── fetch-debug-assets.sh     # 可选备用
│   ├── download-debug-assets.sh
│   ├── download-debug-live2d.sh
│   ├── download-debug-asr.sh
│   └── download-debug-tts.sh
└── docs/debug-assets.md
```

真机（脚本备用）：

```bash
adb push debug-assets/ /sdcard/Android/data/<pkg>/files/debug-assets/
# 或 run-as 拷到 filesDir/debug-assets/
```

App 内下载优先写入公共 `…/LanXin/`（文件管理器可见）；若系统限制写失败则回退 `getExternalFilesDir()/LanXin/`，并在设置页展示真实路径。

---

## 3. 设置页就绪语义

| 状态 | 条件 | UI |
|------|------|-----|
| **已就绪** | 路径存在（文件或非空目录）；或 `stub://` | 绿色/✓ 短标签 |
| **已就绪（内置示例）** | Live2D 仓内 Sample | 绿色/✓ |
| **未就绪** | ASR/TTS 空路径等 | **一键下载** + 可选脚本说明 |
| **路径无效** | 配置了但文件不存在 | 提示检查路径 / 重新下载 |

---

## 4. Git / Release

| 做法 | 推荐 |
|------|------|
| 主仓 commit 数百 MB 模型 | ❌ |
| 官方 Sample Mao 进 assets（白名单） | ✅ |
| App 内下载到 LanXin/ | ✅ |
| 脚本 + gitignore（ASR/TTS） | ✅ 备用 |
| 妹居资源进仓 | ❌ |
| ASR/TTS/本地脑权重进仓 | ❌ |
| AstrBot 服务器下载当交付 | ❌ |

---

## 5. CI

- pet / ASR / TTS **stub 单测**绿（含 `DebugAssetDownloaderTest` / `DebugAssetCatalogTest` / `ArchiveExtractorTest`）
- **不**在 CI 下载 ASR/TTS 大包；transport mock
- 检查：仓内 Mao、`docs/debug-assets.md`、`fetch-debug-assets.sh`（仍保留）

---

## 6. 与里程碑关系

| 阶段 | 资源用法 |
|------|----------|
| **M1** | HTML 占位 + Stub；路径约定 |
| **M2a** | 路径校验 + 设置就绪 + fetch 文案 |
| **M2b** | Live2D 真显示（WebView 壳 + 降级）|
| **M2b 打磨** | 会话表情/口型 + 设置引导 |
| **App 内下载** | 一键 Live2D/ASR/TTS + 国内镜像回退 |
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
