#!/usr/bin/env python3
"""
CI 验证：下载 sherpa-onnx 模型 → 真合成 → 校验 PCM 非空。

用法:
  pip install sherpa-onnx
  python3 scripts/ci-verify-tts.py --variant melo   # 默认 VITS Melo
  python3 scripts/ci-verify-tts.py --variant matcha  # Matcha Baker
  python3 scripts/ci-verify-tts.py --tts-dir /path  # 本地路径
"""
import argparse
import os, sys, tarfile, ssl
import urllib.request

def download(url, dest):
    print(f"  \u2193 {url}")
    ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
    with urllib.request.urlopen(url, context=ctx, timeout=60) as resp:
        total = getattr(resp, 'length', None)
        chunk_size = 1024 * 1024
        downloaded = 0
        with open(dest, 'wb') as f:
            while True:
                chunk = resp.read(chunk_size)
                if not chunk: break
                f.write(chunk); downloaded += len(chunk)
                if total: print(f"  {downloaded/1024/1024:.1f}/{total/1024/1024:.1f} MB", end='', flush=True)
        print()

def verify_asr(model_dir):
    for f in ["encoder-epoch-99-avg-1.int8.onnx","decoder-epoch-99-avg-1.int8.onnx",
              "joiner-epoch-99-avg-1.int8.onnx","tokens.txt"]:
        p=os.path.join(model_dir,f); assert os.path.isfile(p),f"ASR missing:{p}"
        print(f"  \u2705 ASR {f} ({os.path.getsize(p)/1024/1024:.1f} MB)")
    print("OK ASR verified")

def verify_tts_melo(model_dir):
    for f in ["model.onnx","tokens.txt","lexicon.txt"]:
        p=os.path.join(model_dir,f); assert os.path.isfile(p),f"TTS missing:{p}"
        print(f"  \u2705 TTS {f} ({os.path.getsize(p)/1024/1024:.1f} MB)")
    for f in ["dict/jieba.dict.utf8","dict/hmm_model.utf8","dict/idf.utf8","dict/stop_words.utf8","dict/user.dict.utf8"]:
        p=os.path.join(model_dir,f); assert os.path.isfile(p),f"TTS dict missing:{p}"
    print("OK TTS files verified (VITS Melo)")
    try:
        import sherpa_onnx
        tts_config = sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(
                vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                    tokenizer=model_dir+"/tokens.txt",
                    model=model_dir+"/model.onnx",
                    lexicon=model_dir+"/lexicon.txt",
                    dict_dir=model_dir+"/dict",
                ), num_threads=2))
        tts = sherpa_onnx.OfflineTts(tts_config)
        audio = tts.generate("\u4f60\u597d\uff0c\u6b22\u8fce\u4f7f\u7528\u5170\u5fc3\u3002", sid=0, speed=1.0)
        assert audio.samples is not None and len(audio.samples)>500, f"short: {len(audio.samples)}"
        print(f"  \U0001f3a4 Synthesized {len(audio.samples)} samples at {audio.sample_rate}Hz ({len(audio.samples)/audio.sample_rate:.1f}s)")
        print("OK TTS synthesis verified")
    except ImportError:
        print("  \u26a0\ufe0f sherpa-onnx not installed, skip synthesis test")
    except Exception as e:
        print(f"  \u274c TTS synthesis failed: {e}")
        raise

def verify_tts_matcha(model_dir):
    for f in ["model-steps-3.onnx","vocos-22khz-univ.onnx","tokens.txt","lexicon.txt",
              "date.fst","number.fst","phone.fst"]:
        p=os.path.join(model_dir,f); assert os.path.isfile(p),f"TTS missing:{p}"
        print(f"  \u2705 TTS {f}")
    for f in ["dict/jieba.dict.utf8","dict/hmm_model.utf8","dict/idf.utf8",
              "dict/stop_words.utf8","dict/user.dict.utf8"]:
        p=os.path.join(model_dir,f); assert os.path.isfile(p),f"TTS dict missing:{p}"
    print("OK TTS files verified (Matcha Baker)")
    try:
        import sherpa_onnx
        tts_config = sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(
                matcha=sherpa_onnx.OfflineTtsMatchaModelConfig(
                    acoustic_model=model_dir+"/model-steps-3.onnx",
                    vocoder=model_dir+"/vocos-22khz-univ.onnx",
                    tokens=model_dir+"/tokens.txt",
                    lexicon=model_dir+"/lexicon.txt",
                    dict_dir=model_dir+"/dict"),
                num_threads=2),
            rule_fsts=f"{model_dir}/phone.fst,{model_dir}/date.fst,{model_dir}/number.fst",
            max_num_sentences=1)
        tts = sherpa_onnx.OfflineTts(tts_config)
        audio = tts.generate("\u4f60\u597d\uff0c\u6b22\u8fce\u4f7f\u7528\u5170\u5fc3\u3002", sid=0, speed=1.0)
        assert audio.samples is not None and len(audio.samples)>500, f"short: {len(audio.samples)}"
        print(f"  \U0001f3a4 Synthesized {len(audio.samples)} samples at {audio.sample_rate}Hz ({len(audio.samples)/audio.sample_rate:.1f}s)")
        print("OK TTS synthesis verified")
    except ImportError:
        print("  \u26a0\ufe0f sherpa-onnx not installed, skip synthesis test")
    except Exception as e:
        print(f"  \u274c TTS synthesis failed: {e}")
        raise

def main():
    parser = argparse.ArgumentParser(description="CI verify ASR/TTS models")
    parser.add_argument("--asr-dir", default=None)
    parser.add_argument("--tts-dir", default=None)
    parser.add_argument("--variant", choices=["melo","matcha"], default="melo")
    parser.add_argument("--download", action="store_true")
    parser.add_argument("--output-dir", default="/tmp/ci-voice-models")
    args = parser.parse_args()
    workdir = args.output_dir; os.makedirs(workdir, exist_ok=True)

    # ASR
    print("="*60+" ASR "+("="*54))
    asr_url="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23.tar.bz2"
    asr_archive=os.path.join(workdir,"asr.tar.bz2"); asr_dir=workdir+"/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
    if not os.path.exists(asr_archive):
        try: download(asr_url, asr_archive)
        except: pass
    if args.asr_dir: asr_dir=args.asr_dir
    else:
        with tarfile.open(asr_archive,"r:bz2") as tar: tar.extractall(workdir)
    assert os.path.isdir(asr_dir), f"ASR dir not found: {asr_dir}"
    verify_asr(asr_dir)

    # TTS
    print("\n"+"="*60+f" TTS ({args.variant}) "+("="*52))
    if args.tts_dir: tts_dir=args.tts_dir
    elif args.download:
        if args.variant=="melo":
            tts_dir=workdir+"/vits-melo-tts-zh_en"; os.makedirs(tts_dir,exist_ok=True)
            tts_tar="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2"
            tts_archive=os.path.join(workdir,"tts-melo.tar.bz2")
            if not os.path.exists(tts_archive):
                try: download(tts_tar,tts_archive)
                except: pass
            with tarfile.open(tts_archive,"r:bz2") as tar: tar.extractall(workdir)
            for entry in os.listdir(workdir):
                src=os.path.join(workdir,entry)
                if os.path.isdir(src) and ("melo" in entry.lower() or "vits" in entry.lower()):
                    if not os.path.isdir(tts_dir): os.rename(src,tts_dir); break
            assert os.path.isdir(tts_dir),f"TTS dir not found:{tts_dir}"
            verify_tts_melo(tts_dir)
        else:
            tts_dir=workdir+"/matcha-icefall-zh-baker"; os.makedirs(tts_dir,exist_ok=True)
            tts_tar="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/matcha-icefall-zh-baker.tar.bz2"
            vocoder_url="https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/vocos-22khz-univ.onnx"
            tts_archive=os.path.join(workdir,"tts-matcha.tar.bz2")
            if not os.path.exists(tts_archive):
                try: download(tts_tar,tts_archive)
                except: pass
            with tarfile.open(tts_archive,"r:bz2") as tar: tar.extractall(workdir)
            voc_dest=os.path.join(tts_dir,"vocos-22khz-univ.onnx")
            if not os.path.exists(voc_dest):
                try: download(vocoder_url,voc_dest)
                except: pass
            assert os.path.isdir(tts_dir),f"TTS dir not found:{tts_dir}"
            verify_tts_matcha(tts_dir)
    else:
        if args.variant=="melo": verify_tts_melo(os.path.join(workdir,"vits-melo-tts-zh_en"))
        else: verify_tts_matcha(os.path.join(workdir,"matcha-icefall-zh-baker"))

    print("\nOK All CI voice verification passed")

if __name__=="__main__": main()
