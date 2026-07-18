# app/libs

预编译 native / 第三方 AAR 的**本地下载目录**（默认 gitignore）。

## sherpa-onnx（P0 ASR + P1 TTS）

构建时由 `app/build.gradle.kts` 的 `downloadSherpaOnnxAar` 任务自动拉取：

| 属性 | 值 |
|------|-----|
| 文件 | `sherpa-onnx-static-link-onnxruntime-1.13.4.aar` |
| 来源 | [k2-fsa/sherpa-onnx v1.13.4](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.4) |
| 变体 | **static-link-onnxruntime**（单 so 为主，避免与 ORT Mobile 双份冲突） |
| 默认 URL | `https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4/sherpa-onnx-static-link-onnxruntime-1.13.4.aar` |
| 能力 | Offline ASR + **OfflineTts**（同一 `libsherpa-onnx-jni.so`） |

覆盖方式：

```bash
# 本地已有 AAR
export SHERPA_ONNX_AAR=/path/to/sherpa-onnx-static-link-onnxruntime-1.13.4.aar

# 或自定义下载 URL（国内镜像等）
export SHERPA_ONNX_AAR_URL=https://ghfast.top/https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4/sherpa-onnx-static-link-onnxruntime-1.13.4.aar
```

**不要**把 AAR / `.so` commit 进 git。

模型权重外置：

- ASR → `LanXin/asr/...`
- TTS → `LanXin/tts/...`（Matcha 另需 `vocos-22khz-univ.onnx` 等 vocoder，可放模型目录或上一级）

许可证：Apache-2.0（见 `third_party/sherpa-onnx/NOTICE`）。

## MNN（P2 本地 LLM）

构建时由 `app/build.gradle.kts` 的 `downloadMnnNative` 任务自动拉取官方 Android 预编译 zip，并只解压 **arm64-v8a** 的 `.so` 到 `app/src/main/jniLibs/arm64-v8a/`：

| 属性 | 值 |
|------|-----|
| 版本 | **3.6.0** |
| 文件 | `mnn_3.6.0_android_armv7_armv8_cpu_opencl_vulkan.zip`（暂存 `app/libs/`，gitignore） |
| 来源 | [alibaba/MNN v3.6.0](https://github.com/alibaba/MNN/releases/tag/3.6.0) |
| 必需 so | `libMNN.so` · `libMNN_Express.so` · `libllm.so` · `libc++_shared.so` |
| 可选 so | OpenCL / Vulkan / OpenCV / Audio（缺则跳过，CPU 路径仍可用） |
| 我方 JNI | CMake 产出 `libmnn_lanxin.so`（`app/src/main/cpp/`） |

覆盖方式：

```bash
# 本地已有 zip
export MNN_NATIVE_ZIP=/path/to/mnn_3.6.0_android_armv7_armv8_cpu_opencl_vulkan.zip

# 或自定义下载 URL（国内镜像等）
export MNN_NATIVE_URL=https://ghfast.top/https://github.com/alibaba/MNN/releases/download/3.6.0/mnn_3.6.0_android_armv7_armv8_cpu_opencl_vulkan.zip
```

**不要**把 zip / `.so` commit 进 git。

模型权重外置：`LanXin/models/local-llm/light/`（`config.json` + `*.mnn` + tokenizer，见 `docs/local-inference.md`）。

许可证：Apache-2.0（见 `third_party/mnn/NOTICE`）。
