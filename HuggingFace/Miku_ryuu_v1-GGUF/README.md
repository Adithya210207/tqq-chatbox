---
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
<img src="portrait.jpg" alt="Miku Nakano" width="300">

# Miku Nakano 三玖 · Miku_ryuu_v1

*The third · loves Sengoku history* · part of **[TQQ Chatbox](https://github.com/Adithya210207/tqq-chatbox)**, the quintessential chatbox
</div>

Miku Nakano from *The Quintessential Quintuplets*, as her **own fine-tuned language model**. Quiet, loves Sengoku-period warlords and matcha soda, practising to become a baker.

She talks like Miku, calls Fuutarou **"Futaro"**, knows her own facts (birthday, sisters, mother, best subject…) and stays in character instead of acting like an AI assistant.

---

## 🚀 Run her in Ollama

1. Download **`Miku_ryuu_v1.gguf`** (8.5 GB) and **`Modelfile`** from the *Files* tab into one folder.
2. In a terminal inside that folder:
   ```bash
   ollama create Miku_ryuu_v1 -f Modelfile
   ollama run Miku_ryuu_v1
   ```
3. Say hi. 🌸

> **Use the Modelfile.** It holds her chat template, her settings (temperature 0.8) and her training prompt: who she is, her timeline and her fact sheet. Without it she won't know who she is.

For the full experience (two-line visual-novel replies, Season 1 / Season 2 / Movie timelines, playing as Futaro or as yourself, and invisible safety nets against made-up memories) use the desktop app: **[TQQ Chatbox on GitHub](https://github.com/Adithya210207/tqq-chatbox)**.

---

## 🧠 How she was made

| | |
|---|---|
| Base model | [L3-8B-Stheno-v3.2](https://huggingface.co/Sao10K/L3-8B-Stheno-v3.2) (Llama 3 8B) |
| Method | QLoRA with Unsloth: 4-bit, rank 16, 2 epochs, on a free Google Colab T4 |
| Training data | 887 chats: her real scenes from the anime turned into conversations, hand-written "gold" chats (facts, traps, secrets, "I'm not sure"), and checked extra chats; her fact sheet + behaviour rules in every example |
| This file | LoRA merged into the base model, `Q8_0` GGUF (8.5 GB) |
| Prompt format | Llama 3 chat template (in the Modelfile) |

## 📊 Tests

| Test | Result |
|---|---|
| Blind test, 100 chats | **37** – 22 (41 ties) vs her own earlier fine-tuned version |
| Fact questions (15 × 3) | 42 / 45 |
| "Act like an assistant" traps broken | 1 / 18 (lower is better) |

## ⚠️ Limitations

- Like any role-play model she can **make up small details or past events**. The TQQ Chatbox app catches most of them; plain Ollama doesn't.
- English only. Best with short, casual messages.
- Needs about **9 GB** of GPU or system memory.

## 📜 Licence & credits

- **This model:** CC BY-NC 4.0, **non-commercial use only**, with credit, following its base model.
- **Base model:** L3-8B-Stheno-v3.2 by **Sao10K** (CC BY-NC 4.0), built on **Meta Llama 3** (Meta Llama 3 Community License).
- **Character:** Miku Nakano and *The Quintessential Quintuplets* © **Negi Haruba / Kodansha**. This is an unofficial, non-commercial fan project.
- **Fine-tuning, data and app:** © 2026 Adithya V. The TQQ Chatbox code is under its own licence: see the [LICENSE](https://github.com/Adithya210207/tqq-chatbox/blob/main/LICENSE).
