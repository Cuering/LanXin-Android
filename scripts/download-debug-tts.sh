#!/usr/bin/env bash
# 下载 Debug 用 sherpa-onnx 中文 TTS（女声优先）到 debug-assets/tts/
# 优先 HuggingFace 镜像逐文件下载（用户实测可用），回退 GitHub tar.bz2。
#
# 用法:
#   bash scripts/download-debug-tts.sh
#   TTS_VARIANT=melo bash scripts/download-debug-tts.sh
#   SKIP_DOWNLOAD=1 bash scripts/download-debug-tts.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${OUT_DIR:-$ROOT/debug-assets/tts}"
TMP_DIR="${TMP_DIR:-$ROOT/debug-assets/.tmp}"
TTS_VARIANT="${TTS_VARIANT:-melo}"

# 用户实测：hf-mirror 可打开 matcha-icefall-zh-baker model card 与 Files
HF_REPO="csukuangfj/matcha-icefall-zh-baker"
HF_MIRROR_BASE="https://hf-mirror.com/${HF_REPO}/resolve/main"
HF_OFFICIAL_BASE="https://huggingface.co/${HF_REPO}/resolve/main"
RELEASE_TTS="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

# matcha-baker 运行必需（跳过训练脚本 / 无关 README）
TTS_FILES=(
  "model-steps-3.onnx"
  "vocos-22khz-univ.onnx"
  "tokens.txt"
  "lexicon.txt"
  "date.fst"
  "number.fst"
  "phone.fst"
  "dict/jieba.dict.utf8"
  "dict/hmm_model.utf8"
  "dict/idf.utf8"
  "dict/stop_words.utf8"
  "dict/user.dict.utf8"
  "dict/pos_dict/char_state_tab.utf8"
  "dict/pos_dict/prob_emit.utf8"
  "dict/pos_dict/prob_start.utf8"
  "dict/pos_dict/prob_trans.utf8"
)

# vocoder 从独立仓库下载：不在 HF_TTS_REPO 里
VOCODER_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/vocos-22khz-univ.onnx"

mkdir -p "$OUT_DIR" "$TMP_DIR"

download_file() {
  local url="$1"
  local dest="$2"
  mkdir -p "$(dirname "$dest")"
  # CI / 弱网：更长超时 + 断点续传
  curl -fSL --retry 5 --retry-delay 3 --retry-all-errors \
    --connect-timeout 30 --max-time 900 \
    -C - -o "$dest" "$url"
}

download_hf_files() {
  local base="$1"
  local target="$2"
  mkdir -p "$target"
  local f
  for f in "${TTS_FILES[@]}"; do
    # vocoder 不在 HF 模型仓库，从 GitHub vocoder-models 单独拉
    if [[ "$f" == "vocos-22khz-univ.onnx" ]]; then
      echo "  ← $VOCODER_URL"
      download_file "$VOCODER_URL" "$target/$f"
    else
      echo "  ← $base/$f"
      download_file "$base/$f" "$target/$f"
    fi
  done
}

is_ready() {
  local d="$1"
  [[ -d "$d" ]] || return 1
  [[ -f "$d/tokens.txt" ]] || return 1
  # 至少一个非 vocoder onnx
  find "$d" -maxdepth 1 -type f -name '*.onnx' ! -name '*vocos*' ! -name '*vocoder*' ! -name '*hifigan*' | head -1 | grep -q . || return 1
  # Matcha 才强制 vocoder
  if [[ "$(basename "$d")" == *matcha* ]] || find "$d" -maxdepth 1 -name 'model-steps*.onnx' | head -1 | grep -q .; then
    [[ -f "$d/vocos-22khz-univ.onnx" ]] || return 1
  fi
  return 0
}

github_archive_urls() {
  case "$TTS_VARIANT" in
    matcha-baker|matcha|baker|default)
      cat <<EOF
${RELEASE_TTS}/matcha-icefall-zh-baker.tar.bz2
${RELEASE_TTS}/sherpa-onnx-matcha-icefall-zh-baker.tar.bz2
EOF
      ;;
    melo|vits-melo|melo-zh-en)
      cat <<EOF
${RELEASE_TTS}/vits-melo-tts-zh_en.tar.bz2
${RELEASE_TTS}/sherpa-onnx-vits-melo-tts-zh_en.tar.bz2
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

TARGET_MATCHA="$OUT_DIR/matcha-icefall-zh-baker"
TARGET_MELO="$OUT_DIR/vits-melo-tts-zh_en"

echo "==> Debug TTS"
echo "    variant : $TTS_VARIANT"
echo "    out     : $OUT_DIR"
echo "    strategy: hf-mirror → huggingface → github tar.bz2（仅 matcha-baker 走 HF 逐文件）"
# 当前 variant 目标目录（CI/本地共用）
case "$TTS_VARIANT" in
  matcha-baker|matcha|baker|default) TARGET="$TARGET_MATCHA" ;;
  melo|vits-melo|melo-zh-en) TARGET="$TARGET_MELO" ;;
  vits-zh-ll|multi) TARGET="$OUT_DIR/vits-zh-ll" ;;
  *) TARGET="$OUT_DIR/$TTS_VARIANT" ;;
esac
echo "    target  : $TARGET"

normalize_extracted_dir() {
  local want_basename found
  want_basename="$(basename "$TARGET")"
  [[ -d "$TARGET" ]] && return 0
  found="$(find "$OUT_DIR" -maxdepth 2 -type d -name "$want_basename" | head -1 || true)"
  if [[ -z "$found" ]]; then
    case "$TTS_VARIANT" in
      melo|vits-melo|melo-zh-en)
        found="$(find "$OUT_DIR" -maxdepth 2 -type d \( -name '*melo*' -o -name '*vits*melo*' \) | head -1 || true)"
        ;;
      matcha-baker|matcha|baker|default)
        found="$(find "$OUT_DIR" -maxdepth 2 -type d -name '*matcha*baker*' | head -1 || true)"
        ;;
    esac
  fi
  if [[ -n "${found:-}" && "$found" != "$TARGET" ]]; then
    mkdir -p "$(dirname "$TARGET")"
    mv "$found" "$TARGET" || true
  fi
}

# GitHub Actions：按 TTS_VARIANT 拉对应 release tar（禁止硬编码 matcha）
if [[ "${CI:-}" == "true" || "${GITHUB_ACTIONS:-}" == "true" ]]; then
  echo "CI 环境：按 variant=$TTS_VARIANT 拉 GitHub release tar.bz2 …"
  if ! is_ready "$TARGET"; then
    while IFS= read -r url; do
      [[ -z "$url" ]] && continue
      ARCHIVE="$TMP_DIR/tts-ci-$(basename "$url")"
      echo "  ← $url"
      if download_file "$url" "$ARCHIVE"; then
        echo "  解压…"
        case "$(basename "$ARCHIVE")" in
          *.tar.bz2) tar -xjf "$ARCHIVE" -C "$OUT_DIR" || true ;;
          *.tar.gz|*.tgz) tar -xzf "$ARCHIVE" -C "$OUT_DIR" || true ;;
          *) tar -xf "$ARCHIVE" -C "$OUT_DIR" 2>/dev/null || true ;;
        esac
        normalize_extracted_dir
        if [[ -d "$TARGET" && "$(basename "$TARGET")" == *matcha* && ! -f "$TARGET/vocos-22khz-univ.onnx" ]]; then
          download_file "$VOCODER_URL" "$TARGET/vocos-22khz-univ.onnx" || true
        fi
        rm -f "$ARCHIVE"
        if is_ready "$TARGET"; then
          echo "✅ CI tar 路径就绪: $TARGET"
          break
        fi
      else
        rm -f "$ARCHIVE" 2>/dev/null || true
      fi
    done < <(github_archive_urls)
  else
    echo "CI: 当前 variant 已就绪: $TARGET"
  fi
fi


if [[ "${SKIP_DOWNLOAD:-0}" == "1" ]]; then
  echo "SKIP_DOWNLOAD=1，跳过实际下载。"
  echo
  echo "---- 设置页应填路径（下载后）----"
  echo "tts_model_dir = $OUT_DIR/<解压目录>"
  echo "App 内落盘等价路径: LanXin/tts/vits-melo-tts-zh_en"
  echo "文档: docs/debug-assets.md"
  exit 0
fi

# 仅当前 variant 目标就绪才跳过（matcha 就绪不能挡住 melo）
if is_ready "$TARGET"; then
  echo "已存在当前 variant 就绪目录，跳过下载: $TARGET"
else
  ok=0
  if [[ "$TTS_VARIANT" == "matcha-baker" || "$TTS_VARIANT" == "matcha" || "$TTS_VARIANT" == "baker" || "$TTS_VARIANT" == "default" ]]; then
    echo "尝试 hf-mirror 逐文件…"
    if download_hf_files "$HF_MIRROR_BASE" "$TARGET_MATCHA"; then
      if is_ready "$TARGET_MATCHA"; then
        ok=1
        echo "hf-mirror 完成"
      fi
    else
      rm -rf "$TARGET_MATCHA"
      echo "hf-mirror 失败，尝试 huggingface.co…"
      if download_hf_files "$HF_OFFICIAL_BASE" "$TARGET_MATCHA"; then
        if is_ready "$TARGET_MATCHA"; then
          ok=1
          echo "huggingface.co 完成"
        fi
      else
        rm -rf "$TARGET_MATCHA"
      fi
    fi
  fi

  if [[ "$ok" != "1" ]]; then
    echo "回退 GitHub 归档…"
    while IFS= read -r URL; do
      [[ -z "$URL" ]] && continue
      ARCHIVE_NAME="$(basename "$URL")"
      ARCHIVE="$TMP_DIR/$ARCHIVE_NAME"
      echo "尝试: $URL"
      if curl -fSL --retry 2 --retry-delay 1 -o "$ARCHIVE" "$URL"; then
        echo "解压…"
        case "$ARCHIVE_NAME" in
          *.tar.bz2) tar -xjf "$ARCHIVE" -C "$OUT_DIR" ;;
          *.tar.gz|*.tgz) tar -xzf "$ARCHIVE" -C "$OUT_DIR" ;;
          *.zip) unzip -q "$ARCHIVE" -d "$OUT_DIR" ;;
          *) tar -xf "$ARCHIVE" -C "$OUT_DIR" 2>/dev/null || true ;;
        esac
        rm -f "$ARCHIVE"
        ok=1
        break
      else
        rm -f "$ARCHIVE" 2>/dev/null || true
        echo "  失败，试下一个候选…"
      fi
    done < <(github_archive_urls)
  fi

  if [[ "$ok" != "1" ]]; then
    echo >&2
    echo "❌ 未能自动下载 TTS（HF 与 GitHub 均失败）。" >&2
    echo "请打开 https://hf-mirror.com/csukuangfj/matcha-icefall-zh-baker 或" >&2
    echo "https://github.com/k2-fsa/sherpa-onnx/releases 手动下载到:" >&2
    echo "  $OUT_DIR" >&2
    echo "然后设置 tts_model_dir 指向解压目录。" >&2
    exit 1
  fi
fi

RESOLVED="$TARGET"
if ! is_ready "$RESOLVED" 2>/dev/null; then
  RESOLVED="$(find "$OUT_DIR" -mindepth 1 -maxdepth 1 -type d ! -name '.*' | while read -r d; do is_ready "$d" && echo "$d"; done | head -1 || true)"
fi

echo
echo "---- 设置页应填路径 ----"
echo "tts_model_dir = ${RESOLVED:-$OUT_DIR/<解压目录>}"
echo "tts_enabled   = true   # M1 默认 false；debug 真机时再开"
echo
echo "App 内落盘等价路径: LanXin/tts/vits-melo-tts-zh_en"
echo "推荐模型: vits-melo-tts-zh_en（默认，更自然）/ matcha-icefall-zh-baker"
echo "文档: docs/debug-assets.md"
echo "✅ TTS debug 资源步骤完成"
