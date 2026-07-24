#!/usr/bin/env bash
# CI 构建时预下载 ASR/TTS 模型到 app/src/main/assets/voice/，使之随 APK 打包。
#
# 用法:
#   bash scripts/ci-bundle-voice-assets.sh
#
# 环境变量:
#   SKIP_DOWNLOAD=1  仅打印路径，不实际下载（用于 dry-run 或本机构建）
#
# 产物:
#   app/src/main/assets/voice/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/
#     ├── encoder-epoch-99-avg-1.int8.onnx
#     ├── decoder-epoch-99-avg-1.int8.onnx
#     ├── joiner-epoch-99-avg-1.int8.onnx
#     └── tokens.txt
#
# TTS 体积过大（~31MB），不打包进 APK；用户从设置页一键下载或走系统 TTS 回退。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS_DIR="$ROOT/app/src/main/assets/voice"
TMP_DIR="$ROOT/debug-assets/.tmp"

ASR_VARIANT="${ASR_VARIANT:-zipformer-zh-14M}"
HF_REPO="csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
HF_MIRROR_BASE="https://hf-mirror.com/${HF_REPO}/resolve/main"
HF_OFFICIAL_BASE="https://huggingface.co/${HF_REPO}/resolve/main"
RELEASE_BASE="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"

ASR_FILES=(
  "encoder-epoch-99-avg-1.int8.onnx"
  "decoder-epoch-99-avg-1.int8.onnx"
  "joiner-epoch-99-avg-1.int8.onnx"
  "tokens.txt"
)

ASR_TARGET="$ASSETS_DIR/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"

echo "==> CI Bundle Voice Assets"
echo "    asr target: $ASR_TARGET"

if [[ "${SKIP_DOWNLOAD:-0}" == "1" ]]; then
  echo "SKIP_DOWNLOAD=1，跳过下载。若本机构建，请手动放模型到:"
  echo "  $ASR_TARGET/"
  exit 0
fi

mkdir -p "$ASR_TARGET" "$TMP_DIR"

# 检查是否已有
if [[ -f "$ASR_TARGET/tokens.txt" ]] && \
   find "$ASR_TARGET" -maxdepth 1 -name '*.onnx' | head -1 | grep -q .; then
  echo "ASR 模型已存在，跳过下载: $ASR_TARGET"
  ls -lh "$ASR_TARGET/"*.onnx "$ASR_TARGET/tokens.txt"
  exit 0
fi

echo "下载 ASR 模型到 $ASR_TARGET …"

# 策略：hf-mirror → huggingface → github tar.bz2
download_file() {
  local url="$1" dest="$2"
  curl -fSL --retry 3 --retry-delay 2 -o "$dest" "$url"
}

ok=0

# 尝试 HF 逐文件
echo "尝试 hf-mirror 逐文件…"
hf_ok=0
for f in "${ASR_FILES[@]}"; do
  echo "  ← $HF_MIRROR_BASE/$f"
  download_file "$HF_MIRROR_BASE/$f" "$ASR_TARGET/$f" || { hf_ok=1; break; }
done
if [[ "$hf_ok" == "0" ]] && [[ -f "$ASR_TARGET/tokens.txt" ]]; then
  ok=1
  echo "hf-mirror 完成"
fi

if [[ "$ok" != "1" ]]; then
  echo "尝试 huggingface.co 逐文件…"
  rm -rf "$ASR_TARGET"/*.onnx "$ASR_TARGET/tokens.txt" 2>/dev/null || true
  hf_ok=0
  for f in "${ASR_FILES[@]}"; do
    echo "  ← $HF_OFFICIAL_BASE/$f"
    download_file "$HF_OFFICIAL_BASE/$f" "$ASR_TARGET/$f" || { hf_ok=1; break; }
  done
  if [[ "$hf_ok" == "0" ]] && [[ -f "$ASR_TARGET/tokens.txt" ]]; then
    ok=1
    echo "huggingface.co 完成"
  fi
fi

if [[ "$ok" != "1" ]]; then
  echo "回退 GitHub 归档…"
  rm -rf "$ASR_TARGET"/*.onnx "$ASR_TARGET/tokens.txt" 2>/dev/null || true
  URL="${RELEASE_BASE}/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23.tar.bz2"
  ARCHIVE="$TMP_DIR/asr.tar.bz2"
  echo "  ← $URL"
  download_file "$URL" "$ARCHIVE"
  echo "  解压…"
  tar -xjf "$ARCHIVE" -C "$(dirname "$ASR_TARGET")"
  rm -f "$ARCHIVE"
  if [[ -f "$ASR_TARGET/tokens.txt" ]]; then
    ok=1
  fi
fi

if [[ "$ok" != "1" ]]; then
  echo >&2 "❌ ASR 模型下载失败。请手动放入: $ASR_TARGET"
  exit 1
fi

echo "✅ ASR 模型已打包:"
ls -lh "$ASR_TARGET/"*.onnx "$ASR_TARGET/tokens.txt"
echo ""
echo "TTS 模型体积过大，未打包。用户首次运行需从设置页一键下载。"
