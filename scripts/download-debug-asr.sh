#!/usr/bin/env bash
# 下载 Debug 用 sherpa-onnx 中文 ASR 小模型到 debug-assets/asr/
# 用法:
#   bash scripts/download-debug-asr.sh
#   ASR_VARIANT=paraformer-small bash scripts/download-debug-asr.sh
#   SKIP_DOWNLOAD=1 bash scripts/download-debug-asr.sh   # 仅打印路径约定
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${OUT_DIR:-$ROOT/debug-assets/asr}"
TMP_DIR="${TMP_DIR:-$ROOT/debug-assets/.tmp}"
ASR_VARIANT="${ASR_VARIANT:-zipformer-zh-14M}"
RELEASE_BASE="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"

mkdir -p "$OUT_DIR" "$TMP_DIR"

pick_url() {
  case "$ASR_VARIANT" in
    zipformer-zh-14M|default|light)
      echo "${RELEASE_BASE}/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23.tar.bz2"
      ;;
    bilingual-small)
      echo "${RELEASE_BASE}/sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16.tar.bz2"
      ;;
    paraformer-small)
      echo "${RELEASE_BASE}/sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2"
      ;;
    *)
      echo "未知 ASR_VARIANT=$ASR_VARIANT" >&2
      echo "可选: zipformer-zh-14M | bilingual-small | paraformer-small" >&2
      exit 2
      ;;
  esac
}

URL="$(pick_url)"
ARCHIVE_NAME="$(basename "$URL")"
# strip .tar.bz2
DIR_NAME="${ARCHIVE_NAME%.tar.bz2}"
TARGET="$OUT_DIR/$DIR_NAME"

echo "==> Debug ASR"
echo "    variant : $ASR_VARIANT"
echo "    url     : $URL"
echo "    target  : $TARGET"

if [[ "${SKIP_DOWNLOAD:-0}" == "1" ]]; then
  echo "SKIP_DOWNLOAD=1，跳过实际下载。"
else
  if [[ -d "$TARGET" ]] && find "$TARGET" -type f | head -1 | grep -q .; then
    echo "已存在内容，跳过下载: $TARGET"
  else
    ARCHIVE="$TMP_DIR/$ARCHIVE_NAME"
    echo "下载中…"
    curl -fSL --retry 3 --retry-delay 2 -o "$ARCHIVE" "$URL"
    echo "解压到 $OUT_DIR …"
    tar -xjf "$ARCHIVE" -C "$OUT_DIR"
    # 部分包解压后目录名与 archive 一致；若多一层则保持现状
    if [[ ! -d "$TARGET" ]]; then
      # 尝试定位刚解压的唯一目录
      echo "提示: 解压目录名可能与包名不同，请 ls $OUT_DIR"
    fi
    rm -f "$ARCHIVE"
  fi
fi

# 解析实际目录
RESOLVED="$TARGET"
if [[ ! -d "$RESOLVED" ]]; then
  # 取 OUT_DIR 下最新目录
  RESOLVED="$(find "$OUT_DIR" -mindepth 1 -maxdepth 1 -type d ! -name '.*' | head -1 || true)"
fi

echo
echo "---- 设置页应填路径 ----"
echo "offline_asr_model_path = ${RESOLVED:-$TARGET}"
echo "offline_asr_enabled    = true   # 产品默认仍为 false；仅 debug 自测时打开"
echo "offline_asr_language   = zh"
echo "offline_asr_sample_rate_hz = 16000"
echo
echo "文档: docs/debug-assets.md"
echo "索引: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html"
echo "✅ ASR debug 资源步骤完成"
