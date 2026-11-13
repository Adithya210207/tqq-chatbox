"""Run every test conversation on each model and save the replies.

    py eval\\run_compare.py --sister Nino Nino_ryuu_v1 nino-base
    (no --sister = Miku; tests come from eval/<sister>_test_100.py)

Fair setup: same messages, same Modelfile settings, and the same random seed
per test for both models. Models run one after another (only one fits in
memory). Results go to eval/results_<model>.jsonl; re-running resumes.
"""
import argparse
import json
import sys
import time
from pathlib import Path

import requests

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sister_tests

URL = "http://localhost:11434/api/chat"
HERE = Path(__file__).resolve().parent


def run(model, TESTS):
    out = HERE / f"results_{model}.jsonl"
    done = {json.loads(l)["n"] for l in open(out, encoding="utf-8")} if out.exists() else set()
    t0 = time.time()
    for n, (opener, follow) in enumerate(TESTS, 1):
        if n in done:
            continue
        msgs, replies = [], []
        for line in [opener] + ([follow] if follow else []):
            msgs.append({"role": "user", "content": line})
            r = requests.post(URL, timeout=600, json={
                "model": model, "messages": msgs, "stream": False, "keep_alive": "15m",
                "options": {"seed": 1000 + n}}).json()
            reply = r["message"]["content"].strip()
            msgs.append({"role": "assistant", "content": reply})
            replies.append(reply)
        with open(out, "a", encoding="utf-8") as f:
            f.write(json.dumps({"n": n, "model": model, "turns": [opener] + ([follow] if follow else []),
                                "replies": replies}, ensure_ascii=False) + "\n")
        print(f"{model} {n}/100  ({time.time() - t0:.0f}s)", flush=True)
    requests.post("http://localhost:11434/api/generate", json={"model": model, "keep_alive": 0})


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--sister", default="Miku")
    ap.add_argument("models", nargs="+")
    a = ap.parse_args()
    tests = sister_tests.load(a.sister).TESTS
    for m in a.models:
        run(m, tests)
    print("ALL DONE", flush=True)
