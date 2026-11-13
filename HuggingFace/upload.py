"""Uploads the five sisters to Hugging Face (public model repos <user>/<Sister>_ryuu_v1-GGUF).
Log in first, yourself:  hf auth login     Then:  py upload.py [--small-only] [Sister ...]   (safe to re-run: it resumes)

Network note (2026-11-13): on this connection about half of the requests to huggingface.co are reset
("WinError 10054") and the Xet storage server didn't answer at all, so every step retries, and Xet is
switched off (classic large-file upload instead). The .gguf is hard-linked into each card folder (no copy).
"""
import os, sys, time
os.environ.setdefault("HF_HUB_DISABLE_XET", "1")          # must be set before importing huggingface_hub
from huggingface_hub import HfApi

HERE = os.path.dirname(os.path.abspath(__file__))
MODELS = os.path.join(os.path.dirname(HERE), "Ryuu_v1 models")
args = [a for a in sys.argv[1:] if not a.startswith("--")]
SMALL_ONLY = "--small-only" in sys.argv
SISTERS = args or ["Miku", "Ichika", "Nino", "Yotsuba", "Itsuki"]
api = HfApi()


def retry(what, fn, tries=40):
    for k in range(1, tries + 1):
        try:
            return fn()
        except Exception as e:
            msg = str(e).splitlines()[0][:120]
            if k == tries:
                raise
            wait = min(60, 3 * k)
            print(f"  {what}: try {k} failed ({msg}); retrying in {wait}s", flush=True)
            time.sleep(wait)


user = retry("login check", lambda: api.whoami()["name"])
print("logged in as:", user, flush=True)
for name in SISTERS:
    folder = os.path.join(HERE, f"{name}_ryuu_v1-GGUF")
    repo = f"{user}/{name}_ryuu_v1-GGUF"
    retry("create repo", lambda: api.create_repo(repo, repo_type="model", private=False, exist_ok=True))
    print(f"{repo}: page files ...", flush=True)
    retry("page files", lambda: api.upload_folder(repo_id=repo, repo_type="model", folder_path=folder,
                                                  allow_patterns=["README.md", "Modelfile", "portrait.jpg"],
                                                  commit_message=f"{name}: model card, Modelfile and portrait"))
    if SMALL_ONLY:
        print(f"OK (small files) https://huggingface.co/{repo}", flush=True)
        continue
    gguf = os.path.join(folder, f"{name}_ryuu_v1.gguf")
    if not os.path.exists(gguf):
        os.link(os.path.join(MODELS, f"{name}_ryuu_v1.gguf"), gguf)     # hard link, no copy
    print(f"{repo}: uploading {name}_ryuu_v1.gguf (8.5 GB) ...", flush=True)
    retry("model file", lambda: api.upload_large_folder(repo_id=repo, repo_type="model", folder_path=folder,
                                                        allow_patterns=["*.gguf"]), tries=20)
    print(f"DONE {repo}  https://huggingface.co/{repo}", flush=True)
print("ALL DONE", flush=True)
