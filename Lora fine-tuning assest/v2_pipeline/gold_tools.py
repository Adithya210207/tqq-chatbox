"""Adds hand-written "gold" chats to output/<Sister>/gold.jsonl.

Each batch file in gold/ calls add_batch(). Running a batch twice is safe:
chats whose id already exists are skipped. Every chat goes through the same
automatic checks as step 3, and the batch is refused if any chat fails.
"""
from common import read_jsonl, sister_dir, write_jsonl
from step3_synthetic import check

PARTNERS = {"Fuutarou", "a classmate", "a new friend"}
STAGES = {"season_1", "season_2", "movie"}


def add_batch(sister, batch_no, chats):
    rows, problems = [], []
    for i, (stage, partner, topic, lines) in enumerate(chats, 1):
        cid = f"{sister.lower()}_gold_b{batch_no:02d}_{i:02d}"
        if stage not in STAGES or partner not in PARTNERS:
            problems.append(f"{cid}: bad stage/partner")
            continue
        msgs = [{"role": "user" if j % 2 == 0 else "assistant", "content": t.strip()}
                for j, t in enumerate(lines)]
        for m in msgs:
            # The anime subtitles almost never START her line with "..." (2 of
            # 732 lines). Strip it so the LoRA doesn't learn that habit from us.
            t = m["content"]
            if m["role"] == "assistant" and t.startswith("...") and len(t) > 3:
                t = t[3:].lstrip()
                m["content"] = t[:1].upper() + t[1:]
        why = check(msgs, sister, partner)
        if why:
            problems.append(f"{cid}: {why}")
        rows.append({"id": cid, "source": "gold", "timeline_stage": stage, "partner": partner,
                     "topic": topic, "messages": msgs, "generator": "claude-opus-5-5 (hand-written in chat)"})
    if problems:
        print("Not saved, fix these first:\n  " + "\n  ".join(problems))
        return
    path = sister_dir(sister) / "gold.jsonl"
    have = read_jsonl(path)
    ids = {r["id"] for r in have}
    new = [r for r in rows if r["id"] not in ids]
    write_jsonl(path, have + new)
    print(f"{sister}: added {len(new)} gold chats (batch {batch_no}), total now {len(have) + len(new)}")
