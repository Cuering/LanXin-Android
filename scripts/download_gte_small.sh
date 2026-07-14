#!/usr/bin/env bash
# 下载 GTE-small int8 模型 (Xenova 量化版) 到 Android assets
# 用法: bash scripts/download_gte_small.sh

set -euo pipefail

MODEL_DIR="app/src/main/assets/models/gte-small"
mkdir -p "$MODEL_DIR"

HF_BASE="https://huggingface.co/Xenova/gte-small/resolve/main/onnx/int8"

# 模型文件 ~33MB
echo "下载 model_int8.onnx..."
curl -fSL "$HF_BASE/model_int8.onnx" -o "$MODEL_DIR/model_int8.onnx"

# tokenizer.json (约 2MB)
if [ ! -f "$MODEL_DIR/tokenizer.json" ]; then
    echo "下载 tokenizer.json..."
    curl -fSL "https://huggingface.co/Xenova/gte-small/resolve/main/tokenizer.json" \
        -o "$MODEL_DIR/tokenizer.json"
else
    echo "tokenizer.json 已存在，跳过"
fi

echo "✅ 下载完成："
ls -lh "$MODEL_DIR/"
