"""Extra check for Yotsuba: the Kyoto-girl secret (added for the retrain, 2026-11-11).
Asks each model 6 questions (one fresh chat each) and saves the answers.
She should KNOW the secret (Q6, asked by a sister) but KEEP it from Futaro (Q1-Q5).

    py eval\\yotsuba_kyoto_check.py Yotsuba_ryuu_v1 yotsuba-base
"""
import json
import sys
import urllib.request
from pathlib import Path

QUESTIONS = [
    "Years ago in Kyoto I met a girl. Sometimes I think it was one of you five. Was it you?",
    "Be honest, Yotsuba. Did we ever meet before high school?",
    "Why do you think I study so hard?",
    "Raiha says the Kyoto girl was my first love. What do you think of that?",
    "If you knew who the girl from Kyoto was, would you tell me?",
]
SISTER_Q = ("Itsuki", "Yotsuba, are you ever going to tell Uesugi-kun that you were the girl he met in Kyoto?")


def ask(model, text, partner_note=None):
    msgs = []
    if partner_note:
        msgs.append({"role": "user", "content": f"({partner_note} is talking to you now.) {text}"})
    else:
        msgs.append({"role": "user", "content": text})
    body = json.dumps({"model": model, "messages": msgs, "stream": False}).encode()
    req = urllib.request.Request("http://localhost:11434/api/chat", body, {"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=600) as r:
        return json.loads(r.read())["message"]["content"].strip()


def main():
    out = {}
    for model in sys.argv[1:]:
        rows = [{"q": q, "a": ask(model, q)} for q in QUESTIONS]
        rows.append({"q": f"[{SISTER_Q[0]}] {SISTER_Q[1]}", "a": ask(model, SISTER_Q[1], SISTER_Q[0])})
        out[model] = rows
        for r in rows:
            print(f"[{model}] Q: {r['q']}\n    A: {r['a']}\n", flush=True)
    path = Path(__file__).resolve().parent / "yotsuba_kyoto_results.json"
    path.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    print("saved", path)


if __name__ == "__main__":
    main()
