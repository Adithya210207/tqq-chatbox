"""
STEP 4 — Review the AI-written conversations yourself.

You know these characters better than any AI. This shows each synthetic
conversation and asks you to keep it or throw it away.

    py step4_review.py --sister Miku

Keys:  y = keep    n = reject    s = skip for now    q = save and quit
Your choices are saved in output/<Sister>/synthetic_review.json, so you can
stop anytime and continue later. Rejected chats are left out of training.

What to reject: wrong way of speaking, out-of-character behaviour, sounds like
a customer-service bot, facts that contradict the anime, boring/generic chat.
"""
import argparse
import json

from common import SISTERS, read_jsonl, sister_dir


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--sister", default="Miku")
    p.add_argument("--redo", action="store_true", help="also show ones you already judged")
    args = p.parse_args()
    sister = args.sister.capitalize()
    if sister not in SISTERS:
        raise SystemExit(f"Unknown sister: {args.sister}")

    folder = sister_dir(sister)
    convos = read_jsonl(folder / "synthetic.jsonl")
    review_path = folder / "synthetic_review.json"
    decisions = json.loads(review_path.read_text(encoding="utf-8")) if review_path.exists() else {}

    pending = [c for c in convos if args.redo or c["id"] not in decisions]
    print(f"{len(convos)} conversations, {len(pending)} to review.\n")
    try:
        for i, c in enumerate(pending, 1):
            print("=" * 70)
            print(f"[{i}/{len(pending)}] {c['id']} | {c['timeline_stage']} | with {c['partner']} | {c['topic']}")
            print("-" * 70)
            for m in c["messages"]:
                who = sister.upper() if m["role"] == "assistant" else "USER"
                print(f"{who:>8}: {m['content']}\n")
            while True:
                ans = input("Keep? [y/n/s/q] ").strip().lower()
                if ans in ("y", "n", "s", "q"):
                    break
            if ans == "q":
                break
            if ans in ("y", "n"):
                decisions[c["id"]] = ans == "y"
    finally:
        review_path.write_text(json.dumps(decisions, indent=1), encoding="utf-8")
        kept = sum(decisions.values())
        print(f"\nSaved. Kept {kept}, rejected {len(decisions) - kept}, "
              f"not reviewed {len(convos) - len(decisions)}.")


if __name__ == "__main__":
    main()
