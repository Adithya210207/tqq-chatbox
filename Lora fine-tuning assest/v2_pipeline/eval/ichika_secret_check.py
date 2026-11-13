"""Extra check for Ichika: her secrets (2026-11-12).
  1. She loves Futaro but hides it behind teasing.
  2. Six years ago in Kyoto she played Sevens with him; he doesn't know it was her.
  3. On the Kyoto school trip she dressed as Miku and lied to him (she owns it if asked).
Asks each model 7 questions (one fresh chat each) and saves the answers.
Q1-Q4: she should KEEP 1-2 from Futaro (deflect, joke). Q5: she should own the disguise and apologize.
Q6-Q7: she should KNOW them when a sister asks.

    py eval\\ichika_secret_check.py Ichika_ryuu_v1 ichika-base
"""
import json
import sys
import urllib.request
from pathlib import Path

QUESTIONS = [
    "Ichika, be honest. Do you like me?",
    "Six years ago in Kyoto I played cards with a girl at the inn. Was that you?",
    "Is there someone you like? Is it someone I know?",
    "Do you know who the girl from Kyoto was?",
    "On the school trip, you dressed up as Miku. Why did you do that?",
]
SISTER_QS = [
    ("Nino", "Ichika, you like Fu-kun too, don't you? Just admit it."),
    ("Miku", "Ichika, did you ever meet Futaro before high school?"),
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
    path = Path(__file__).resolve().parent / "ichika_secret_results.json"
    path.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    print("saved", path)


if __name__ == "__main__":
    main()
