#!/usr/bin/env bash
# 准备 Debug 用 Live2D 官方 Sample（Mao / Haru）到 debug-assets/live2d/
#
# 说明:
# - 官方 Sample 受 Free Material License / Sample Data Terms 约束:
#   https://www.live2d.com/en/learn/sample/model-terms
# - CubismWebSamples 仓库可通过 sparse checkout 拉取 Resources，体积可控。
# - 若网络受限，脚本会打印手动步骤并退出 0（不阻断其它资源脚本）。
#
# 用法:
#   bash scripts/download-debug-live2d.sh
#   LIVE2D_MODEL=Haru bash scripts/download-debug-live2d.sh
#   SKIP_DOWNLOAD=1 bash scripts/download-debug-live2d.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${OUT_DIR:-$ROOT/debug-assets/live2d}"
TMP_DIR="${TMP_DIR:-$ROOT/debug-assets/.tmp}"
LIVE2D_MODEL="${LIVE2D_MODEL:-Mao}"   # Mao | Haru | all
CUBISM_REPO="${CUBISM_REPO:-https://github.com/Live2D/CubismWebSamples.git}"
CUBISM_BRANCH="${CUBISM_BRANCH:-develop}"

mkdir -p "$OUT_DIR" "$TMP_DIR"

echo "==> Debug Live2D"
echo "    model(s): $LIVE2D_MODEL"
echo "    out     : $OUT_DIR"
echo "    license : https://www.live2d.com/en/learn/sample/model-terms"
echo "    sample  : https://www.live2d.com/en/learn/sample/niziiro-mao"
echo "              https://www.live2d.com/en/learn/sample/haru"

models=()
case "$LIVE2D_MODEL" in
  all) models=(Mao Haru) ;;
  Mao|Haru) models=("$LIVE2D_MODEL") ;;
  *)
    echo "未知 LIVE2D_MODEL=$LIVE2D_MODEL（Mao|Haru|all）" >&2
    exit 2
    ;;
esac

print_manual() {
  cat <<EOF

---- 手动步骤（官方需合规使用）----
1. 阅读许可: https://www.live2d.com/en/learn/sample/model-terms
2. 克隆或下载 CubismWebSamples:
     git clone --depth 1 --filter=blob:none --sparse \\
       $CUBISM_REPO $TMP_DIR/CubismWebSamples
     cd $TMP_DIR/CubismWebSamples
     git sparse-checkout set Samples/Resources/Mao Samples/Resources/Haru
3. 拷贝到约定目录:
     cp -a Samples/Resources/Mao  $OUT_DIR/
     cp -a Samples/Resources/Haru $OUT_DIR/
4. 设置页 / 配置:
     live2d_model_path = $OUT_DIR/Mao/Mao.model3.json
     # 或 Haru:
     live2d_model_path = $OUT_DIR/Haru/Haru.model3.json

仓库内占位（无 moc3）: app/src/main/assets/pet/desktop-pet.html
EOF
}

if [[ "${SKIP_DOWNLOAD:-0}" == "1" ]]; then
  echo "SKIP_DOWNLOAD=1，跳过实际下载。"
  print_manual
  exit 0
fi

need_fetch=0
for m in "${models[@]}"; do
  if [[ ! -f "$OUT_DIR/$m/$m.model3.json" ]]; then
    need_fetch=1
  fi
done

if [[ "$need_fetch" == "0" ]]; then
  echo "model3.json 已存在，跳过克隆。"
else
  if ! command -v git >/dev/null 2>&1; then
    echo "未找到 git，无法自动拉取 CubismWebSamples。"
    print_manual
    exit 0
  fi

  CLONE_DIR="$TMP_DIR/CubismWebSamples"
  rm -rf "$CLONE_DIR"
  echo "sparse-clone CubismWebSamples ($CUBISM_BRANCH)…"
  set +e
  git clone --depth 1 --filter=blob:none --sparse \
    --branch "$CUBISM_BRANCH" \
    "$CUBISM_REPO" "$CLONE_DIR"
  CLONE_RC=$?
  set -e
  if [[ $CLONE_RC -ne 0 ]]; then
    echo "克隆失败（网络/权限）。"
    print_manual
    exit 0
  fi

  (
    cd "$CLONE_DIR"
    PATHS=()
    for m in "${models[@]}"; do
      PATHS+=("Samples/Resources/$m")
    done
    git sparse-checkout set "${PATHS[@]}"
  )

  for m in "${models[@]}"; do
    SRC="$CLONE_DIR/Samples/Resources/$m"
    if [[ -d "$SRC" ]]; then
      rm -rf "$OUT_DIR/$m"
      cp -a "$SRC" "$OUT_DIR/$m"
      echo "已复制 $m → $OUT_DIR/$m"
    else
      echo "警告: 未找到 $SRC"
    fi
  done
fi

echo
echo "---- 设置页应填路径 ----"
for m in "${models[@]}"; do
  JSON="$OUT_DIR/$m/$m.model3.json"
  if [[ -f "$JSON" ]]; then
    echo "live2d_model_path = $JSON"
  else
    echo "live2d_model_path = $OUT_DIR/$m/$m.model3.json  # 待下载"
  fi
done
echo
echo "许可: https://www.live2d.com/en/learn/sample/model-terms"
echo "文档: docs/debug-assets.md"
echo "✅ Live2D debug 资源步骤完成（样例仅限学习/SDK 集成测试）"
