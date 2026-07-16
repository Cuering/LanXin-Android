#!/usr/bin/env bash
# 下载 Debug 用 sherpa-onnx 中文 TTS（女声优先）到 debug-assets/tts/
# 用法:
#   bash scripts/download-debug-tts.sh
#   TTS_VARIANT=melo bash scripts/download-debug-tts.sh
#   SKIP_DOWNLOAD=1 bash scripts/download-debug-tts.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${OUT_DIR:-$ROOT/debug-assets/tts}"
TMP_DIR="${TMP_DIR:-$ROOT/debug-assets/.tmp}"
TTS_VARIANT="${TTS_VARIANT:-matcha-baker}"

# 官方 release 命名可能微调；失败时打印文档链接
# 参考: https://k2-fsa.github.io/sherpa/onnx/tts/
#       https://github.com/k2-fsa/sherpa-onnx/releases
RELEASE_TTS="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

mkdir -p "$OUT_DIR" "$TMP_DIR"

# 候选 URL 列表（按 variant）；脚本会依次尝试直到成功
urls_for_variant() {
  case "$TTS_VARIANT" in
    matcha-baker|matcha|baker|default)
      cat <<EOF
${RELEASE_TTS}/matcha-icefall-zh-baker.tar.bz2
${RELEASE_TTS}/sherpa-onnx-matcha-icefall-zh-baker.tar.bz2
https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/matcha-icefall-zh-baker.tar.bz2
EOF
      ;;
    melo|vits-melo|melo-zh-en)
      cat <<EOF
${RELEASE_TTS}/vits-melo-tts-zh_en.tar.bz2
${RELEASE_TTS}/sherpa-onnx-vits-melo-tts-zh_en.tar.bz2
https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
EOF
      ;;
    vits-zh-ll|multi)
      cat <<EOF
${RELEASE_TTS}/vits-zh-ll.tar.bz2
${RELEASE_TTS}/sherpa-onnx-vits-zh-ll.tar.bz2
https://github.com/csukuangfj/sherpa-onnx-vits-zh-ll/releases/latest/download/vits-zh-ll.tar.bz2
EOF
      ;;
    *)
      echo "未知 TTS_VARIANT=$TTS_VARIANT" >&2
      echo "可选: matcha-baker | melo | vits-zh-ll" >&2
      exit 2
      ;;
  esac
}

echo "==> Debug TTS"
echo "    variant : $TTS_VARIANT"
echo "    out     : $OUT_DIR"

if [[ "${SKIP_DOWNLOAD:-0}" == "1" ]]; then
  echo "SKIP_DOWNLOAD=1，跳过实际下载。"
  echo
  echo "---- 设置页应填路径（下载后）----"
  echo "tts_model_dir = $OUT_DIR/<解压目录>"
  echo "文档: docs/debug-assets.md · https://k2-fsa.github.io/sherpa/onnx/tts/"
  exit 0
fi

# 若已有任意模型目录则跳过
if find "$OUT_DIR" -mindepth 1 -maxdepth 1 -type d ! -name '.*' 2>/dev/null | grep -q .; then
  echo "已存在 TTS 目录，跳过下载:"
  find "$OUT_DIR" -mindepth 1 -maxdepth 1 -type d ! -name '.*'
else
  OK=0
  while IFS= read -r URL; do
    [[ -z "$URL" ]] && continue
    ARCHIVE_NAME="$(basename "$URL")"
    ARCHIVE="$TMP_DIR/$ARCHIVE_NAME"
    echo "尝试: $URL"
    if curl -fSL --retry 2 --retry-delay 1 -o "$ARCHIVE" "$URL"; then
      echo "解压…"
      # 兼容 tar.bz2 / tar.gz / zip
      case "$ARCHIVE_NAME" in
        *.tar.bz2) tar -xjf "$ARCHIVE" -C "$OUT_DIR" ;;
        *.tar.gz|*.tgz) tar -xzf "$ARCHIVE" -C "$OUT_DIR" ;;
        *.zip) unzip -q "$ARCHIVE" -d "$OUT_DIR" ;;
        *) tar -xf "$ARCHIVE" -C "$OUT_DIR" 2>/dev/null || true ;;
      esac
      rm -f "$ARCHIVE"
      OK=1
      break
    else
      rm -f "$ARCHIVE" 2>/dev/null || true
      echo "  失败，试下一个候选…"
    fi
  done < <(urls_for_variant)

  if [[ "$OK" != "1" ]]; then
    echo >&2
    echo "❌ 未能自动下载 TTS 包（release 文件名可能已变更）。" >&2
    echo "请打开 https://k2-fsa.github.io/sherpa/onnx/tts/ 与" >&2
    echo "https://github.com/k2-fsa/sherpa-onnx/releases 手动下载 matcha-baker / melo 到:" >&2
    echo "  $OUT_DIR" >&2
    echo "然后设置 tts_model_dir 指向解压目录。" >&2
    exit 1
  fi
fi

RESOLVED="$(find "$OUT_DIR" -mindepth 1 -maxdepth 1 -type d ! -name '.*' | head -1 || true)"

echo
echo "---- 设置页应填路径 ----"
echo "tts_model_dir = ${RESOLVED:-$OUT_DIR/<解压目录>}"
echo "tts_enabled   = true   # M1 默认 false；debug 真机时再开"
echo
echo "推荐模型: matcha-icefall-zh-baker（女声）/ vits-melo-tts-zh_en"
echo "文档: docs/debug-assets.md"
echo "✅ TTS debug 资源步骤完成"
