"""
STEP 1 — Build clean canon data from the anime transcript.  (Fix A + Fix C)

Run:   py step1_build_canon.py

For every sister it writes to  output/<Sister>/ :
  canon_turns.jsonl      every line she says, with captions merged into full
                         turns, plus the scene lines that came before it
                         (step 2 uses this to write "what the user said").
  canon_multiturn.jsonl  real anime scenes cut into short back-and-forth
                         chats (6-12 messages) where she is the assistant.

No AI is used here. It is fast and gives the same result every time.
"""
from collections import Counter

from common import (GROUP_SPEAKERS, SISTERS, build_scenes, is_filler,
                    load_stage_prompts, sister_dir, system_prompt, write_jsonl)

CONTEXT_TURNS = 6          # how many earlier lines to keep as context
MAX_WINDOW_MESSAGES = 12   # longest multi-turn chunk (user+assistant messages)
MIN_ASSISTANT_TURNS = 2    # a chunk must have at least this many replies from her
MAX_USER_LINES = 5         # more lines than this from others in a row = she left the talk
KEEP_USER_LINES = 3        # ...and we keep only the last few of them as the opener


def build_turn_records(sister, scenes):
    rows = []
    for scene in scenes:
        turns = scene["turns"]
        for i, turn in enumerate(turns):
            if turn["speaker"] != sister:
                continue
            context = [{"speaker": t["speaker"], "text": t["text"]}
                       for t in turns[max(0, i - CONTEXT_TURNS):i]]
            prev = turns[i - 1] if i > 0 else None
            partner = prev["speaker"] if prev and prev["speaker"] != sister else None
            if partner in GROUP_SPEAKERS:
                partner = None
            rows.append({
                "id": f"{sister.lower()}_canon_{len(rows) + 1:04d}",
                "scene_id": scene["scene_id"],
                "episode": scene["episode"],
                "timestamp": turn["start"],
                "timeline_stage": scene["timeline_stage"],
                "partner": partner,
                "context": context,
                "reply": turn["text"],
                "captions_merged": turn["captions"],
                "is_filler": is_filler(turn["text"]),
            })
    return rows


def scene_to_messages(sister, scene):
    """Turn one scene into alternating user/assistant messages.
    Each message keeps a list of (speaker, text) lines until it is rendered."""
    msgs = []
    for t in scene["turns"]:
        role = "assistant" if t["speaker"] == sister else "user"
        if msgs and msgs[-1]["role"] == role:
            # Two people talking in a row both count as "the user side".
            msgs[-1]["lines"].append((t["speaker"], t["text"]))
        else:
            msgs.append({"role": role, "lines": [(t["speaker"], t["text"])]})
    return msgs


def split_on_long_gaps(msgs):
    """If she stays silent while others say many lines, she has left the
    conversation: start a new chunk there, keeping only the last few lines."""
    segments, current = [], []
    for m in msgs:
        if m["role"] == "user" and len(m["lines"]) > MAX_USER_LINES:
            if current:
                segments.append(current)
            current = [{"role": "user", "lines": m["lines"][-KEEP_USER_LINES:]}]
        else:
            current.append(m)
    if current:
        segments.append(current)
    return segments


def render_chunk(chunk):
    """Name the speakers only when more than one other person is talking."""
    others = Counter(sp for m in chunk if m["role"] == "user" for sp, _ in m["lines"])
    named = len(others) > 1
    out = []
    for m in chunk:
        if m["role"] == "assistant":
            text = " ".join(t for _, t in m["lines"])
        else:
            text = "\n".join(f"{sp}: {t}" if named else t for sp, t in m["lines"])
        out.append({"role": m["role"], "content": text})
    names = [n for n, _ in others.most_common(3) if n not in GROUP_SPEAKERS]
    if not names:
        partner = "your sisters"
    elif len(names) == 1:
        partner = names[0]
    else:
        partner = ", ".join(names[:-1]) + " and " + names[-1]
    return out, partner


def cut_windows(msgs):
    """Cut a long scene into chunks that start with user and end with assistant."""
    while msgs and msgs[0]["role"] != "user":
        msgs = msgs[1:]
    windows, start = [], 0
    while start < len(msgs):
        end = min(start + MAX_WINDOW_MESSAGES, len(msgs))
        while end > start and msgs[end - 1]["role"] != "assistant":
            end -= 1
        if end <= start:
            break
        chunk = msgs[start:end]
        if sum(m["role"] == "assistant" for m in chunk) >= MIN_ASSISTANT_TURNS:
            windows.append(chunk)
        start = end
        while start < len(msgs) and msgs[start]["role"] != "user":
            start += 1
    return windows


def build_multiturn(sister, scenes, prompts):
    rows = []
    for scene in scenes:
        if not any(t["speaker"] == sister for t in scene["turns"]):
            continue
        msgs = scene_to_messages(sister, scene)
        chunks = [c for seg in split_on_long_gaps(msgs) for c in cut_windows(seg)]
        for chunk in chunks:
            messages, partner = render_chunk(chunk)
            sys = system_prompt(sister, scene["timeline_stage"], partner, prompts)
            rows.append({
                "id": f"{sister.lower()}_scene_{len(rows) + 1:04d}",
                "source": "canon_multiturn",
                "scene_id": scene["scene_id"],
                "episode": scene["episode"],
                "timeline_stage": scene["timeline_stage"],
                "messages": [{"role": "system", "content": sys}] + messages,
            })
    return rows


def main():
    scenes = build_scenes()
    total_turns = sum(len(s["turns"]) for s in scenes)
    print(f"Scenes: {len(scenes)} | merged turns: {total_turns}\n")
    print(f"{'Sister':8} {'lines':>6} {'avg words':>10} {'filler':>7} {'scene chats':>12}")
    for sister in SISTERS:
        prompts = load_stage_prompts(sister)
        turns = build_turn_records(sister, scenes)
        multi = build_multiturn(sister, scenes, prompts)
        out = sister_dir(sister)
        write_jsonl(out / "canon_turns.jsonl", turns)
        write_jsonl(out / "canon_multiturn.jsonl", multi)
        avg = sum(len(t["reply"].split()) for t in turns) / max(1, len(turns))
        filler = sum(t["is_filler"] for t in turns)
        print(f"{sister:8} {len(turns):>6} {avg:>10.1f} {filler:>7} {len(multi):>12}")
    print("\nWritten to v2_pipeline/output/<Sister>/")


if __name__ == "__main__":
    main()
