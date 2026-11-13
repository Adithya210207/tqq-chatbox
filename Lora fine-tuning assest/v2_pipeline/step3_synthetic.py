"""
STEP 3 — Synthetic conversations.  (Fix D)

Makes NEW multi-turn chats about everyday topics, so she learns to hold a
normal conversation with you (something the anime alone can't teach).

Two ways to make them:

  roleplay (default)  Two AIs actually TALK, one message at a time.
                      --her-model plays the sister: it gets her character card
                      plus ~15 of her REAL anime lines as a style guide.
                      --user-model plays Fuutarou / a classmate / a friend.
                      This is how roleplay models work best.

  script              One AI writes the whole conversation at once (older way).

Examples:
  py step3_synthetic.py --sister Miku --count 3                       # quick test
  py step3_synthetic.py --sister Miku --count 200                     # full run
  py step3_synthetic.py --sister Miku --her-model stheno:latest       # different actor
  py step3_synthetic.py --sister Miku --her-model gemma4:12b,stheno:latest   # mix (random per chat)
  py step3_synthetic.py --sister all --mode script --backend claude

LATER (round 2): once you have trained her LoRA and loaded it in Ollama
(e.g. as "miku"), use HER as the actor:  --her-model miku
Her own fine-tuned voice usually gives the most natural data.

Resumable: stop with Ctrl+C, run the same command again to continue.
Then READ the results with step4_review.py - you are the quality check.
"""
import argparse
import collections
import difflib
import random
import re

from common import (ALLOWED_NAMES, FULL_NAMES, SISTERS, append_jsonl, futaro_name_forms,
                    load_stage_prompts, read_jsonl, sister_dir)
from llm import ask_json, chat, strict_object

# Chosen after a side-by-side test on 2026-11-09 (see GUIDE.md):
# Rocinante-XL-16B gives her the most human, emotional voice; gemma4 is less
# vivid but very consistent. Each chat randomly picks one (2:1 for Rocinante).
# gemma4 plays the other person: it was the most believable Fuutarou.
# "rocinante-xl" must first be created from ollama/Modelfile_rocinante-xl.
DEFAULT_HER_MODEL = "rocinante-xl,rocinante-xl,gemma4:12b"
DEFAULT_USER_MODEL = "gemma4:12b"

TOPICS = [
    "studying for an upcoming exam together", "what to eat for dinner tonight",
    "a bad day at school", "weekend plans", "a movie or TV show they watched",
    "a part-time job", "the weather and the changing seasons", "a favorite food",
    "being teased by one of her sisters", "a misunderstanding that needs clearing up",
    "a gift idea for one of her sisters", "feeling nervous about the future",
    "her hobby or special interest", "a funny thing that happened today",
    "shopping for clothes", "cooking or baking something", "a school event or festival",
    "being sick and staying home", "an argument with a sister", "a compliment she wasn't expecting",
    "a rainy day with nothing to do", "a late-night phone call", "walking home together",
    "summer vacation plans", "a test result she is proud of (or ashamed of)",
    "trying to keep a secret", "giving the user advice about a problem",
    "the user being late", "something the user cooked", "something from her past she rarely talks about",
    "feeling jealous", "comforting the user after bad news", "a library study session",
    "choosing a club or activity", "money worries", "an awkward silence",
    "the user asking for her honest opinion", "a festival with fireworks", "New Year's plans",
]

# (name used in her system prompt, who the partner AI plays)
PARTNERS = [
    ("Fuutarou", "Fuutarou Uesugi from The Quintessential Quintuplets: the quintuplets' tutor. "
                 "A blunt, studious, poor high-school boy with top grades, awkward about feelings, "
                 "who secretly cares about the sisters"),
    ("Fuutarou", "Fuutarou Uesugi from The Quintessential Quintuplets: the quintuplets' tutor. "
                 "A blunt, studious, poor high-school boy with top grades, awkward about feelings, "
                 "who secretly cares about the sisters"),
    ("a classmate", "a friendly high-school classmate who knows her reasonably well"),
    ("a new friend", "someone around her age who became her friend recently"),
]
STAGES = ["season_1", "season_2", "movie"]

_NAME_RE = re.compile(r"\b(Futaro-kun|Fuutarou-kun|Uesugi-san|Uesugi-kun|Uesugi|Futaro|Fuutarou)\b")


def address_form(sister, stage):
    """How she actually calls Fuutarou in the anime, counted from her real lines."""
    rows = read_jsonl(sister_dir(sister) / "canon_turns.jsonl")
    by_stage = collections.Counter(n for r in rows if r["timeline_stage"] == stage
                                   for n in _NAME_RE.findall(r["reply"]))
    overall = collections.Counter(n for r in rows for n in _NAME_RE.findall(r["reply"]))
    best = (by_stage if sum(by_stage.values()) >= 3 else overall).most_common(1)
    return best[0][0] if best else "Futaro"


def style_examples(sister, stage, rng, k=15):
    rows = read_jsonl(sister_dir(sister) / "canon_turns.jsonl")
    good = [r["reply"] for r in rows
            if r["timeline_stage"] == stage and not r["is_filler"] and 4 <= len(r["reply"].split()) <= 40]
    return rng.sample(good, min(k, len(good)))


FACT_RULE = ("Keep facts consistent with the series. Don't invent big plot events or shared "
             "childhood memories: Fuutarou only met the quintuplets in their second year of high school.")

# ===========================================================================
# ROLEPLAY MODE
# ===========================================================================
def her_system(sister, stage, partner, examples, prompts):
    lines = "\n".join(f"- {e}" for e in examples)
    who = "Fuutarou" if partner == "Fuutarou" else partner
    return f"""You are {FULL_NAMES[sister]}. {prompts[stage]}

This is exactly how you talk (real lines of yours):
{lines}

You are texting with {who}. When you address Fuutarou you call him "{address_form(sister, stage)}".
Reply as {sister} only: ONE short message of natural spoken dialogue, like a real chat.
Match the length and tone of your real lines. No narration, no actions in asterisks, no name prefix.
{FACT_RULE}"""


def partner_system(sister, partner_desc, topic):
    return f"""You are {partner_desc}. You are texting with {FULL_NAMES[sister]}.
Topic to talk about: {topic}.
Write only your next message: 1-2 natural sentences of dialogue, react to what she said.
No narration, no actions in asterisks, no name prefix. {FACT_RULE}"""


_ACTION_RE = re.compile(r"\*[^*]*\*|\([^)]*\)|\[[^\]]*\]")
_EMOJI_RE = re.compile("[\U0001F000-\U0001FAFF☀-➿️‍]")


def clean_reply(text, speaker_names):
    """Strip *actions*, (asides), name prefixes, quotes, and anything after the
    model starts writing the other person's line."""
    # same punctuation style as the subtitles (straight quotes, "..." not "…")
    text = (text.replace("’", "'").replace("‘", "'").replace("“", '"')
            .replace("”", '"').replace("…", "..."))
    text = _ACTION_RE.sub("", text).replace("*", "")
    text = _EMOJI_RE.sub("", text)
    # cut if the model starts a new speaker line ("Fuutarou: ...")
    text = re.split(r"\n\s*\n|\n\s*\w[\w' -]{0,20}:", text)[0]
    for name in speaker_names:
        text = re.sub(rf"^\s*{re.escape(name)}\s*:\s*", "", text, flags=re.IGNORECASE)
    text = re.sub(r"\s+", " ", text).strip().strip('"').strip()
    text = re.sub(r"^[-–—]\s+", "", text)   # "- I'll go with you..." -> "I'll go with you..."
    return text


def roleplay_conversation(sister, stage, partner, partner_desc, topic, n_turns,
                          her_model, user_model, backend, prompts, rng):
    examples = style_examples(sister, stage, rng)
    sys_her = her_system(sister, stage, partner, examples, prompts)
    sys_user = partner_system(sister, partner_desc, topic)
    names = [sister, FULL_NAMES[sister], "Fuutarou", "Futaro", "Uesugi", "Me", "You"]
    convo = []   # list of {"role": "user"/"assistant", "content"} from HER point of view
    empty_retries = 0
    while len(convo) < n_turns:
        if len(convo) % 2 == 0:
            # partner speaks: from the partner's point of view, roles are flipped
            flipped = [{"role": "assistant" if m["role"] == "user" else "user", "content": m["content"]}
                       for m in convo]
            msgs = [{"role": "user", "content": "(You start the conversation.)"}] + flipped
            text = chat(sys_user, msgs, backend=backend if backend == "claude" else "ollama",
                        model=user_model, temperature=0.9, max_tokens=120)
            role = "user"
        else:
            text = chat(sys_her, convo, backend="ollama", model=her_model,
                        temperature=0.9, max_tokens=160)
            role = "assistant"
        text = clean_reply(text, names)
        if not text:
            # Roleplay models sometimes answer with only *actions*; ask again.
            empty_retries += 1
            if empty_retries > 3:
                return None, "empty message after cleaning"
            continue
        convo.append({"role": role, "content": text})
    return convo, None


# ===========================================================================
# SCRIPT MODE (one AI writes the whole chat as JSON)
# ===========================================================================
def build_prompt(sister, stage, partner_desc, topic, n_turns, examples, prompts):
    name = FULL_NAMES[sister]
    calls = address_form(sister, stage)
    lines = "\n".join(f"- {e}" for e in examples)
    return f"""Write a realistic text-chat conversation for a roleplay dataset.

Character: {name} from "The Quintessential Quintuplets".
Character card: {prompts[stage]}

Real lines she says in the anime (copy her TONE, rhythm and word choice — do not reuse these lines):
{lines}

She is chatting with: {partner_desc}.
Topic: {topic}.

Rules:
- Exactly {n_turns} messages, alternating, starting with the user. Role "user" is {partner_desc}; role "assistant" is {sister}.
- {sister} talks exactly like in the anime: short-to-medium casual replies, emotions, quirks, how she addresses people. She is NOT an assistant: she never offers help like a chatbot, never says "How can I help", never lists things.
- She has her own opinions, moods and reactions; she can tease, disagree, get flustered, change subject, ask questions back.
- When she addresses Fuutarou she calls him "{calls}" (that is how she says it in the anime).
- {FACT_RULE}
- No narration, no stage directions in asterisks, no speaker names inside the text.
"""


def schema(n_turns):
    msg = strict_object({"role": {"type": "string", "enum": ["user", "assistant"]},
                         "content": {"type": "string"}})
    return strict_object({"messages": {"type": "array", "items": msg,
                                       "minItems": n_turns, "maxItems": n_turns}})


# ===========================================================================
# Quality checks (both modes)
# ===========================================================================
BANNED = ["as an ai", "how can i help", "how may i help", "i'm here to help", "language model",
          "assistant", "*",
          # the model talking about its own instructions instead of staying in character
          "reply as", "replying as", "guideline", "in character", "roleplay", "role-play",
          "the quintessential quintuplets", "system prompt", "~",
          # added 2026-11-09 after reading the first 68 chats
          "the character would", "as the character", "line of dialogue", "spreadsheet"]

# Pet phrases a model latched onto. Allowed in at most PET_PHRASE_CAP saved chats.
PET_PHRASES = [re.compile(r"\bas (an|your|his) equals?\b"),
               re.compile(r"\bstand (by|beside|next to) (you|him|futaro)\b"),
               re.compile(r"\bstand by (your|his|futaro's) side\b")]
PET_PHRASE_CAP = 3

# Talking ABOUT Futaro in the third person while he is the one she talks to:
# "Futaro is so dense", "Fu-kun's notes", "Uesugi-san? He never listens."
_HIM = r"(fu-kun|fuu?tarou?(?:-kun|-san)?|uesugi(?:-kun|-san)?)"
HE_ABOUT_HIM_RE = re.compile(
    rf"\b{_HIM}('s\b|\s+(is|was|has|had|does|did|said|says|thinks|thought|told|always|never|"
    rf"isn't|wasn't|doesn't|didn't|can't|won't|would|will|gets|got|likes|liked|seems|looks|keeps|"
    rf"knows|wants|tries|really|just|even|still)\b)"
    rf"|\b{_HIM}[?!.,]+\s+(he|he's|his|him)\b")

# Internet-chat words Miku would never say out loud in the anime.
TEXTSPEAK_RE = re.compile(r"\b(lol|lmao|lmfao|omg|rofl|haha+|hehe+|gotcha)\b")


def _phrases(text, n=5):
    words = re.findall(r"[a-z']+", text.lower())
    return {" ".join(words[i:i + n]) for i in range(len(words) - n + 1)}


def overused_phrases(saved_rows):
    """Phrases she already uses in too many saved chats. New chats repeating
    them are rejected, so one pet phrase can't take over the whole dataset.
    Returns (set of overused 5-word phrases, list of maxed-out pet regexes)."""
    min_chats = max(5, len(saved_rows) // 15)   # 5 of 50 chats, 13 of 200
    counts = {}
    pet_counts = [0] * len(PET_PHRASES)
    for row in saved_rows:
        her = " ".join(m["content"].lower() for m in row["messages"] if m["role"] == "assistant")
        for p in _phrases(her):
            counts[p] = counts.get(p, 0) + 1
        for i, rx in enumerate(PET_PHRASES):
            pet_counts[i] += bool(rx.search(her))
    phrases = {p for p, c in counts.items() if c >= min_chats}
    pets = [rx for rx, c in zip(PET_PHRASES, pet_counts) if c >= PET_PHRASE_CAP]
    return phrases, pets


def check(messages, sister, partner=None, overused=(frozenset(), [])):
    """Return None if fine, otherwise the reason it is rejected."""
    if not messages or messages[0]["role"] != "user" or messages[-1]["role"] != "assistant":
        return "must start with user and end with her"
    for a, b in zip(messages, messages[1:]):
        if a["role"] == b["role"]:
            return "roles don't alternate"
    her = [m["content"].lower() for m in messages if m["role"] == "assistant"]
    for m in messages:
        text = m["content"].lower()
        if not text.strip():
            return "empty message"
        if m["role"] == "assistant":
            if any(b in text for b in BANNED):
                return "assistant-like phrasing"
            if len(text.split()) > 70:
                return "reply too long"
            if text.startswith(sister.lower() + ":"):
                return "speaker name inside text"
            if re.search(rf"\b{sister.lower()}\b", text):
                return "she says her own name (model got confused)"
            if TEXTSPEAK_RE.search(text):
                return "internet slang (lol, haha...)"
            if re.search(r"\s[,.!?](?!\.)", text):
                return "missing word (space before punctuation)"
            if partner and partner != "Fuutarou" and \
                    re.search(r"(,\s*(futaro|fuutarou|uesugi)\s*[.!?,]|^(futaro|fuutarou|uesugi)\s*,)", text):
                return "calls the wrong person Futaro"
            wrong = [n for n in futaro_name_forms(text) if n not in ALLOWED_NAMES.get(sister, {n})]
            if wrong:
                return f"wrong name for Futaro ({wrong[0]})"
            if partner == "Fuutarou" and HE_ABOUT_HIM_RE.search(text):
                return "talks about Futaro as 'he' while talking to him"
            if overused[0] & _phrases(text) or any(rx.search(text) for rx in overused[1]):
                return "overused phrase from earlier chats"
        if text.rstrip().endswith(":"):
            return "cut off mid-sentence"
    if sum(h.count("got it") for h in her) >= 2:
        return "says 'Got it' again and again"
    for prev, cur in zip(messages, messages[1:]):
        # she parrots what the other person just said
        if cur["role"] == "assistant" and len(cur["content"].split()) >= 6 and \
                difflib.SequenceMatcher(None, prev["content"].lower(), cur["content"].lower()).ratio() > 0.6:
            return "she copies the other person's line"
    for i, a in enumerate(her):
        for b in her[i + 1:]:
            # short lines ("I know.") may repeat naturally; long ones shouldn't
            if min(len(a.split()), len(b.split())) >= 6 and \
                    difflib.SequenceMatcher(None, a, b).ratio() > 0.6:
                return "she repeats herself"
    return None


# ===========================================================================
def run(sister, args):
    out_path = sister_dir(sister) / "synthetic.jsonl"
    have = read_jsonl(out_path)
    todo = max(0, args.count - len(have))
    prompts = load_stage_prompts(sister)
    her_models = [m.strip() for m in args.her_model.split(",") if m.strip()]
    rng = random.Random(1000 + len(have))   # different topics each resume
    print(f"\n{sister}: {len(have)} already, making {todo} more "
          f"(mode={args.mode}, her={args.her_model if args.mode == 'roleplay' else args.model or args.backend})")
    made = rejected = 0
    # ids continue after the highest existing one (rows may have been deleted)
    next_id = max([int(r["id"].rsplit("_", 1)[1]) for r in have] + [0]) + 1
    overused = overused_phrases(have)
    while made < todo:
        stage = rng.choice(STAGES)
        partner, partner_desc = rng.choice(PARTNERS)
        topic = rng.choice(TOPICS)
        n_turns = rng.choice([6, 8, 8, 10])
        try:
            if args.mode == "roleplay":
                her_model = rng.choice(her_models)
                msgs, problem = roleplay_conversation(
                    sister, stage, partner, partner_desc, topic, n_turns,
                    her_model, args.user_model, args.backend, prompts, rng)
                generator = f"roleplay:her={her_model},user={args.user_model}"
            else:
                prompt = build_prompt(sister, stage, partner_desc, topic, n_turns,
                                      style_examples(sister, stage, rng), prompts)
                data = ask_json(prompt, schema(n_turns), backend=args.backend,
                                model=args.model, temperature=0.9)
                msgs = [{"role": m["role"], "content": m["content"].strip()} for m in data["messages"]]
                problem = None
                generator = f"script:{args.backend}:{args.model or 'default'}"
        except RuntimeError as e:
            print(f"  skipped ({e})")
            continue
        problem = problem or check(msgs, sister, partner, overused)
        if problem:
            rejected += 1
            print(f"  rejected: {problem}")
            if rejected > 2 * todo + 20:
                print("  too many rejects - try a different model")
                break
            continue
        row = {
            "id": f"{sister.lower()}_synth_{next_id:04d}",
            "source": "synthetic", "timeline_stage": stage, "partner": partner,
            "topic": topic, "messages": msgs, "generator": generator,
        }
        append_jsonl(out_path, row)
        have.append(row)
        overused = overused_phrases(have)
        next_id += 1
        made += 1
        print(f"  {made}/{todo}  [{stage}] {topic}")
    print(f"{sister}: done -> {out_path}")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--sister", default="Miku", help="Ichika, Nino, Miku, Yotsuba, Itsuki or all")
    p.add_argument("--count", type=int, default=200, help="total conversations wanted per sister")
    p.add_argument("--mode", default="roleplay", choices=["roleplay", "script"])
    p.add_argument("--her-model", default=DEFAULT_HER_MODEL,
                   help="Ollama model(s) that play her; comma-separate several to mix")
    p.add_argument("--user-model", default=DEFAULT_USER_MODEL, help="model that plays the other person")
    p.add_argument("--backend", default="ollama", choices=["ollama", "claude"],
                   help="script mode: who writes; roleplay mode: who plays the other person")
    p.add_argument("--model", default=None, help="script mode only: model override")
    args = p.parse_args()
    if args.mode == "roleplay" and args.backend == "claude" and args.user_model == DEFAULT_USER_MODEL:
        args.user_model = None   # use Claude's default model for the partner
    for s in (SISTERS if args.sister.lower() == "all" else [args.sister.capitalize()]):
        run(s, args)


if __name__ == "__main__":
    main()
