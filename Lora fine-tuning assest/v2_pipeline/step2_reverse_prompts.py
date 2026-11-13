"""
STEP 2 — Reverse-prompting.  (Fix B)

For every real line a sister says, an AI writes the message someone could
have sent her so that her line is a natural, direct answer.
HER LINE IS NEVER CHANGED — only the "user" side is invented.

Examples:
  py step2_reverse_prompts.py --sister Miku --limit 16          # quick test
  py step2_reverse_prompts.py --sister Miku                     # full run, local Ollama
  py step2_reverse_prompts.py --sister all --backend claude     # all five via Claude API

It saves after every batch, so you can stop it (Ctrl+C) and run the same
command again later — it continues where it left off.
"""
import argparse
import random
import re

from common import (FULL_NAMES, SISTERS, append_jsonl, load_stage_prompts,
                    read_jsonl, sister_dir)
from llm import ask_json, strict_object

FILLER_KEEP_RATE = 0.15   # keep only 15% of "Huh?"/"Yeah." lines


def pick_rows(sister):
    rows = read_jsonl(sister_dir(sister) / "canon_turns.jsonl")
    if not rows:
        raise SystemExit(f"No canon data for {sister}. Run step1_build_canon.py first.")
    rng = random.Random(42)
    return [r for r in rows if not r["is_filler"] or rng.random() < FILLER_KEEP_RATE]


def build_prompt(sister, batch, variants, prompts):
    name = FULL_NAMES[sister]
    stage = batch[0]["timeline_stage"]
    items = []
    for i, r in enumerate(batch, 1):
        ctx = "\n".join(f"   {c['speaker']}: {c['text']}" for c in r["context"][-4:]) or "   (start of scene)"
        who = r["partner"] or "a friend or classmate"
        items.append(f"#{i}  (the message comes from: {who})\n"
                     f"   Earlier in the scene:\n{ctx}\n"
                     f"   {sister}'s line: \"{r['reply']}\"")
    return f"""You are helping build a roleplay chat dataset for {name} from the anime "The Quintessential Quintuplets".

Character card: {prompts[stage]}

Below are real lines {sister} says in the anime. For EACH item, write {variants} different chat message(s) that the named person could send to {sister} so that her line reads as a natural, DIRECT reply to that message.

Rules:
- Each message is 1-3 casual sentences, in that person's voice.
- It must make sense without the anime scene: include whatever detail her line responds to.
- Never copy or paraphrase her line. Never mention "the anime" or "the scene".
- Keep names, facts and relationships consistent with the series.

{chr(10).join(items)}

Return JSON with one entry per item, in the same order."""


def schema(n_items, variants):
    msg_list = {"type": "array", "items": {"type": "string"},
                "minItems": variants, "maxItems": variants}
    return strict_object({"items": {
        "type": "array", "minItems": n_items, "maxItems": n_items,
        "items": strict_object({"item_number": {"type": "integer"},
                                "messages": msg_list})}})


def _words(text):
    return re.findall(r"[a-z']+", text.lower())


def copies_reply(msg, reply, n=4):
    """True if the message repeats any 4 words in a row from her line
    (or her whole line, if it is shorter than that)."""
    r, m = _words(reply), " ".join(_words(msg))
    if not r:
        return False
    if len(r) < n:
        return " ".join(r) in m and len(r) >= 2
    return any(" ".join(r[i:i + n]) in m for i in range(len(r) - n + 1))


def looks_bad(msg, reply):
    m = msg.strip().lower()
    return (len(m) < 3 or copies_reply(msg, reply)
            or "anime" in m or "scene" in m or len(m.split()) > 70)


def run(sister, args):
    rows = pick_rows(sister)
    out_path = sister_dir(sister) / "reverse_prompts.jsonl"
    done = {r["canon_id"] for r in read_jsonl(out_path)}
    todo = [r for r in rows if r["id"] not in done]
    if args.limit:
        todo = todo[:args.limit]
    prompts = load_stage_prompts(sister)
    print(f"\n{sister}: {len(done)} done already, {len(todo)} to go "
          f"({args.backend}, batches of {args.batch})")

    # Keep each batch inside one timeline stage so the character card fits.
    batches, cur = [], []
    for r in todo:
        if cur and (len(cur) == args.batch or r["timeline_stage"] != cur[0]["timeline_stage"]):
            batches.append(cur)
            cur = []
        cur.append(r)
    if cur:
        batches.append(cur)

    for bi, batch in enumerate(batches, 1):
        try:
            data = ask_json(build_prompt(sister, batch, args.variants, prompts),
                            schema(len(batch), args.variants),
                            backend=args.backend, model=args.model, temperature=0.8)
        except RuntimeError as e:
            print(f"  batch {bi}: skipped ({e})")
            continue
        results = data["items"]
        if len(results) != len(batch):
            print(f"  batch {bi}: wrong number of answers, skipped")
            continue
        for r, res in zip(batch, results):
            msgs = [m.strip() for m in res["messages"] if not looks_bad(m, r["reply"])]
            if not msgs:
                continue
            append_jsonl(out_path, {
                "canon_id": r["id"], "scene_id": r["scene_id"],
                "timeline_stage": r["timeline_stage"], "partner": r["partner"],
                "user_messages": msgs, "reply": r["reply"],
                "generator": f"{args.backend}:{args.model or 'default'}",
            })
        print(f"  batch {bi}/{len(batches)} saved")
    print(f"{sister}: finished -> {out_path}")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--sister", default="Miku", help="Ichika, Nino, Miku, Yotsuba, Itsuki or all")
    p.add_argument("--backend", default="ollama", choices=["ollama", "claude"])
    p.add_argument("--model", default=None, help="override the model name")
    p.add_argument("--batch", type=int, default=8, help="lines per AI request")
    p.add_argument("--variants", type=int, default=2, help="user messages per line")
    p.add_argument("--limit", type=int, default=0, help="only do this many lines (for testing)")
    args = p.parse_args()
    for s in (SISTERS if args.sister.lower() == "all" else [args.sister.capitalize()]):
        run(s, args)


if __name__ == "__main__":
    main()
