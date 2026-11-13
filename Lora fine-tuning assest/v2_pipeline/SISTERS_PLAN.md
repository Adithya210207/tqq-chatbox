# Plan for the other four sisters

> **Official step-by-step plan for all four sisters: `OFFICIAL_PLAN.md`** (approved 2026-11-11). This file holds the recipe, order and lessons behind it.

Written 2026-11-10, after Miku was finished (`Miku_ryuu_v1`).
Follow the same steps for each sister, in this order:

| # | Sister | Canon lines | Calls Futaro | Ollama name |
|---|---|---|---|---|
| 1 | **Nino** | 844 (most) | "Futaro" / "Fu-kun" | `Nino_ryuu_v1` |
| 2 | **Yotsuba** | 766 | "Uesugi-san" | `Yotsuba_ryuu_v1` |
| 3 | **Itsuki** | 720 | "Uesugi-kun" | `Itsuki_ryuu_v1` |
| 4 | **Ichika** | 667 | "Futaro-kun" | `Ichika_ryuu_v1` |

Step 1 (canon scenes) is already done for all four.

---

## The winning recipe (from Miku), don't change it

- Stheno 8B, **4-bit** base, LoRA rank 16, lr 1e-4
- **2 epochs** (1 epoch lost; 8-bit only tied)
- **Fact sheet inside every training example** (`facts/<Sister>.txt`)
- Ollama SYSTEM = exactly the training prompt; temperature 0.8
- Lesson: adding lines to SYSTEM **after** training did not help. Fixes must go **into the training data**.

`make_notebook.py` already defaults to 4-bit + 2 epochs.

---

## Step 0: one-time tool fixes (before Nino, about 30 min, Claude)

| Fix | Why |
|---|---|
| `score.py`: take the two model names as arguments | `MODELS` is hard-coded |
| `run_compare.py` / `fact_test.py`: add `--sister`, load `eval/<sister>_test_100.py` and that sister's facts and traps | both are Miku-only now |
| `make_notebook.py`: add the Drive-save cell + "files are already uploaded" check cell | what worked tonight |
| Add the "3rd-person Futaro" check to the synthetic/external filters | Miku's last real flaw |
| Put the 5 behavior rules (call him "you", don't invent history, short replies, ...) into the training rules | they only work if trained in |

---

## Per-sister steps

| Step | What | Who | Time |
|---|---|---|---|
| 1 | Write `facts/<Sister>.txt` | Claude drafts, **you check against the anime** | 15 min |
| 2 | Claude writes reverse prompts for her canon lines (`reverse_claude/<sister>_rp_*.py`, ~130 lines per file), fully on its own, no user input; skip wrong-speaker / wrong-addressee / disguise / epilogue lines | Claude | 1–2 h |
| 3 | Claude writes 50 gold chats + 30 fact/trap chats (`gold/<sister>_batch*.py`) | Claude | 1 h |
| 4 | Make `prompts/PROMPT_ChatGPT_<Sister>.md` and `PROMPT_Gemini_<Sister>.md`. You paste the answers into `output/<Sister>/raw_*.txt` | Claude + **you** | 30 min |
| 5 | `import_external.py` for both, then Claude reviews and fixes | Claude | 30 min |
| 6 | ~~Local LLM chats~~ **skipped** (user's decision 2026-11-10: they were the weakest source and only ~5% of Miku's data) | | |
| 7 | `step5_assemble.py --sister <Sister>`, check counts | Claude | 5 min |
| 8 | Write `eval/<sister>_test_100.py` + her facts/traps | Claude | 30 min |
| 9 | Train on Colab (**demo account**, `authuser=1`): upload train/val, Run before chat cell | Claude | ~60 min |
| 10 | Export GGUF, copy to demo account's Drive, download to PC | Claude (**you**: Drive pop-up the first time) | ~40 min |
| 11 | `ollama create <Sister>_ryuu_v1` | Claude | 5 min |
| 12 | Tests: fact test + 100-chat blind test vs **plain Stheno with the same prompt** (shows what the LoRA adds) | Claude | 30 min |
| 13 | **You** judge 20 blind pairs too (Claude isn't the only judge) | **you** | 20 min |
| 14 | Back up: share → "Make a copy" into the **adithya210207.v** Drive, rename, then delete the demo account's copy **and empty its Bin** | Claude | 10 min |
| 15 | Update HANDOFF.md | Claude | 5 min |

Data per sister: canon + reverse prompts, 50 Claude gold, 30 Claude fact/trap, 50 ChatGPT, 50 Gemini.

About **1 day per sister**, mostly waiting.

---

## Rules learned tonight

- **A free Drive is only 15 GB.** Empty its Bin before each new save, or the copy hangs at "flushing".
- Colab can only connect the Drive of the account running it. The Adithya Drive won't connect from the demo account.
- Direct Colab → PC download is slow (~70 min). Use Colab → Drive → PC (~20 min).
- Run long jobs detached (WMI), never as children of the Claude app.
- Free GPU: about 4 h per account per day. One sister's training + export takes about 1.5 h.
- Keep the old training files; save each adapter zip in `output/<Sister>/adapters/`.
- Facts we aren't sure of stay **flagged** for you to check. Never invent canon.

---

## Optional later

- **Miku v2:** retrain Miku with the step 0 fixes in her data (the 5 behavior rules + 3rd-person filter), then test against `Miku_ryuu_v1`.
- **Step 4 app:** a chat app that re-checks facts and regenerates wrong answers.
