#!/usr/bin/env bash
# Phase 6 M2 推荐入口：拉取 Debug Live2D / ASR / TTS。
#
# 本脚本是 download-debug-assets.sh 的薄封装（兼容设置页文案与文档）。
#
# 硬性约束：
# - 在**开发者机器**或按需的 GitHub Actions 上运行
# - **不要**在 AstrBot 服务器 /data/download 上 curl 拉模型当交付
# - 大文件写入 debug-assets/（gitignore），不进 git
#
# 用法：
#   bash scripts/fetch-debug-assets.sh
#   COMPONENTS=live2d bash scripts/fetch-debug-assets.sh
#   SKIP_DOWNLOAD=1 bash scripts/fetch-debug-assets.sh
#
# 详见 docs/debug-assets.md · docs/meiju-style-pet.md

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="$ROOT/scripts/download-debug-assets.sh"

if [[ ! -f "$TARGET" ]]; then
  echo "缺少 $TARGET" >&2
  exit 1
fi

echo "==> fetch-debug-assets → download-debug-assets.sh $*"
echo "    docs: docs/debug-assets.md"
echo "    禁止在 AstrBot 服务器缓存模型作为交付"
echo

exec bash "$TARGET" "$@"
