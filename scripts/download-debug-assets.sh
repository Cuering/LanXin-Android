#!/usr/bin/env bash
# 一键准备 Debug 资源：Live2D（官方 sample）+ ASR + TTS（sherpa-onnx）
# 大文件写入 debug-assets/（gitignore），不进 git 主仓。
#
# 用法:
#   bash scripts/download-debug-assets.sh
#   SKIP_DOWNLOAD=1 bash scripts/download-debug-assets.sh   # 仅打印约定
#   COMPONENTS=asr,tts bash scripts/download-debug-assets.sh
#   COMPONENTS=live2d bash scripts/download-debug-assets.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

COMPONENTS="${COMPONENTS:-live2d,asr,tts}"
export SKIP_DOWNLOAD="${SKIP_DOWNLOAD:-0}"

echo "=========================================="
echo " LanXin Debug Assets"
echo " root: $ROOT"
echo " components: $COMPONENTS"
echo " docs: docs/debug-assets.md"
echo "=========================================="
echo

run_one() {
  local name="$1"
  local script="$2"
  echo "-------- $name --------"
  if [[ -x "$script" ]] || [[ -f "$script" ]]; then
    bash "$script"
  else
    echo "缺少脚本: $script" >&2
    exit 1
  fi
  echo
}

IFS=',' read -ra PARTS <<< "$COMPONENTS"
for c in "${PARTS[@]}"; do
  c="$(echo "$c" | tr '[:upper:]' '[:lower:]' | xargs)"
  case "$c" in
    live2d|l2d) run_one "Live2D" "scripts/download-debug-live2d.sh" ;;
    asr|speech) run_one "ASR" "scripts/download-debug-asr.sh" ;;
    tts|voice)  run_one "TTS" "scripts/download-debug-tts.sh" ;;
    all)
      run_one "Live2D" "scripts/download-debug-live2d.sh"
      run_one "ASR" "scripts/download-debug-asr.sh"
      run_one "TTS" "scripts/download-debug-tts.sh"
      ;;
    "") ;;
    *)
      echo "未知 component: $c（live2d|asr|tts）" >&2
      exit 2
      ;;
  esac
done

echo "=========================================="
echo " 汇总：设置页路径约定"
echo "=========================================="
echo "live2d_model_path     → debug-assets/live2d/Mao/Mao.model3.json"
echo "offline_asr_model_path→ debug-assets/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
echo "tts_model_dir         → debug-assets/tts/<解压目录>"
echo
echo "缺失资源时 App 使用 stub / desktop-pet.html 占位。"
echo "妹居资源禁止入库；仅本机 APK 可作对照（docs/meiju-style-pet.md）。"
echo
echo "完整文档: docs/debug-assets.md"
echo "✅ 全部完成"
