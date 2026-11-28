#!/usr/bin/env python3
"""
Converts Thonburian Whisper (Thai-trained Whisper by BiodatLab, Mahidol University) to a
whisper.cpp model file that ScamSense can import.

Runs on a normal PC (Windows / macOS / Linux, no GPU needed):

    pip install torch transformers huggingface_hub numpy
    python convert_thonburian.py                     # default: biodatlab/distill-whisper-th-small
    python convert_thonburian.py --repo <hf-repo-id> # another Whisper (transformers) model

Output: ggml-thonburian-f16.bin in the current folder. Copy it to the phone, e.g.
    adb push ggml-thonburian-f16.bin /sdcard/Download/
then ScamSense → ป้องกันสายโทร → ข้อ 3 → โมเดล "ภาษาไทย" → นำเข้าไฟล์โมเดล.
The app shrinks it to q5_1 on the phone (smaller and faster).

The conversion itself is whisper.cpp's own script (models/convert-h5-to-ggml.py, MIT),
pinned to the version ScamSense is built with. Check the model's license on its
Hugging Face page before redistributing the converted file.
"""
import argparse
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

WHISPER_CPP_TAG = "v1.9.3"  # same as third_party/whisper.cpp
CONVERT_URL = f"https://raw.githubusercontent.com/ggml-org/whisper.cpp/{WHISPER_CPP_TAG}/models/convert-h5-to-ggml.py"
MEL_URL = "https://raw.githubusercontent.com/openai/whisper/main/whisper/assets/mel_filters.npz"
# Same multilingual vocabulary as every Whisper small/medium model; used only if the repo lacks vocab.json.
VOCAB_FALLBACK_REPO = "openai/whisper-small"


def fetch(url: str, dest: Path) -> None:
    print(f"  downloading {url}")
    with urllib.request.urlopen(url, timeout=120) as r, open(dest, "wb") as f:
        shutil.copyfileobj(r, f)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--repo", default="biodatlab/distill-whisper-th-small", help="Hugging Face model id")
    ap.add_argument("--out", default="ggml-thonburian-f16.bin", help="output file name")
    args = ap.parse_args()

    try:
        from huggingface_hub import hf_hub_download, snapshot_download
        import torch  # noqa: F401  (needed by the converter)
        import transformers  # noqa: F401
    except ImportError:
        print("Missing packages. Run:  pip install torch transformers huggingface_hub numpy")
        return 1

    work = Path(tempfile.mkdtemp(prefix="thonburian-"))
    try:
        print(f"1/4 Downloading {args.repo} from Hugging Face…")
        model_dir = Path(snapshot_download(
            args.repo, local_dir=work / "model",
            allow_patterns=["*.json", "*.safetensors", "*.bin", "*.txt", "*.model"],
        ))
        if not (model_dir / "vocab.json").exists():
            print("  vocab.json not in repo; using the standard Whisper vocabulary")
            shutil.copy(hf_hub_download(VOCAB_FALLBACK_REPO, "vocab.json"), model_dir / "vocab.json")

        print("2/4 Downloading the whisper.cpp converter and mel filters…")
        convert = work / "convert-h5-to-ggml.py"
        fetch(CONVERT_URL, convert)
        assets = work / "openai-whisper" / "whisper" / "assets"
        assets.mkdir(parents=True)
        fetch(MEL_URL, assets / "mel_filters.npz")

        print("3/4 Converting (a few minutes)…")
        out_dir = work / "out"
        out_dir.mkdir()
        subprocess.run([sys.executable, str(convert), str(model_dir), str(work / "openai-whisper"), str(out_dir)],
                       check=True, stdout=subprocess.DEVNULL)

        print("4/4 Saving…")
        result = Path(args.out).resolve()
        shutil.move(str(out_dir / "ggml-model.bin"), result)
        size_mb = result.stat().st_size / 1e6
        print(f"\nDone: {result} ({size_mb:.0f} MB)")
        print("Copy it to the phone, e.g.:  adb push \"%s\" /sdcard/Download/" % result.name)
        return 0
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
