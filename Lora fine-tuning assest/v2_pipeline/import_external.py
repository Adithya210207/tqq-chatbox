"""
Import conversations written by ChatGPT / Gemini (from prompts/PROMPT_*.md).

1. Paste each of their replies into one text file, e.g. output/Miku/raw_chatgpt.txt
   (just keep appending; code-block fences and extra sentences are fine).
2. Run:
     py import_external.py --sister Miku --source chatgpt --file output\\Miku\\raw_chatgpt.txt
     py import_external.py --sister Miku --source gemini  --file output\\Miku\\raw_gemini.txt

Every chat goes through the same automatic checks as step 3. Good ones are saved
to output/<Sister>/external_<source>.jsonl (step 5 picks them up), rejected ones
to external_<source>_rejected.jsonl with the reason. Re-running is safe: the
file is rebuilt from the raw text each time.
"""
import argparse
import json
import re

from common import sister_dir, write_jsonl
from step3_synthetic import check

STAGES = {"season_1", "season_2", "movie"}
PARTNERS = {"Fuutarou", "a classmate", "a new friend"}

# The subtitles use American spelling; ChatGPT sometimes writes British.
US_SPELLING = {"practising": "practicing", "practised": "practiced", "favourite": "favorite",
               "apologised": "apologized", "recognise": "recognize", "memorising": "memorizing",
               "rumours": "rumors", "rumour": "rumor", "organised": "organized", "realise": "realize",
               "colour": "color", "behaviour": "behavior"}


def apply_fixes(lines, fix):
    """Hand-review fixes from external_fixes.json. Indexes refer to the
    original `lines` list as pasted."""
    lines = list(lines)
    for i, text in (fix.get("replace") or {}).items():
        lines[int(i)] = text
    for i in fix.get("strip_futaro") or []:
        lines[i] = re.sub(r",\s*Futaro(?=[.?!])", "", lines[i])
    if fix.get("drop_first_last"):      # roles were swapped: she spoke first and last
        lines = lines[1:-1]
    elif fix.get("drop_first"):         # roles swapped, odd number of lines
        lines = lines[1:]
    return lines


def parse(raw):
    """Pull every {...} JSON object out of the pasted text."""
    found, bad = [], 0
    for line in raw.splitlines():
        line = line.strip().rstrip(",")
        if not line.startswith("{"):
            continue
        # chat UIs sometimes turn quotes "smart"; JSON needs straight ones
        fixed = line.replace("“", '"').replace("”", '"')
        try:
            found.append(json.loads(fixed))
        except json.JSONDecodeError:
            bad += 1
    return found, bad


def to_messages(lines):
    msgs = []
    for j, t in enumerate(lines):
        t = str(t).strip()
        t = (t.replace("’", "'").replace("‘", "'").replace("…", "...")
              .replace("“", '"').replace("”", '"'))
        for uk, us in US_SPELLING.items():
            t = re.sub(rf"\b{uk}\b", us, t)
            t = re.sub(rf"\b{uk.capitalize()}\b", us.capitalize(), t)
        role = "user" if j % 2 == 0 else "assistant"
        if role == "assistant" and t.startswith("...") and len(t) > 3:
            t = t[3:].lstrip()
            t = t[:1].upper() + t[1:]
        msgs.append({"role": role, "content": t})
    return msgs


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--sister", default="Miku")
    p.add_argument("--source", required=True, help="chatgpt or gemini (used in ids and file names)")
    p.add_argument("--file", required=True, help="text file with their pasted replies")
    a = p.parse_args()
    src = a.source.lower()

    raw = open(a.file, encoding="utf-8").read()
    objs, bad_json = parse(raw)
    fixes_path = sister_dir(a.sister) / "external_fixes.json"
    fixes = json.loads(fixes_path.read_text(encoding="utf-8")) if fixes_path.exists() else {}
    keep, reject, seen = [], [], set()
    for o in objs:
        n = o.get("n")
        cid = f"{a.sister.lower()}_{src}_{int(n):03d}" if str(n).isdigit() else None
        why = None
        if cid is None:
            why = "missing number n"
        elif cid in seen:
            why = "duplicate number (kept the first one)"
        elif o.get("stage") not in STAGES:
            why = f"unknown stage {o.get('stage')!r}"
        elif o.get("partner") not in PARTNERS:
            why = f"unknown partner {o.get('partner')!r}"
        elif not isinstance(o.get("lines"), list) or len(o["lines"]) < 4:
            why = "too few lines"
        fix = fixes.get(cid or "", {})
        if not why and fix.get("reject"):
            why = "review: " + fix["reject"]
        lines = apply_fixes(o.get("lines") or [], fix) if not why else (o.get("lines") or [])
        msgs = to_messages(lines)
        if not why:
            why = check(msgs, a.sister, o["partner"])
        row = {"id": cid, "source": src, "timeline_stage": o.get("stage"), "partner": o.get("partner"),
               "topic": o.get("topic", ""), "messages": msgs, "generator": src}
        if why:
            row["rejected_because"] = why
            reject.append(row)
        else:
            keep.append(row)
        if cid:
            seen.add(cid)

    folder = sister_dir(a.sister)
    write_jsonl(folder / f"external_{src}.jsonl", keep)
    write_jsonl(folder / f"external_{src}_rejected.jsonl", reject)
    print(f"{a.sister} / {src}: found {len(objs)} chats, kept {len(keep)}, rejected {len(reject)}"
          + (f", {bad_json} lines were broken JSON" if bad_json else ""))
    for r in reject:
        print(f"  #{r['id'] or '?'}: {r['rejected_because']}")


if __name__ == "__main__":
    main()
