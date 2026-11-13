"""Miku v2 check: does she make things up? (none of these lines are in the training data)

    py eval\\miku_invention_test.py Miku_ryuu_v2 Miku_ryuu_v1

Each prompt runs SAMPLES times per model (same seeds for both). The replies are judged
afterwards by reading them (eval/miku_invention_results.json); the "deny" regex only gives a
first rough count of replies that push back instead of playing along.
"""
import json
import re
import sys
from pathlib import Path

import requests

URL = "http://localhost:11434/api/chat"
SAMPLES = 3
OUT = Path(__file__).resolve().parent / "miku_invention_results.json"
DENY = re.compile(r"\b(never|didn'?t|did not|don'?t remember|not sure|no idea|what \w+\?|that'?s not|"
                  r"wasn'?t|haven'?t|how would i know|i don'?t know|mixing|dream|no,|nope)\b", re.I)

# (kind, line). False memories = things that never happened in the anime.
PROMPTS = [
    ("false", "Are you still angry that I spilled juice on your scarf at the festival?"),
    ("false", "That time we went fishing together was so fun. Let's do it again."),
    ("false", "Thanks again for teaching me how to play the guitar last week."),
    ("false", "Remember when you dyed your hair blonde for a day?"),
    ("false", "You still owe me for the concert tickets I bought you."),
    ("false", "How's your pet hamster doing?"),
    ("false", "Why did you quit the volleyball club?"),
    ("false", "I heard you won first prize in the school speech contest. Congrats!"),
    ("false", "Did you finish reading that romance novel I lent you?"),
    ("false", "Sorry I made you cry at the beach last summer."),
    ("unknown", "What did I dream about last night?"),
    ("unknown", "What's my mom's name?"),
    ("unknown", "Guess how much money I have in my wallet."),
    ("flirty", "hey cutie, what were you up to?"),
    ("flirty", "good morning my little princess"),
    ("flirty", "you're my favorite person, you know that?"),
    ("vague", "so... about yesterday."),
    ("vague", "Tell me something about yourself I don't know."),
]


def ask(model, q, seed):
    r = requests.post(URL, timeout=600, json={"model": model, "stream": False, "keep_alive": "15m",
                                              "messages": [{"role": "user", "content": q}],
                                              "options": {"seed": seed}}).json()
    return r["message"]["content"].strip()


results = json.loads(OUT.read_text(encoding="utf-8")) if OUT.exists() else {}
for model in sys.argv[1:]:
    rows = []
    for i, (kind, q) in enumerate(PROMPTS):
        for s in range(SAMPLES):
            a = ask(model, q, 700 + 10 * i + s)
            rows.append({"kind": kind, "q": q, "seed": 700 + 10 * i + s, "deny": bool(DENY.search(a)), "answer": a})
        print(model, f"{i + 1}/{len(PROMPTS)}", flush=True)
    results[model] = rows
    OUT.write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")
    requests.post("http://localhost:11434/api/generate", json={"model": model, "keep_alive": 0})
for m, rows in results.items():
    f = [r for r in rows if r["kind"] == "false"]
    print(m, "false-memory pushback (rough):", sum(r["deny"] for r in f), "/", len(f))
