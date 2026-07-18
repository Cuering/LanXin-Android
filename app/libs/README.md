# app/libs

预编译 native / 第三方 AAR 的**本地下载目录**（默认 gitignore）。

## sherpa-onnx（P0 离线 ASR）

构建时由 `app/build.gradle.kts` 的 `downloadSherpaOnnxAar` 任务自动拉取：

| 属性 | 值 |
|------|-----|
| 文件 | `sherpa-onnx-static-link-onnxruntime-1.13.4.aar` |
| 来源 | [k2-fsa/sherpa-onnx v1.13.4](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.4) |
| 变体 | **static-link-onnxruntime**（单 so 为主，避免与 ORT Mobile 双份冲突） |
| 默认 URL | `https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4/sherpa-onnx-static-link-onnxruntime-1.13.4.aar` |

覆盖方式：

```bash
# 本地已有 AAR
export SHERPA_ONNX_AAR=/path/to/sherpa-onnx-static-link-onnxruntime-1.13.4.aar

# 或自定义下载 URL（国内镜像等）
export SHERPA_ONNX_AAR_URL=https://ghfast.top/https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4/sherpa-onnx-static-link-onnxruntime-1.13.4.aar
```

**不要**把 AAR / `.so` commit 进 git。模型权重仍走 `LanXin/asr/...` 外置下载。

许可证：Apache-2.0（见 `third_party/sherpa-onnx/NOTICE`）。
