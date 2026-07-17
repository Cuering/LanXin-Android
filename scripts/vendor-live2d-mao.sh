#!/usr/bin/env bash
# 将官方 CubismWebSamples Niziiro Mao 同步到仓内 assets（可 commit）。
#
# 许可: https://www.live2d.com/en/learn/sample/model-terms
# 来源: https://github.com/Live2D/CubismWebSamples (Samples/Resources/Mao)
#
# 用法:
#   bash scripts/vendor-live2d-mao.sh
#   SKIP_DOWNLOAD=1 bash scripts/vendor-live2d-mao.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="${DEST:-$ROOT/app/src/main/assets/pet/live2d/Mao}"
TMP_DIR="${TMP_DIR:-$ROOT/debug-assets/.tmp}"
CUBISM_REPO="${CUBISM_REPO:-https://github.com/Live2D/CubismWebSamples.git}"
CUBISM_BRANCH="${CUBISM_BRANCH:-develop}"

echo "==> vendor Live2D Mao → $DEST"
echo "    license: https://www.live2d.com/en/learn/sample/model-terms"
echo "    sample : https://www.live2d.com/en/learn/sample/niziiro-mao"

if [[ "${SKIP_DOWNLOAD:-0}" == "1" ]]; then
  echo "SKIP_DOWNLOAD=1"
  exit 0
fi

mkdir -p "$TMP_DIR" "$(dirname "$DEST")"
CLONE_DIR="$TMP_DIR/CubismWebSamples-vendor"
rm -rf "$CLONE_DIR"

git clone --depth 1 --filter=blob:none --sparse \
  --branch "$CUBISM_BRANCH" \
  "$CUBISM_REPO" "$CLONE_DIR"
(
  cd "$CLONE_DIR"
  git sparse-checkout set Samples/Resources/Mao
)

SRC="$CLONE_DIR/Samples/Resources/Mao"
if [[ ! -f "$SRC/Mao.model3.json" ]]; then
  echo "错误: 未找到 $SRC/Mao.model3.json" >&2
  exit 1
fi

rm -rf "$DEST"
cp -a "$SRC" "$DEST"

cat > "$DEST/NOTICE.txt" <<'EOF'
Niziiro Mao — Live2D Official Sample
====================================

Source: Live2D CubismWebSamples
  https://github.com/Live2D/CubismWebSamples/tree/develop/Samples/Resources/Mao

Product page:
  https://www.live2d.com/en/learn/sample/niziiro-mao

License — Live2D Sample Data Terms (Free Material License):
  https://www.live2d.com/en/learn/sample/model-terms

This tree is the official sample only. Do not replace with commercial /
Meiju / third-party models. Commercial Live2D content must not be committed.

Vendored for LanXin Android desktop pet out-of-box debug & polish.
Re-sync: bash scripts/vendor-live2d-mao.sh
EOF

echo
echo "已写入: $DEST"
du -sh "$DEST"
echo "git 白名单见 .gitignore: !app/src/main/assets/pet/live2d/**"
echo "✅ vendor 完成。请 git add 后提交。"
