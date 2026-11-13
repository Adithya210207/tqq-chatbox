"""
Reverse prompts written by Claude in chat (2026-11-10), replacing the local-AI
step 2 output. For every real anime line of hers, Claude wrote the message the
scene's partner says right before it. Her line is never changed.

Each part file in reverse_claude/ holds MSGS = {line number: "user message"}.
None means "skip this line" (e.g. she is talking to someone else, or the line
makes no sense without seeing the scene).

    py reverse_claude_tools.py --sister Miku

compiles all parts into output/<Sister>/reverse_claude.jsonl, which step 5
uses instead of reverse_prompts.jsonl.
"""
import argparse
import importlib.util
import json
from pathlib import Path

from common import read_jsonl, sister_dir, write_jsonl

HERE = Path(__file__).resolve().parent


def load_parts(sister):
    msgs, extra = {}, {}
    for path in sorted((HERE / "reverse_claude").glob(f"{sister.lower()}_rp_*.py")):
        spec = importlib.util.spec_from_file_location(path.stem, path)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        for n, text in mod.MSGS.items():
            if n in msgs:
                raise SystemExit(f"line {n} appears twice ({path.name})")
            msgs[n] = text
        # EXTRA = {"<other>_canon_0123": ("Partner", "message")}: her real lines that the
        # subtitles label as another sister (added for Ichika, 2026-11-12)
        extra.update(getattr(mod, "EXTRA", {}))
    return msgs, extra


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--sister", default="Miku")
    a = p.parse_args()
    folder = sister_dir(a.sister)
    turns = read_jsonl(folder / "canon_turns.jsonl")
    msgs, extra = load_parts(a.sister)
    rows, skipped, missing = [], 0, []
    for t in turns:
        n = int(t["id"].rsplit("_", 1)[1])
        if n not in msgs:
            missing.append(n)
            continue
        if msgs[n] is None:
            skipped += 1
            continue
        # ("Fuutarou", "message") overrides a wrong or missing partner label
        partner, text = msgs[n] if isinstance(msgs[n], tuple) else (t["partner"], msgs[n])
        rows.append({"canon_id": t["id"], "scene_id": t["scene_id"], "timeline_stage": t["timeline_stage"],
                     "partner": partner, "user_messages": [text.strip()], "reply": t["reply"],
                     "generator": "claude (hand-written in chat)"})
    for cid, (partner, text) in extra.items():
        other = cid.split("_")[0].capitalize()
        t = next(r for r in read_jsonl(sister_dir(other) / "canon_turns.jsonl") if r["id"] == cid)
        rows.append({"canon_id": cid, "scene_id": t["scene_id"], "timeline_stage": t["timeline_stage"],
                     "partner": partner, "user_messages": [text.strip()], "reply": t["reply"],
                     "generator": "claude (hand-written in chat)"})
    write_jsonl(folder / "reverse_claude.jsonl", rows)
    print(f"{a.sister}: {len(rows)} written ({len(extra)} from mislabeled lines), {skipped} skipped on purpose, "
          f"{len(missing)} not done yet")
    if missing:
        print("  first missing:", missing[:10])


if __name__ == "__main__":
    main()
