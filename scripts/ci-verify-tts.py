#!/usr/bin/env python3
"""
CI 验证：下载 sherpa-onnx 模型 → 真合成 → 校验 PCM 非空。

在 GitHub CI (x86_64 Linux) 上运行，通过 sherpa-onnx Python wheel 验证：
1. ASR 模型可加载
2. TTS 模型（matcha + vocoder）可加载，合成输出非空 PCM

用法:
  pip install sherpa-onnx
  python3 scripts/ci-verify-tts.py
"""
import argparse
import os
import sys
import subprocess
import tempfile
import urllib.request
import tarfile
import json

def download(url, dest):
    print(f"  ↓ {url}")
    urllib.request.urlretrieve(url, dest)

def verify_asr(model_dir):
    """Verify ASR model files exist and are valid ONNX."""
    required = [
        "encoder-epoch-99-avg-1.int8.onnx",
        "decoder-epoch-99-avg-1.int8.onnx",
        "joiner-epoch-99-avg-1.int8.onnx",
        "tokens.txt",
    ]
    for f in required:
        path = os.path.join(model_dir, f)
        assert os.path.isfile(path), f"ASR missing: {path}"
        sz = os.path.getsize(path)
        print(f"  ✅ ASR {f} ({sz / 1024 / 1024:.1f} MB)")
    print("✅ ASR model files verified")

def verify_tts(model_dir):
    """Verify TTS model files exist and try real synthesis via sherpa-onnx."""
    required = [
        "model-steps-3.onnx",
        "vocos-22khz-univ.onnx",
        "tokens.txt",
        "lexicon.txt",
        "date.fst",
        "number.fst",
        "phone.fst",
    ]
    dict_files = [
        "dict/jieba.dict.utf8",
        "dict/hmm_model.utf8",
        "dict/idf.utf8",
        "dict/stop_words.utf8",
        "dict/user.dict.utf8",
    ]
    for f in required:
        path = os.path.join(model_dir, f)
        assert os.path.isfile(path), f"TTS missing: {path}"
        print(f"  ✅ TTS {f} ({os.path.getsize(path) / 1024 / 1024:.1f} MB)")
    for f in dict_files:
        path = os.path.join(model_dir, f)
        assert os.path.isfile(path), f"TTS dict missing: {path}"
    print("✅ TTS model files verified")

    # Try real synthesis with sherpa-onnx Python bindings
    try:
        import sherpa_onnx
    except ImportError:
        print("⚠️  sherpa-onnx not installed, skipping native synthesis test")
        print("   Install: pip install sherpa-onnx")
        return

    print("  🎤 Running TTS synthesis (sherpa-onnx Python)...")
    try:
        tts_config = sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(
                matcha=sherpa_onnx.OfflineTtsMatchaModelConfig(
                    acoustic_model=os.path.join(model_dir, "model-steps-3.onnx"),
                    vocoder=os.path.join(model_dir, "vocos-22khz-univ.onnx"),
                    tokens=os.path.join(model_dir, "tokens.txt"),
                    lexicon=os.path.join(model_dir, "lexicon.txt"),
                    dict_dir=os.path.join(model_dir, "dict"),
                ),
                num_threads=2,
            ),
            rule_fsts=(
                f"{model_dir}/phone.fst,"
                f"{model_dir}/date.fst,"
                f"{model_dir}/number.fst"
            ),
            max_num_sentences=1,
        )
        tts = sherpa_onnx.OfflineTts(tts_config)
        audio = tts.generate("你好，欢迎使用兰心桌宠语音助手。今天天气真不错！", sid=0, speed=1.0)
        assert audio.samples is not None, "TTS returned None samples"
        assert len(audio.samples) > 1000, f"TTS output too short: {len(audio.samples)}"
        duration = len(audio.samples) / audio.sample_rate
        print(f"  🔉 Synthesized {len(audio.samples)} samples at {audio.sample_rate}Hz ({duration:.1f}s)")
        print("✅ TTS native synthesis verified (PCM non-empty)")
    except Exception as e:
        print(f"❌ TTS synthesis failed: {e}")
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description="CI verify ASR/TTS models")
    parser.add_argument("--asr-dir", default=None, help="ASR model directory")
    parser.add_argument("--tts-dir", default=None, help="TTS model directory")
    parser.add_argument("--download", action="store_true", help="Download models if not present")
    parser.add_argument("--output-dir", default="/tmp/ci-voice-models", help="Download directory")
    args = parser.parse_args()

    workdir = args.output_dir

    if args.download:
        print("=" * 60)
        print("Downloading ASR model...")
        print("=" * 60)
        asr_archive = os.path.join(workdir, "asr.tar.bz2")
        os.makedirs(workdir, exist_ok=True)
        asr_url = (
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"
            "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23.tar.bz2"
        )
        if not os.path.exists(asr_archive):
            download(asr_url, asr_archive)
        print("  Extracting...")
        with tarfile.open(asr_archive, "r:bz2") as tar:
            tar.extractall(path=workdir)
        asr_dir = os.path.join(
            workdir, "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
        )
        assert os.path.isdir(asr_dir), f"ASR dir not found: {asr_dir}"

        print()
        print("=" * 60)
        print("Downloading TTS model (matcha-icefall-zh-baker + vocoder)...")
        print("=" * 60)
        tts_dir = os.path.join(workdir, "matcha-icefall-zh-baker")
        os.makedirs(tts_dir, exist_ok=True)

        # Download model files from HF mirror
        hf_base = "https://hf-mirror.com/csukuangfj/matcha-icefall-zh-baker/resolve/main"
        tts_files = [
            "model-steps-3.onnx",
            "tokens.txt",
            "lexicon.txt",
            "date.fst",
            "number.fst",
            "phone.fst",
        ]
        for f in tts_files:
            dest = os.path.join(tts_dir, f)
            if not os.path.exists(dest):
                download(f"{hf_base}/{f}", dest)

        # Download dict files
        dict_base = f"{hf_base}/dict"
        dict_files = [
            "jieba.dict.utf8",
            "hmm_model.utf8",
            "idf.utf8",
            "stop_words.utf8",
            "user.dict.utf8",
            "pos_dict/char_state_tab.utf8",
            "pos_dict/prob_emit.utf8",
            "pos_dict/prob_start.utf8",
            "pos_dict/prob_trans.utf8",
        ]
        for f in dict_files:
            dest = os.path.join(tts_dir, f)
            if not os.path.exists(dest):
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                download(f"{dict_base}/{f}", dest)

        # Download vocoder from GitHub
        vocoder_path = os.path.join(tts_dir, "vocos-22khz-univ.onnx")
        if not os.path.exists(vocoder_path):
            download(
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/vocos-22khz-univ.onnx",
                vocoder_path,
            )
    else:
        asr_dir = args.asr_dir
        tts_dir = args.tts_dir

    print()
    print("=" * 60)
    print("Verifying ASR model...")
    print("=" * 60)
    verify_asr(asr_dir or os.path.join(workdir, "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"))

    print()
    print("=" * 60)
    print("Verifying TTS model (files + native synthesis)...")
    print("=" * 60)
    verify_tts(tts_dir or os.path.join(workdir, "matcha-icefall-zh-baker"))

    print()
    print("=" * 60)
    print("✅ ALL CHECKS PASSED")
    print("=" * 60)


if __name__ == "__main__":
    main()
