#!/usr/bin/env bash
# 下载 Debug 用 sherpa-onnx 中文 ASR 小模型到 debug-assets/asr/
# 优先 HuggingFace 镜像逐文件下载（用户实测可用），回退 GitHub tar.bz2。
#
# 用法:
#   bash scripts/download-debug-asr.sh
#   ASR_VARIANT=paraformer-small bash scripts/download-debug-asr.sh
#   SKIP_DOWNLOAD=1 bash scripts/download-debug-asr.sh   # 仅打印路径约定
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${OUT_DIR:-$ROOT/debug-assets/asr}"
TMP_DIR="${TMP_DIR:-$ROOT/debug-assets/.tmp}"
ASR_VARIANT="${ASR_VARIANT:-zipformer-zh-14M}"

# 用户实测：hf-mirror 可打开 model card 与 Files
HF_REPO="csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
HF_MIRROR_BASE="https://hf-mirror.com/${HF_REPO}/resolve/main"
HF_OFFICIAL_BASE="https://huggingface.co/${HF_REPO}/resolve/main"
RELEASE_BASE="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"

# 运行必需（跳过训练脚本 / test_wavs）
ASR_FILES=(
  "encoder-epoch-99-avg-1.int8.onnx"
  "decoder-epoch-99-avg-1.int8.onnx"
  "joiner-epoch-99-avg-1.int8.onnx"
  "tokens.txt"
)

mkdir -p "$OUT_DIR" "$TMP_DIR"

github_archive_url() {
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

download_file() {
  local url="$1"
  local dest="$2"
  curl -fSL --retry 3 --retry-delay 2 -o "$dest" "$url"
}

download_hf_files() {
  local base="$1"
  local target="$2"
  mkdir -p "$target"
  local f
  for f in "${ASR_FILES[@]}"; do
    echo "  ← $base/$f"
    download_file "$base/$f" "$target/$f"
  done
}

is_ready() {
  local d="$1"
  [[ -d "$d" ]] || return 1
  [[ -f "$d/tokens.txt" ]] || return 1
  # 至少一个 onnx
  find "$d" -maxdepth 1 -type f \( -name '*.onnx' \) | head -1 | grep -q .
}

URL="$(github_archive_url)"
ARCHIVE_NAME="$(basename "$URL")"
DIR_NAME="${ARCHIVE_NAME%.tar.bz2}"
TARGET="$OUT_DIR/$DIR_NAME"

echo "==> Debug ASR"
echo "    variant : $ASR_VARIANT"
echo "    target  : $TARGET"
echo "    strategy: hf-mirror → huggingface → github tar.bz2"

if [[ "${SKIP_DOWNLOAD:-0}" == "1" ]]; then
  echo "SKIP_DOWNLOAD=1，跳过实际下载。"
else
  if is_ready "$TARGET"; then
    echo "已存在关键文件，跳过下载: $TARGET"
  else
    ok=0
    if [[ "$ASR_VARIANT" == "zipformer-zh-14M" || "$ASR_VARIANT" == "default" || "$ASR_VARIANT" == "light" ]]; then
      echo "尝试 hf-mirror 逐文件…"
      if download_hf_files "$HF_MIRROR_BASE" "$TARGET"; then
        if is_ready "$TARGET"; then
          ok=1
          echo "hf-mirror 完成"
        fi
      else
        rm -rf "$TARGET"
        echo "hf-mirror 失败，尝试 huggingface.co…"
        if download_hf_files "$HF_OFFICIAL_BASE" "$TARGET"; then
          if is_ready "$TARGET"; then
            ok=1
            echo "huggingface.co 完成"
          fi
        else
          rm -rf "$TARGET"
        fi
      fi
    fi

    if [[ "$ok" != "1" ]]; then
      echo "回退 GitHub 归档: $URL"
      ARCHIVE="$TMP_DIR/$ARCHIVE_NAME"
      download_file "$URL" "$ARCHIVE"
      echo "解压到 $OUT_DIR …"
      tar -xjf "$ARCHIVE" -C "$OUT_DIR"
      rm -f "$ARCHIVE"
      if [[ ! -d "$TARGET" ]]; then
        echo "提示: 解压目录名可能与包名不同，请 ls $OUT_DIR"
      fi
    fi
  fi
fi

RESOLVED="$TARGET"
if [[ ! -d "$RESOLVED" ]]; then
  RESOLVED="$(find "$OUT_DIR" -mindepth 1 -maxdepth 1 -type d ! -name '.*' | head -1 || true)"
fi

echo
echo "---- 设置页应填路径 ----"
echo "offline_asr_model_path = ${RESOLVED:-$TARGET}"
echo "offline_asr_enabled    = true   # 产品默认仍为 false；仅 debug 自测时打开"
echo "offline_asr_language   = zh"
echo "offline_asr_sample_rate_hz = 16000"
echo
echo "App 内落盘等价路径: LanXin/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
echo
