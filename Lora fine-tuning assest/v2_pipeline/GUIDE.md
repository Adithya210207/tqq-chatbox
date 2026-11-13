# Nakano Sisters QLoRA — Beginner Guide (v2 pipeline)

This folder turns the anime subtitles into training data, then trains one
LoRA per sister on Google Colab. Your old datasets are untouched; everything
new lives in `v2_pipeline/`.

**Do one sister first (Miku), all the way to chatting with her in Ollama.
Only then do the other four.** You'll learn a lot from the first one.

---

## What's in this folder

| File | What it does | Uses AI? |
|---|---|---|
| `common.py` | Shared settings and helpers. | – |
| `step1_build_canon.py` | **Fix A + C.** Merges split subtitle captions into full turns; cuts real scenes into short chats. | No |
| `step2_reverse_prompts.py` | **Fix B.** For each real line she says, writes a message the user could have sent her. Her line is never changed. | Yes |
| `step3_synthetic.py` | **Fix D.** Writes new everyday conversations in her style. | Yes |
| `step4_review.py` | You read the step 3 chats and keep or reject each one. | No |
| `step5_assemble.py` | Combines everything into `final/train.jsonl` + `final/val.jsonl`. | No |
| `train_qlora_colab.ipynb` | The training notebook for Google Colab. | – |
| `ollama/Modelfile_<Sister>` | Lets you chat with the trained sister on your PC. | – |
| `llm.py` | Talks to the AI used by steps 2 and 3 (Ollama or Claude). | – |
| `output/<Sister>/` | Everything the steps produce. | – |

All commands below are run in **PowerShell inside the `v2_pipeline` folder**.
To get there: open the folder in File Explorer, click the address bar, type
`powershell`, press Enter.

---

## Step 1 — Build the canon data (already done, takes 2 seconds)

```
py step1_build_canon.py
```

What changed compared to your old data:
* Subtitle captions from the same person are glued back into one turn.
  Miku's average reply went from **4.2 words → 8.7 words**.
* "Sister" in the subtitles is Raiha, so it's now labelled Raiha.
* Scenes are split on 10-second pauses (217 scenes instead of 61), and chats
  are cut so she's actually taking part in them.

## Step 2 — Reverse prompts (the biggest quality gain)

Test on 16 lines first and read the result:

```
py step2_reverse_prompts.py --sister Miku --limit 16
```

Then the full run for Miku:

```
py step2_reverse_prompts.py --sister Miku
```

You can press **Ctrl+C** anytime; running the same command again continues
where it stopped.

## Step 3 — Synthetic conversations

```
py step3_synthetic.py --sister Miku --count 3      # test
py step3_synthetic.py --sister Miku --count 200    # full run
```

This uses **roleplay mode**: two AIs actually chat, one message at a time.
One plays her (`--her-model`), using her character card and ~15 of her real
anime lines as a voice guide; the other plays Fuutarou, a classmate or a
friend (`--user-model`). Roleplay models are much better at this than at
writing a whole script in one go.

**One-time setup** (the models were already downloaded on 2026-11-09). In
PowerShell inside `v2_pipeline\ollama`:
```
ollama create rocinante-xl -f Modelfile_rocinante-xl
```
This registers Rocinante with the chat format its author recommends. Don't
use the raw `hf.co/...` name in the scripts: Ollama gives it the wrong format.

**Which model plays her (tested 2026-11-09, same 3 scenarios each):**

| Model | Verdict |
|---|---|
| Rocinante-XL-16B | Most human and emotional ("...It's not about any guy. I just started overthinking everything again"). More slip-ups (repeats, emojis, name typos), which the filters and your review catch. **Default, 2 of every 3 chats.** |
| gemma4:12b | Quiet and in character, but bland and repetitive. **Default, 1 of every 3 chats**, for consistency. Also plays Fuutarou best. |
| Stheno 8B | Human, but not Miku ("Totally! … haha"). |
| Rocinante-X-12B | Broke character ("I'll reply as Miku Nakano… following all guidelines"). |

Speed on your laptop: about 1.5–3 minutes per chat, so **200 chats ≈ 7 hours**
(run it overnight, or use `--count 100`).

Automatic filters throw away chats where she: replies with only \*actions\*,
repeats herself, uses emojis or "~", talks about her instructions, says her
own name, or sounds like an assistant. Your review (step 4) catches the rest.

* Try a different actor: `--her-model stheno:latest`
* Mix actors (random per chat): `--her-model modelA,modelB`
* **Round 2 (the best data):** after you've trained her LoRA and loaded it in
  Ollama as e.g. `miku`, run step 3 again with `--her-model miku`. Her own
  fine-tuned voice makes the most natural chats. Review them in step 4, add
  them in step 5, and train again.

## Step 4 — Review (important!)

```
py step4_review.py --sister Miku
```

Press `y` to keep, `n` to reject, `q` to save and stop. Reject a chat if:
* she addresses people wrong (Miku says "Futaro", Ichika "Futaro-kun",
  Yotsuba "Uesugi-san", Itsuki "Uesugi-kun"),
* she sounds like a polite customer-service bot,
* it contradicts the anime (e.g. Fuutarou "remembering" her as a child),
* it's just bland.

Rejecting 20–40% of AI-written chats is normal.

## Step 5 — Build the final files

```
py step5_assemble.py --sister Miku
```

Add `--only-approved` to use only the chats you pressed `y` on.
The result is `output/Miku/final/train.jsonl` and `val.jsonl`.

## Step 6 — Train on Google Colab

1. Go to https://colab.research.google.com → **File → Upload notebook** →
   pick `train_qlora_colab.ipynb`.
2. **Runtime → Change runtime type → T4 GPU**.
3. Run the cells top to bottom. Each one explains itself.
4. Section 9 lets you chat with her right in Colab. Try her before exporting.

You do **not** upload the 16 GB model from your PC; Colab downloads Stheno
itself.

## Step 7 — Chat with her on your PC

1. Put the downloaded `.gguf` and `ollama/Modelfile_Miku` in one folder.
2. Open `Modelfile_Miku` in Notepad, fix the `FROM` line to match the .gguf
   file name.
3. In PowerShell in that folder:
   ```
   ollama create miku -f Modelfile_Miku
   ollama run miku
   ```

---

## Choosing the AI for steps 2 and 3

| | Ollama (local, `--backend ollama`) | Claude API (`--backend claude`) |
|---|---|---|
| Cost | Free | Rough guess for **all five sisters**: Opus ≈ $35, Sonnet ≈ $18, Haiku ≈ $2 |
| Speed | Miku alone: step 2 ≈ 2 h, step 3 (200 chats) ≈ 2.5 h | Minutes |
| Quality | About 70% of step 2 and maybe half of step 3 are usable; you'll reject more in step 4 | Much more natural dialogue and better canon knowledge |
| Setup | Ollama running (already installed); default model `gemma4:12b` | `py -m pip install anthropic`, API key from console.anthropic.com, then `$env:ANTHROPIC_API_KEY = "sk-ant-..."` |

The cost numbers are estimates. Always do a `--limit 16` / `--count 3` test
first and check the usage page on console.anthropic.com before a full run.
To pick a cheaper Claude model: `--model claude-sonnet-5-5` or
`--model claude-haiku-5-5`.

You can mix them, e.g. Ollama for step 2 and Claude for step 3.

To use a different local model, add `--model`, e.g. `--model stheno:latest`.
In a side-by-side test (2026-11-09) gemma4:12b beat Stheno at both steps:
Stheno often wrote the user's message from the wrong person's side, and its
Miku came out cheerful and chatty ("Sure thing, Futaro! Thanks for
suggesting it!") instead of quiet and hesitant. Stheno is still the model you
*train*; its human feel comes from there, not from the data generator.

---

## Things you should check yourself

**The character cards** (`<Sister>_AI/knowledge/personality.json`) were
written by an AI earlier and are put at the top of every training example,
so mistakes there get learned. I couldn't confirm these, please check:
* Movie stage: Nino "leads the takoyaki stall" and Miku "leads the pancake
  stall". Is that the right way round?
* Nino Season 2: is her haircut really in the *Seven Goodbyes* arc?
* Itsuki Season 2: the "Rena" disguise "by the lake".

If you edit a card, re-run step 1 and step 5 (steps 2–4 don't need redoing).

**Subtitle speaker names** are best-effort (the subtitle file says so). In
the disguise episodes, some lines may be credited to the wrong sister.

---

## Reading the training numbers (Colab step 8)

* **Training loss** going down = it's learning.
* **Validation loss** = the honest score on data it never trained on.
  - Goes down then flattens → good.
  - Goes back **up** for several checkpoints → it's memorising lines
    ("overfitting"). Retrain with `EPOCHS = 1`.
* Don't chase a tiny number. A loss around 1.5–2.0 is normal for dialogue.

## If something goes wrong

* **"CUDA out of memory"** in Colab → set `BATCH_SIZE = 1` and
  `GRAD_ACCUM = 8`, then Runtime → Restart session and run again.
* **Colab disconnected** → you have to rerun from the top; keep the tab open.
* **A Colab cell errors after Unsloth updates** → copy the error message and
  ask me. Library versions change often.
* **Step 2/3 says "AI call failed"** → is the Ollama app running? Try
  `ollama list` in PowerShell.
* **She replies with very short lines** → you need more step 2/3 data, or
  `--only-approved` is filtering too much.
