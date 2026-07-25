#!/usr/bin/env bash
# CI 构建时预下载 ASR/TTS 模型到 app/src/main/assets/voice/，使之随 APK 打包。
#
# 用法:
#   bash scripts/ci-bundle-voice-assets.sh
#   BUNDLE_TTS=1 bash scripts/ci-bundle-voice-assets.sh
#
# 环境变量:
#   SKIP_DOWNLOAD=1  仅打印路径，不实际下载
#   BUNDLE_TTS=1     打包 TTS（默认 1）
#   TTS_VARIANT      melo（默认）| matcha-baker
#
# 产物:
#   app/src/main/assets/voice/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/
#   app/src/main/assets/voice/tts/vits-melo-tts-zh_en/   # 默认 VITS
#
# 权重不进 git（*.onnx gitignore）；仅构建期写入 assets。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS_DIR="$ROOT/app/src/main/assets/voice"
TMP_DIR="$ROOT/debug-assets/.tmp"
BUNDLE_TTS="${BUNDLE_TTS:-1}"
TTS_VARIANT="${TTS_VARIANT:-melo}"

HF_REPO_ASR="csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
HF_MIRROR_ASR="https://hf-mirror.com/${HF_REPO_ASR}/resolve/main"
HF_OFFICIAL_ASR="https://huggingface.co/${HF_REPO_ASR}/resolve/main"
RELEASE_ASR="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"

ASR_FILES=(
  "encoder-epoch-99-avg-1.int8.onnx"
  "decoder-epoch-99-avg-1.int8.onnx"
  "joiner-epoch-99-avg-1.int8.onnx"
  "tokens.txt"
)
ASR_TARGET="$ASSETS_DIR/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"

RELEASE_TTS="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

case "$TTS_VARIANT" in
  melo|vits-melo|melo-zh-en|vits|default)
    TTS_DIR_NAME="vits-melo-tts-zh_en"
    TTS_TAR_CANDIDATES=(
      "${RELEASE_TTS}/vits-melo-tts-zh_en.tar.bz2"
      "${RELEASE_TTS}/sherpa-onnx-vits-melo-tts-zh_en.tar.bz2"
    )
    HF_TTS_REPO="csukuangfj/vits-melo-tts-zh_en"
    ;;
  matcha-baker|matcha|baker)
    TTS_DIR_NAME="matcha-icefall-zh-baker"
    TTS_TAR_CANDIDATES=(
      "${RELEASE_TTS}/matcha-icefall-zh-baker.tar.bz2"
      "${RELEASE_TTS}/sherpa-onnx-matcha-icefall-zh-baker.tar.bz2"
    )
    HF_TTS_REPO="csukuangfj/matcha-icefall-zh-baker"
    ;;
  *)
    echo "未知 TTS_VARIANT=$TTS_VARIANT（melo|matcha-baker）" >&2
    exit 2
    ;;
esac

TTS_TARGET="$ASSETS_DIR/tts/$TTS_DIR_NAME"
HF_MIRROR_TTS="https://hf-mirror.com/${HF_TTS_REPO}/resolve/main"
HF_OFFICIAL_TTS="https://huggingface.co/${HF_TTS_REPO}/resolve/main"
VOCODER_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/vocos-22khz-univ.onnx"

echo "==> CI Bundle Voice Assets"
echo "    asr target: $ASR_TARGET"
echo "    tts target: $TTS_TARGET (BUNDLE_TTS=$BUNDLE_TTS variant=$TTS_VARIANT)"

if [[ "${SKIP_DOWNLOAD:-0}" == "1" ]]; then
  echo "SKIP_DOWNLOAD=1，跳过下载。请手动放模型到:"
  echo "  $ASR_TARGET/"
  echo "  $TTS_TARGET/"
  exit 0
fi

mkdir -p "$ASR_TARGET" "$TMP_DIR"

download_file() {
  local url="$1" dest="$2"
  mkdir -p "$(dirname "$dest")"
  curl -fSL --retry 5 --retry-delay 3 --retry-all-errors \
    --connect-timeout 30 --max-time 900 \
    -C - -o "$dest" "$url"
}

# ---------- ASR ----------
if [[ -f "$ASR_TARGET/tokens.txt" ]] && \
   find "$ASR_TARGET" -maxdepth 1 -name '*.onnx' | head -1 | grep -q .; then
  echo "ASR 模型已存在，跳过: $ASR_TARGET"
  ls -lh "$ASR_TARGET/"*.onnx "$ASR_TARGET/tokens.txt" 2>/dev/null || true
else
  echo "下载 ASR 模型到 $ASR_TARGET …"
  ok=0
  echo "尝试 hf-mirror 逐文件…"
  hf_ok=0
  for f in "${ASR_FILES[@]}"; do
    echo "  ← $HF_MIRROR_ASR/$f"
    download_file "$HF_MIRROR_ASR/$f" "$ASR_TARGET/$f" || { hf_ok=1; break; }
  done
  if [[ "$hf_ok" == "0" ]] && [[ -f "$ASR_TARGET/tokens.txt" ]]; then
    ok=1
    echo "hf-mirror ASR 完成"
  fi

  if [[ "$ok" != "1" ]]; then
    echo "尝试 huggingface.co 逐文件…"
    rm -f "$ASR_TARGET"/*.onnx "$ASR_TARGET/tokens.txt" 2>/dev/null || true
    hf_ok=0
    for f in "${ASR_FILES[@]}"; do
      echo "  ← $HF_OFFICIAL_ASR/$f"
      download_file "$HF_OFFICIAL_ASR/$f" "$ASR_TARGET/$f" || { hf_ok=1; break; }
    done
    if [[ "$hf_ok" == "0" ]] && [[ -f "$ASR_TARGET/tokens.txt" ]]; then
      ok=1
      echo "huggingface.co ASR 完成"
    fi
  fi

  if [[ "$ok" != "1" ]]; then
    echo "回退 GitHub ASR 归档…"
    rm -f "$ASR_TARGET"/*.onnx "$ASR_TARGET/tokens.txt" 2>/dev/null || true
    URL="${RELEASE_ASR}/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23.tar.bz2"
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
fi

# ---------- TTS ----------
tts_ready() {
  local d="$1"
  [[ -d "$d" ]] || return 1
  [[ -f "$d/tokens.txt" ]] || return 1
  # 至少一个非 vocoder 的 onnx
  find "$d" -maxdepth 1 -type f -name '*.onnx' ! -name '*vocos*' ! -name '*vocoder*' ! -name '*hifigan*' | head -1 | grep -q .
  local has_onnx=$?
  [[ $has_onnx -eq 0 ]] || return 1
  # Matcha 额外要求 vocoder
  if [[ "$(basename "$d")" == *matcha* ]] || find "$d" -maxdepth 1 -name 'model-steps*.onnx' | head -1 | grep -q .; then
    [[ -f "$d/vocos-22khz-univ.onnx" ]] || \
      find "$(dirname "$d")" -maxdepth 1 -name 'vocos*.onnx' | head -1 | grep -q . || return 1
  fi
  return 0
}

if [[ "$BUNDLE_TTS" != "1" ]]; then
  echo "BUNDLE_TTS=$BUNDLE_TTS，跳过 TTS 打包。"
  exit 0
fi

mkdir -p "$(dirname "$TTS_TARGET")"

if tts_ready "$TTS_TARGET"; then
  echo "TTS 模型已存在，跳过: $TTS_TARGET"
  ls -lh "$TTS_TARGET/"*.onnx 2>/dev/null | head || true
  exit 0
fi

echo "下载 TTS ($TTS_VARIANT) 到 $TTS_TARGET …"
ok=0

# 优先 GitHub release tar（CI 更稳）
echo "GitHub release tar.bz2 …"
for url in "${TTS_TAR_CANDIDATES[@]}"; do
  ARCHIVE="$TMP_DIR/tts-bundle.tar.bz2"
  echo "  ← $url"
  if download_file "$url" "$ARCHIVE"; then
    echo "  解压…"
    mkdir -p "$(dirname "$TTS_TARGET")"
    tar -xjf "$ARCHIVE" -C "$(dirname "$TTS_TARGET")" || true
    # 归一目录名
    if [[ ! -d "$TTS_TARGET" ]]; then
      found="$(find "$(dirname "$TTS_TARGET")" -maxdepth 2 -type d \( -name '*melo*' -o -name '*vits*' -o -name '*matcha*baker*' \) | head -1 || true)"
      if [[ -n "${found:-}" && "$found" != "$TTS_TARGET" ]]; then
        mv "$found" "$TTS_TARGET" || true
      fi
    fi
    rm -f "$ARCHIVE"
    # Matcha 补 vocoder
    if [[ "$TTS_DIR_NAME" == *matcha* ]] && [[ -d "$TTS_TARGET" ]] && ! [[ -f "$TTS_TARGET/vocos-22khz-univ.onnx" ]]; then
      download_file "$VOCODER_URL" "$TTS_TARGET/vocos-22khz-univ.onnx" || true
    fi
    if tts_ready "$TTS_TARGET"; then
      ok=1
      echo "✅ tar TTS 就绪"
      break
    fi
  fi
done

if [[ "$ok" != "1" ]]; then
  echo "回退失败：无法下载 TTS $TTS_VARIANT" >&2
  echo "请手动放入: $TTS_TARGET" >&2
  exit 1
fi

echo "✅ TTS 模型已打包 ($TTS_VARIANT):"
ls -lh "$TTS_TARGET/"*.onnx "$TTS_TARGET/tokens.txt" 2>/dev/null | head
echo ""
echo "装完零下载：APK assets → 首次启动 BuiltInVoiceAssets.ensureTtsInstalled()"
