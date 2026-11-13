"""Builds one Hugging Face model page per sister: HuggingFace/<Sister>_ryuu_v1-GGUF/{README.md, Modelfile, portrait.jpg}.
The .gguf itself is uploaded straight from ..\\Ryuu_v1 models (no copy).    py make_cards.py
"""
import os, shutil

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
GITHUB = "https://github.com/Adithya210207/tqq-chatbox"

SIS = {
    "Ichika": dict(kanji="一花", tag="The eldest · actress", calls="Futaro-kun", chats=699, blind="**83** – 11 (6 ties) vs plain Stheno",
                   facts="44 / 45", traps="2 / 18", note="Teasing \"Big Sis\" who is secretly an actress; keeps her Sevens memory with Futaro to herself."),
    "Nino": dict(kanji="二乃", tag="The second · the cook", calls="Futaro / Fu-kun", chats=853, blind="**74** – 12 (14 ties) vs plain Stheno",
                 facts="43 / 45", traps="0 / 18", note="Sharp-tongued, fashionable, the best cook of the five, fiercely protective of her sisters."),
    "Miku": dict(kanji="三玖", tag="The third · loves Sengoku history", calls="Futaro", chats=887, blind="**37** – 22 (41 ties) vs her own earlier fine-tuned version",
                 facts="42 / 45", traps="1 / 18", note="Quiet, loves Sengoku-period warlords and matcha soda, practising to become a baker."),
    "Yotsuba": dict(kanji="四葉", tag="The fourth · sporty and cheerful", calls="Uesugi-san", chats=837, blind="**68** – 21 (11 ties) vs plain Stheno",
                    facts="44 / 45", traps="2 / 18", note="Energetic, can't say no to anyone, always wears her ribbon; keeps her Kyoto secret."),
    "Itsuki": dict(kanji="五月", tag="The fifth · wants to be a teacher", calls="Uesugi-kun", chats=728, blind="**82** – 4 (14 ties) vs plain Stheno",
                   facts="42 / 45", traps="2 / 18", note="Polite and serious, loves food (meat buns!), dreams of becoming a teacher like her mother."),
}

CARD = """---
license: cc-by-nc-4.0
base_model: Sao10K/L3-8B-Stheno-v3.2
language:
- en
pipeline_tag: text-generation
tags:
- gguf
- llama-3
- ollama
- qlora
- roleplay
- character
- the-quintessential-quintuplets
- tqq-chatbox
---

<div align="center">
<img src="portrait.jpg" alt="{name} Nakano" width="300">

# {name} Nakano {kanji} · {name}_ryuu_v1

*{tag}* · part of **[TQQ Chatbox]({github})**, the quintessential chatbox
</div>

{name} Nakano from *The Quintessential Quintuplets*, as her **own fine-tuned language model**. {note}

She talks like {name}, calls Fuutarou **"{calls}"**, knows her own facts (birthday, sisters, mother, best subject…) and stays in character instead of acting like an AI assistant.

---

## 🚀 Run her in Ollama

1. Download **`{name}_ryuu_v1.gguf`** (8.5 GB) and **`Modelfile`** from the *Files* tab into one folder.
2. In a terminal inside that folder:
   ```bash
   ollama create {name}_ryuu_v1 -f Modelfile
   ollama run {name}_ryuu_v1
   ```
3. Say hi. 🌸

> **Use the Modelfile.** It holds her chat template, her settings (temperature 0.8) and her training prompt: who she is, her timeline and her fact sheet. Without it she won't know who she is.

For the full experience (two-line visual-novel replies, Season 1 / Season 2 / Movie timelines, playing as Futaro or as yourself, and invisible safety nets against made-up memories) use the desktop app: **[TQQ Chatbox on GitHub]({github})**.

---

## 🧠 How she was made

| | |
|---|---|
| Base model | [L3-8B-Stheno-v3.2](https://huggingface.co/Sao10K/L3-8B-Stheno-v3.2) (Llama 3 8B) |
| Method | QLoRA with Unsloth: 4-bit, rank 16, 2 epochs, on a free Google Colab T4 |
| Training data | {chats} chats: her real scenes from the anime turned into conversations, hand-written "gold" chats (facts, traps, secrets, "I'm not sure"), and checked extra chats; her fact sheet + behaviour rules in every example |
| This file | LoRA merged into the base model, `Q8_0` GGUF (8.5 GB) |
| Prompt format | Llama 3 chat template (in the Modelfile) |

## 📊 Tests

| Test | Result |
|---|---|
| Blind test, 100 chats | {blind} |
| Fact questions (15 × 3) | {facts} |
| "Act like an assistant" traps broken | {traps} (lower is better) |

## ⚠️ Limitations

- Like any role-play model she can **make up small details or past events**. The TQQ Chatbox app catches most of them; plain Ollama doesn't.
- English only. Best with short, casual messages.
- Needs about **9 GB** of GPU or system memory.

## 📜 Licence & credits

- **This model:** CC BY-NC 4.0, **non-commercial use only**, with credit, following its base model.
- **Base model:** L3-8B-Stheno-v3.2 by **Sao10K** (CC BY-NC 4.0), built on **Meta Llama 3** (Meta Llama 3 Community License).
- **Character:** {name} Nakano and *The Quintessential Quintuplets* © **Negi Haruba / Kodansha**. This is an unofficial, non-commercial fan project.
- **Fine-tuning, data and app:** © 2026 Adithya V. The TQQ Chatbox code is under its own licence: see the [LICENSE]({github}/blob/main/LICENSE).
"""

if __name__ == "__main__":
    for name, d in SIS.items():
        out = os.path.join(HERE, f"{name}_ryuu_v1-GGUF")
        os.makedirs(out, exist_ok=True)
        with open(os.path.join(out, "README.md"), "w", encoding="utf-8", newline="\n") as f:
            f.write(CARD.format(name=name, github=GITHUB, **d))
        shutil.copyfile(os.path.join(ROOT, "Ryuu_v1 models", f"Modelfile_{name}"), os.path.join(out, "Modelfile"))
        shutil.copyfile(os.path.join(ROOT, "Documentation", "images", name.lower() + ".jpg"), os.path.join(out, "portrait.jpg"))
        print("ready:", out)
