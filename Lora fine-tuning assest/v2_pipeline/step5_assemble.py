"""
STEP 5 — Combine everything into the final training files.

    py step5_assemble.py                 # all sisters
    py step5_assemble.py --sister Miku

Writes output/<Sister>/final/train.jsonl and val.jsonl.
These two files are what you upload to Google Colab.

What goes in:
  1. canon_multiturn   real anime scenes as short chats       (always)
  2. reverse prompts   her real line + AI-written user message (from step 2)
     ...or, for lines step 2 hasn't covered yet, the real line that came
        before it in the anime (weaker, but better than nothing)
  3. synthetic         AI-written everyday chats (from step 3), minus any
                       you rejected in step 4

About 10% is held back as "validation": the model never trains on it, and
we use it to check it is learning her style rather than memorising lines.
"""
import argparse
import hashlib
import json
import random

from common import (ALLOWED_NAMES, SISTERS, futaro_name_forms, is_filler, load_stage_prompts,
                    read_jsonl, sister_dir, system_prompt, write_jsonl)
from step2_reverse_prompts import looks_bad

import re

# Topics a sister's data must not contain at all (any line, anyone speaking).
# Yotsuba: leave out the wedding epilogue.
SKIP_TOPICS = {
    # (The Kyoto-girl secret was skipped in v1; the user put it back for v2 on 2026-11-11.)
    "Yotsuba": re.compile(r"(?i)wedding|i'm your bride"),
}

VAL_PERCENT = 10
MAX_FILLER_SHARE = 0.10   # at most 10% of single-line examples may be "Huh?"-style


def is_val(key: str) -> bool:
    """Same scene always lands on the same side, so nothing leaks into validation."""
    return int(hashlib.md5(key.encode()).hexdigest(), 16) % 100 < VAL_PERCENT


def single(sys, user, reply):
    return [{"role": "system", "content": sys},
            {"role": "user", "content": user},
            {"role": "assistant", "content": reply}]


def apply_fix(messages, fix):
    """Hand-review edits from synthetic_fixes.json (the original file stays untouched)."""
    msgs = [dict(m) for m in messages]
    for i, text in (fix.get("replace") or {}).items():
        msgs[int(i)]["content"] = text
    if fix.get("strip_dash"):
        for m in msgs:
            m["content"] = m["content"].lstrip("-–— ").strip()
    if fix.get("truncate"):
        msgs = msgs[:fix["truncate"]]
    assert msgs[-1]["role"] == "assistant", "a fixed chat must still end with her"
    return msgs


def assemble(sister, only_approved):
    folder = sister_dir(sister)
    prompts = load_stage_prompts(sister)
    rng = random.Random(7)
    examples = []   # (split_key, source, messages)

    # 1. Real scenes
    #    (canon_scene_skip.json, if present, is {scene chat id: N}. The subtitles
    #    label another sister's lines as hers, she is in disguise, etc.
    #    N = 0 leaves the chat out; N > 0 keeps only its first N messages.)
    skip_path = folder / "canon_scene_skip.json"
    scene_skip = json.loads(skip_path.read_text(encoding="utf-8")) if skip_path.exists() else {}
    for r in read_jsonl(folder / "canon_multiturn.jsonl"):
        keep = scene_skip.get(r["id"])
        if keep == 0:
            continue
        msgs = r["messages"][:keep] if keep else r["messages"]
        assert msgs[-1]["role"] == "assistant", f'{r["id"]}: a trimmed chat must end with her'
        examples.append((r["scene_id"], "canon_multiturn", msgs))

    # 2. Single lines: reverse prompts where we have them
    turns = {r["id"]: r for r in read_jsonl(folder / "canon_turns.jsonl")}
    covered = set()
    singles = []
    # Claude's hand-written reverse prompts replace the local-AI ones when present.
    claude_rp = folder / "reverse_claude.jsonl"
    rp_path, rp_src = (claude_rp, "reverse_claude") if claude_rp.exists() else (folder / "reverse_prompts.jsonl", "reverse_prompt")
    for r in read_jsonl(rp_path):
        covered.add(r["canon_id"])
        sys = system_prompt(sister, r["timeline_stage"], r["partner"] or "a friend", prompts)
        for u in r["user_messages"]:
            # the copy-check is for local-AI output; hand-written ones may echo her words on purpose
            if rp_src == "reverse_prompt" and looks_bad(u, r["reply"]):
                continue
            singles.append((r["scene_id"], rp_src, single(sys, u, r["reply"]), is_filler(r["reply"])))
    #    ...and the anime's previous line for the rest (not when Claude skipped
    #    a line on purpose: skipped lines stay out)
    for cid, t in turns.items():
        if rp_src == "reverse_claude":
            break
        if cid in covered or not t["partner"] or not t["context"]:
            continue
        sys = system_prompt(sister, t["timeline_stage"], t["partner"], prompts)
        singles.append((t["scene_id"], "canon_pair", single(sys, t["context"][-1]["text"], t["reply"]), t["is_filler"]))

    fillers = [s for s in singles if s[3]]
    normal = [s for s in singles if not s[3]]
    max_fill = int(len(normal) * MAX_FILLER_SHARE / (1 - MAX_FILLER_SHARE))
    rng.shuffle(fillers)
    for key, src, msgs, _ in normal + fillers[:max_fill]:
        examples.append((key, src, msgs))

    # 3. Synthetic chats
    review_path = folder / "synthetic_review.json"
    decisions = json.loads(review_path.read_text(encoding="utf-8")) if review_path.exists() else {}
    fixes_path = folder / "synthetic_fixes.json"
    fixes = json.loads(fixes_path.read_text(encoding="utf-8")) if fixes_path.exists() else {}
    for c in read_jsonl(folder / "synthetic.jsonl"):
        verdict = decisions.get(c["id"])
        if verdict is False or (only_approved and verdict is not True):
            continue
        msgs = apply_fix(c["messages"], fixes[c["id"]]) if c["id"] in fixes else c["messages"]
        partner = "Fuutarou" if c["partner"] == "Fuutarou" else c["partner"]
        sys = system_prompt(sister, c["timeline_stage"], partner, prompts)
        examples.append((c["id"], "synthetic", [{"role": "system", "content": sys}] + msgs))

    # 4. Gold chats (hand-written by Claude in chat, checked by you)
    for c in read_jsonl(folder / "gold.jsonl"):
        sys = system_prompt(sister, c["timeline_stage"], c["partner"], prompts)
        examples.append((c["id"], "gold", [{"role": "system", "content": sys}] + c["messages"]))

    # 5. Chats from ChatGPT / Gemini (import_external.py), minus any reviewed as bad
    for path in sorted(folder.glob("external_*.jsonl")):
        if path.stem.endswith("_rejected"):
            continue
        for c in read_jsonl(path):
            if decisions.get(c["id"]) is False:
                continue
            sys = system_prompt(sister, c["timeline_stage"], c["partner"], prompts)
            examples.append((c["id"], c["source"], [{"role": "system", "content": sys}] + c["messages"]))

    # Split, dedupe, shuffle
    train, val, seen = [], [], set()
    dropped = 0
    for key, src, msgs in examples:
        # She must only call Futaro by her allowed names (common.ALLOWED_NAMES), and
        # never "Kintaro" (Futaro's disguise in canon): drop any chat that breaks this.
        her = [m["content"] for m in msgs if m["role"] == "assistant"]
        if any("kintaro" in h.lower() or any(n not in ALLOWED_NAMES[sister] for n in futaro_name_forms(h))
               for h in her):
            dropped += 1
            continue
        skip = SKIP_TOPICS.get(sister)
        if skip and any(skip.search(m["content"]) for m in msgs[1:]):
            dropped += 1
            continue
        sig = json.dumps(msgs[1:], ensure_ascii=False)
        if sig in seen:
            continue
        seen.add(sig)
        (val if is_val(key) else train).append({"source": src, "messages": msgs})
    rng.shuffle(train)
    rng.shuffle(val)

    out = folder / "final"
    write_jsonl(out / "train.jsonl", train)
    write_jsonl(out / "val.jsonl", val)

    counts = {}
    for row in train + val:
        counts[row["source"]] = counts.get(row["source"], 0) + 1
    words = sum(len(m["content"].split()) for row in train for m in row["messages"] if m["role"] == "assistant")
    print(f"{sister:8} train={len(train):5}  val={len(val):4}  her words in train={words:6}  | "
          + ", ".join(f"{k}={v}" for k, v in sorted(counts.items()))
          + f"  | dropped (wrong name, Kintaro, skipped topics): {dropped}")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--sister", default="all")
    p.add_argument("--only-approved", action="store_true",
                   help="use only synthetic chats you marked 'y' in step 4")
    args = p.parse_args()
    for s in (SISTERS if args.sister.lower() == "all" else [args.sister.capitalize()]):
        assemble(s, args.only_approved)
    print("\nUpload output/<Sister>/final/train.jsonl and val.jsonl to Colab.")


if __name__ == "__main__":
    main()
