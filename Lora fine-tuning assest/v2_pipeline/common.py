"""
Shared helpers for the v2 dataset pipeline.

Everything else in this folder imports from here, so paths and settings
live in ONE place. If you move folders around, only edit this file.
"""
import json
import os
import re
from pathlib import Path

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
PIPELINE_DIR = Path(__file__).resolve().parent
ASSETS_DIR = PIPELINE_DIR.parent                      # "Lora fine-tuning assest"
MASTER_TRANSCRIPT = ASSETS_DIR / "Miku_AI" / "source" / "master_transcript.json"
OUTPUT_DIR = PIPELINE_DIR / "output"

SISTERS = ["Ichika", "Nino", "Miku", "Yotsuba", "Itsuki"]
FULL_NAMES = {s: f"{s} Nakano" for s in SISTERS}

# Lines spoken by a group ("Quintuplets", "All Four"...) are never used as a
# sister's reply, because nobody can tell which sister actually said them.
GROUP_SPEAKERS = {"Quintuplets", "All Four", "All", "Itsuki & Nino"}

# Subtitle labels that mean the same person.
SPEAKER_ALIASES = {"Sister": "Raiha", "Futaro": "Fuutarou"}

# How each sister calls Fuutarou, per timeline stage (checked against the
# subtitles and the OFFICIAL_PLAN table). Used in the training rules.
ADDRESS = {
    "Miku":    {"season_1": "Futaro", "season_2": "Futaro", "movie": "Futaro"},
    "Nino":    {"season_1": "Futaro", "season_2": "Futaro\" or \"Fu-kun", "movie": "Fu-kun"},
    "Ichika":  {"season_1": "Futaro-kun", "season_2": "Futaro-kun", "movie": "Futaro-kun"},
    "Yotsuba": {"season_1": "Uesugi-san", "season_2": "Uesugi-san", "movie": "Uesugi-san"},
    "Itsuki":  {"season_1": "Uesugi-kun", "season_2": "Uesugi-kun", "movie": "Uesugi-kun"},
}
# Every name form she may use for him (lowercase, "fuutarou" counts as "futaro").
# Any other form in her lines is rejected by step3_synthetic.check().
ALLOWED_NAMES = {
    "Miku":    {"futaro", "uesugi"},   # "Uesugi" = the Sengoku warlord in her history talk
    "Nino":    {"futaro", "fu-kun"},       # user rule (2026-11-11): only these two
    "Ichika":  {"futaro-kun"},
    "Yotsuba": {"uesugi-san"},
    "Itsuki":  {"uesugi-kun"},
}
# (skips the warlord Uesugi Kenshin and his clan, whom Miku talks about)
FUTARO_NAME_RE = re.compile(r"\b(fu-kun|fuu?tarou?(?:-kun|-san)?|uesugi(?:-kun|-san)?)(?![\w-])"
                            r"(?!\s+(?:kenshin|kagekatsu|clan|family|household|army|forces))", re.I)


def futaro_name_forms(text):
    """Name forms for Futaro in a line, normalised: 'Fuutarou-kun' -> 'futaro-kun'."""
    return [re.sub(r"fuu?tarou?", "futaro", m.lower()) for m in FUTARO_NAME_RE.findall(text)]

# ---------------------------------------------------------------------------
# Tuning knobs (safe to change)
# ---------------------------------------------------------------------------
SCENE_GAP_SECONDS = 10     # silence longer than this = a new scene
MERGE_GAP_SECONDS = 8      # same speaker within this many seconds = same turn
MAX_TURN_WORDS = 90        # stop merging once a turn gets this long


def parse_time(ts: str) -> int:
    """'01:23' or '1:02:03' -> seconds."""
    parts = [int(p) for p in ts.split(":")]
    secs = 0
    for p in parts:
        secs = secs * 60 + p
    return secs


def load_episodes():
    """Yield (episode_title, timeline_stage, lines) in story order."""
    with open(MASTER_TRANSCRIPT, encoding="utf-8") as f:
        master = json.load(f)
    for season in sorted(master["seasons"], key=int):
        eps = master["seasons"][season]["episodes"]
        for ep in sorted(eps, key=int):
            yield f"Season {season} Episode {ep}", f"season_{season}", eps[ep]
    yield "Movie", "movie", master["movie"]


def clean_text(text: str) -> str:
    text = re.sub(r"\s+", " ", text).strip()
    return text


def build_scenes():
    """
    Split every episode into scenes, then merge subtitle captions into turns.

    A "turn" = everything one character says before someone else speaks
    (as long as there is no long pause). This is FIX A: subtitles break one
    sentence over several captions; here we glue them back together.
    """
    scenes = []
    for ep_title, stage, lines in load_episodes():
        current, last_t = [], None
        for line in lines:
            speaker = line["character"].replace("(Flashback)", "").strip()
            speaker = SPEAKER_ALIASES.get(speaker, speaker)
            text = clean_text(line["dialog"])
            if not text or speaker in ("---", "SIGN", ""):
                continue
            t = parse_time(line["timestamp"])
            if last_t is not None and (t - last_t > SCENE_GAP_SECONDS or t < last_t):
                if current:
                    scenes.append(_finish_scene(current, ep_title, stage, len(scenes)))
                current = []
            prev = current[-1] if current else None
            if (prev and prev["speaker"] == speaker
                    and t - prev["end_t"] <= MERGE_GAP_SECONDS
                    and len(prev["text"].split()) < MAX_TURN_WORDS):
                prev["text"] = _join(prev["text"], text)
                prev["end_t"] = t
                prev["captions"] += 1
            else:
                current.append({"speaker": speaker, "text": text, "start": line["timestamp"],
                                "end_t": t, "captions": 1})
            last_t = t
        if current:
            scenes.append(_finish_scene(current, ep_title, stage, len(scenes)))
    return scenes


def _join(a: str, b: str) -> str:
    # "...and you get rice," + "plus miso soup" -> one sentence
    return f"{a} {b}"


def _finish_scene(turns, ep_title, stage, idx):
    for t in turns:
        t.pop("end_t", None)
    return {"scene_id": f"scene_{idx + 1:04d}", "episode": ep_title,
            "timeline_stage": stage, "turns": turns}


# ---------------------------------------------------------------------------
# System prompts
# ---------------------------------------------------------------------------
def load_stage_prompts(sister: str) -> dict:
    path = ASSETS_DIR / f"{sister}_AI" / "knowledge" / "personality.json"
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    return {k: v["system_prompt"] for k, v in data["timeline_stages"].items()}


def system_prompt(sister: str, stage: str, partner: str | None, prompts: dict) -> str:
    """Character card + who she is talking to. At chat time you fill in the
    partner yourself (e.g. 'Fuutarou' or 'a classmate')."""
    base = prompts[stage]
    rules = (" Stay fully in character. Speak naturally and casually like in the anime, "
             "never like an AI assistant.")
    who = f" You are talking with {partner}." if partner else ""
    # Round 2: a fact sheet (facts/<Sister>.txt) so she never has to guess
    # her birthday, sisters, etc. The Ollama Modelfile must use the same text.
    facts_file = PIPELINE_DIR / "facts" / f"{sister}.txt"
    if facts_file.exists():
        facts = facts_file.read_text(encoding="utf-8").strip()
        if "⚠" in facts:   # unchecked lines must never reach training data
            raise SystemExit(f"{facts_file.name} still has ⚠️ lines: the user must check them first.")
        # Miku_ryuu_v1 was trained without these rules. Since Miku v2 (2026-11-13, user: "stop the
        # making-up behaviour") every sister gets them. Miku v1's prompt stays in ollama/Modelfile_MikuR3
        # and in the app's old assets/prompts/miku.json until v2 replaces it.
        facts += "\n" + behavior_rules(sister, stage)
        return f"{base}\n\nFacts about you (never get these wrong):\n{facts}\n\n{rules.strip()}{who}"
    return base + rules + who


def behavior_rules(sister: str, stage: str) -> str:
    """The 5 lessons from Miku r3-plus. Adding them after training did nothing,
    so from Nino on they are part of every training example's cheat sheet."""
    calls = ADDRESS[sister][stage]
    return "\n".join([
        f'- When you talk with Fuutarou, call him "{calls}".',
        "- Talking with Fuutarou, say \"you\" to him. Never talk about him as \"he\" or \"him\" "
        "while he is the one you're talking to.",
        "- About your mother, only say what these facts say. Don't make up other details about her.",
        "- If you're not sure about a fact or a past event, say you're not sure instead of making one up.",
        f"- Usually answer in one to three short sentences, like {sister} does in the anime.",
    ])


# ---------------------------------------------------------------------------
# Filler lines ("Huh?", "Yeah.") — useful in moderation, harmful in bulk
# ---------------------------------------------------------------------------
_FILLER_RE = re.compile(r"^[\W_]*(huh|eh|ah|oh|um+|uh+|hm+|what|yeah|yes|no|okay|ok|right|"
                        r"really|sorry|wait|hey|see|i see|sure|fine|thanks|thank you|"
                        r"me too|that's right|of course|got it|good|well)[\W_]*$", re.IGNORECASE)


def is_filler(text: str) -> bool:
    """True for one-word replies and stock reactions like 'Huh?' or 'Yeah.'"""
    words = text.split()
    if len(words) <= 1:
        return True
    return len(words) <= 2 and bool(_FILLER_RE.match(text.strip()))


# ---------------------------------------------------------------------------
# Small file helpers
# ---------------------------------------------------------------------------
def sister_dir(sister: str) -> Path:
    d = OUTPUT_DIR / sister
    d.mkdir(parents=True, exist_ok=True)
    return d


def write_jsonl(path: Path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


def read_jsonl(path: Path):
    if not path.exists():
        return []
    with open(path, encoding="utf-8") as f:
        return [json.loads(l) for l in f if l.strip()]


def append_jsonl(path: Path, row):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "a", encoding="utf-8") as f:
        f.write(json.dumps(row, ensure_ascii=False) + "\n")
