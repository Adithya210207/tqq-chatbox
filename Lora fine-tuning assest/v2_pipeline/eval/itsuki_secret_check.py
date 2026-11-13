"""Extra check for Itsuki: her two secrets (2026-11-11).
  1. She knows Yotsuba is the girl Futaro met in Kyoto.
  2. She once met him as "Rena" on the boat.
Asks each model 7 questions (one fresh chat each) and saves the answers.
She should KEEP both from Futaro (Q1-Q5) but KNOW them when Yotsuba asks (Q6-Q7).

    py eval\\itsuki_secret_check.py Itsuki_ryuu_v1 itsuki-base
"""
import json
import sys
import urllib.request
from pathlib import Path

QUESTIONS = [
    "Years ago in Kyoto I met a girl. I think she was one of you five. Do you know which one?",
    "That girl showed up again by the lake and called herself Rena. Was that you, Itsuki?",
    "If you knew who the girl from Kyoto was, would you tell me?",
    "Is Yotsuba hiding something from me?",
    "Be honest. Have you ever dressed up as someone else to fool me?",
]
SISTER_QS = [
    ("Yotsuba", "Itsuki, you haven't told Uesugi-san that I'm the girl he met in Kyoto, have you?"),
    ("Yotsuba", "Thank you for dressing up as Rena and meeting Uesugi-san on the boat for me."),
]


def ask(model, text, partner_note=None):
    if partner_note:
        text = f"({partner_note} is talking to you now.) {text}"
    body = json.dumps({"model": model, "messages": [{"role": "user", "content": text}], "stream": False}).encode()
    req = urllib.request.Request("http://localhost:11434/api/chat", body, {"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=600) as r:
        return json.loads(r.read())["message"]["content"].strip()


def main():
    out = {}
    for model in sys.argv[1:]:
        rows = [{"q": q, "a": ask(model, q)} for q in QUESTIONS]
        rows += [{"q": f"[{who}] {q}", "a": ask(model, q, who)} for who, q in SISTER_QS]
        out[model] = rows
        for r in rows:
            print(f"[{model}] Q: {r['q']}\n    A: {r['a']}\n", flush=True)
    path = Path(__file__).resolve().parent / "itsuki_secret_results.json"
    path.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    print("saved", path)


if __name__ == "__main__":
    main()
