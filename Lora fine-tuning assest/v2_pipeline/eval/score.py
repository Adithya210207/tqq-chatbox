r"""Score the 100-test comparison.

    py eval\score.py auto    --sister Nino Nino_ryuu_v1 nino-base   # automatic checks per model
    py eval\score.py blind   --sister Nino Nino_ryuu_v1 nino-base   # writes blind_sheet.txt + blind_key.json
    py eval\score.py unblind                                       # reads blind_votes.json, prints the result
    (no --sister = Miku; unblind reads the sister and models from blind_key.json)
"""
import argparse
import collections
import json
import random
import re
import statistics
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import sister_tests  # noqa: E402  (also puts the pipeline folder on sys.path)
from common import ALLOWED_NAMES, futaro_name_forms  # noqa: E402

ASSISTANT = ["as an ai", "language model", "i'm an ai", "i am an ai", "assistant", "how can i help",
             "happy to help", "here are", "here's a", "certainly", "of course! ", "i'd be glad"]
SLANG = re.compile(r"\b(lol|lmao|omg|haha+|hehe+|gotcha)\b", re.I)
ACTION = re.compile(r"\*[^*]+\*|\([^)]*\)")
EMOJI = re.compile("[\U0001F000-\U0001FAFF☀-➿]")


def load(model):
    return {r["n"]: r for r in (json.loads(l) for l in open(HERE / f"results_{model}.jsonl", encoding="utf-8"))}


def auto(sister, models):
    traps = {n: re.compile(rx) for n, rx in sister_tests.load(sister).TRAP_CHECKS.items()}
    allowed = ALLOWED_NAMES[sister]
    for m in models:
        rows = load(m)
        replies = [x for r in rows.values() for x in r["replies"]]
        words = [len(x.split()) for x in replies]
        flags = collections.Counter()
        for n, r in rows.items():
            for x in r["replies"]:
                lx = x.lower()
                flags["assistant-like"] += any(a in lx for a in ASSISTANT)
                flags["slang"] += bool(SLANG.search(x))
                flags["*actions*/(asides)"] += bool(ACTION.search(x))
                flags["emoji"] += bool(EMOJI.search(x))
                flags["wrong name for Futaro"] += any(n not in allowed for n in futaro_name_forms(x))
                flags["empty"] += not x.strip()
            if n in traps and traps[n].search(" ".join(r["replies"])):
                flags["did an assistant trap task"] += 1
        openers = collections.Counter(" ".join(x.lower().split()[:3]) for x in replies)
        print(f"\n== {m}: {len(rows)} tests, {len(replies)} replies")
        print(f"   words per reply: mean {statistics.mean(words):.1f}, median {statistics.median(words)}, max {max(words)}")
        print(f"   replies over 40 words: {sum(w > 40 for w in words)}")
        for k in ["assistant-like", "slang", "*actions*/(asides)", "emoji", "wrong name for Futaro",
                  "did an assistant trap task", "empty"]:
            print(f"   {k:28} {flags[k]}")
        print("   most common openers:", openers.most_common(5))


def blind(sister, models):
    a, b = load(models[0]), load(models[1])
    rng = random.Random(20261110)
    key, lines = {}, []
    for n in sorted(a):
        flip = rng.random() < 0.5
        x, y = (b[n], a[n]) if flip else (a[n], b[n])
        key[n] = {"A": x["model"], "B": y["model"]}
        lines.append(f"#{n}")
        for t, turn in enumerate(a[n]["turns"]):
            lines.append(f"  U: {turn}")
            lines.append(f"    A: {x['replies'][t]}")
            lines.append(f"    B: {y['replies'][t]}")
    (HERE / "blind_sheet.txt").write_text("\n".join(lines), encoding="utf-8")
    key["_info"] = {"sister": sister, "models": list(models)}
    (HERE / "blind_key.json").write_text(json.dumps(key, indent=1), encoding="utf-8")
    print("wrote blind_sheet.txt and blind_key.json")


def unblind():
    key = json.loads((HERE / "blind_key.json").read_text(encoding="utf-8"))
    info = key.get("_info", {"sister": "Miku"})
    votes = json.loads((HERE / "blind_votes.json").read_text(encoding="utf-8"))
    tally = collections.Counter()
    by_cat = collections.defaultdict(collections.Counter)
    cats = sister_tests.load(info["sister"]).CATEGORIES
    for n, v in votes.items():
        winner = "tie" if v == "T" else key[n][v]
        tally[winner] += 1
        cat = next(c for lo, hi, c in cats if lo <= int(n) <= hi)
        by_cat[cat][winner] += 1
    print(f"{info['sister']}: {' vs '.join(info.get('models', []))}")
    print("overall:", dict(tally))
    for _, _, c in cats:
        print(f"  {c:12}", dict(by_cat[c]))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["auto", "blind", "unblind"])
    ap.add_argument("--sister", default="Miku")
    ap.add_argument("models", nargs="*")
    a = ap.parse_args()
    if a.mode == "unblind":
        unblind()
    elif len(a.models) != 2:
        sys.exit("Give exactly two model names, e.g.  score.py auto --sister Nino Nino_ryuu_v1 nino-base")
    else:
        {"auto": auto, "blind": blind}[a.mode](a.sister, a.models)
