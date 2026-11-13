# OFFICIAL PLAN: Nino, Yotsuba, Itsuki, Ichika

Approved by the user on 2026-11-11. Do the sisters **one at a time, in this order**, with the same steps for each.
The recipe and lessons behind it are in `SISTERS_PLAN.md`.

## The four sisters

| Order | Sister | Born | Canon lines | Calls Futaro | Reverse-prompt files | Ollama name |
|---|---|---|---|---|---|---|
| 1 | **Nino** | 2nd | 844 | "Futaro" / "Fu-kun" | ~7 | `Nino_ryuu_v1` |
| 2 | **Yotsuba** | 4th | 766 | "Uesugi-san" | ~6 | `Yotsuba_ryuu_v1` |
| 3 | **Itsuki** | 5th (youngest) | 720 | "Uesugi-kun" | ~6 | `Itsuki_ryuu_v1` |
| 4 | **Ichika** | 1st (eldest) | 667 | "Futaro-kun" | ~5 | `Ichika_ryuu_v1` |

In the steps below, **`<Sister>`** means the name (e.g. `Nino`) and **`<sister>`** means the lowercase name (e.g. `nino`).

## Progress

| Part | Nino | Yotsuba | Itsuki | Ichika |
|---|---|---|---|---|
| 0. Tool fixes (once) | ✅ | — | — | — |
| 1. Fact sheet | ✅ | ✅ | ✅ | ✅ |
| 2. Reverse prompts | ✅ 616 | ✅ 595 | ✅ 452 | ✅ 343 (18 from lines labeled Itsuki) |
| 3. Claude chats | ✅ 80 | ✅ 90 | ✅ 150 | ✅ 212 |
| 4. ChatGPT + Gemini | ✅ 100 | ✅ 100 | ✅ 98 (re-checked) | ✅ 98 (re-checked) |
| 5. Build training files | ✅ 853/114 | ✅ 837/103 | ✅ 728/98 | ✅ 699/77 |
| 6. Her test | ✅ | ✅ | ✅ | ✅ |
| 7. Train on Colab | ✅ | ✅ (retrained) | ✅ | ✅ (authuser=5) |
| 8. Get her onto the PC | ✅ | ✅ | ✅ | ✅ |
| 9. Test her | ✅ won 74–12 | ✅ won 68–21 | ✅ won 82–4 (user 20 pairs pending) | ✅ won 83–11 (user 20 pairs pending) |
| 10. Back up and clean up | ✅ | ✅ | ✅ | ✅ |

---

### 🔧 Part 0: one-time tool fixes (Claude, ~30 min, before the first sister only)
1. `eval/score.py`: let it take the two model names in the command
2. `eval/run_compare.py`: add `--sister` so it loads `eval/<sister>_test_100.py`
3. `eval/fact_test.py`: add `--sister` so it loads that sister's facts and tricks
4. `make_notebook.py`: add the "files already uploaded" check cell and the Drive-save cell, then rebuild `train_qlora_colab.ipynb`
5. `import_external.py` + `gold_tools.py`: add a filter for "he/him" about Futaro while talking to him
6. `common.py`: add the **5 behavior rules** to the training rules, using each sister's address form from the table above

### 📝 Part 1: her fact sheet
7. Claude writes `facts/<Sister>.txt`: 4 sisters, her birth order (table above), May 5, mother Rena, Maruo, Kurobara → Asahiyama, her hobbies and likes, Raiha, the no-assistant rule
8. 🙋 **User checks it against the anime.** Anything Claude is unsure of is marked ⚠️.
9. Claude fixes whatever the user corrects

### ✍️ Part 2: reverse prompts (Claude, ~1.5–2 h)
10. Read her canon lines (`output/<Sister>/canon_turns.jsonl`)
11. Write a user message for each line, about 130 lines per file: `reverse_claude/<sister>_rp_01.py`, `_02.py`, …
12. Skip lines that don't fit (wrong speaker, said to someone else, disguise scenes, epilogue)
13. `py reverse_claude_tools.py --sister <Sister>` → `output/<Sister>/reverse_claude.jsonl`

### 💬 Part 3: Claude's chats (Claude, ~1 h)
14. **50 gold chats**: `gold/<sister>_batch1.py` … `_batch5.py`
15. **30 fact/trick chats**: `gold/<sister>_batch6.py`
16. Run each file (`py gold\<sister>_batchN.py`) → `output/<Sister>/gold.jsonl`

### 🤖 Part 4: ChatGPT + Gemini (user + Claude, ~30 min)
17. Claude writes `prompts/PROMPT_ChatGPT_<Sister>.md` and `prompts/PROMPT_Gemini_<Sister>.md` (50 different topics each)
18. 🙋 **User pastes** each prompt into ChatGPT and Gemini
19. 🙋 **User pastes** their answers into `output/<Sister>/raw_chatgpt.txt` and `output/<Sister>/raw_gemini.txt`
20. Import:
    - `py import_external.py --sister <Sister> --source chatgpt --file output\<Sister>\raw_chatgpt.txt`
    - `py import_external.py --sister <Sister> --source gemini --file output\<Sister>\raw_gemini.txt`
21. Claude reads all 100 chats and fixes mistakes in `output/<Sister>/external_fixes.json` (swapped roles, British spelling, overused phrases)

### 📦 Part 5: build the training files (Claude, 5 min)
22. `py step5_assemble.py --sister <Sister>` → `output/<Sister>/final/train.jsonl` + `val.jsonl`
23. Check the counts (about 900 train / 100 val) and that **every chat has the cheat sheet**

### 🧪 Part 6: her test (Claude, ~30 min)
24. Write `eval/<sister>_test_100.py`: 100 test chats (daily life, tutoring, sisters, her hobbies, feelings, facts, tricks)
25. Add her facts and tricks to the fact test

### 🏋️ Part 7: train on Colab (Claude, ~1 h 30 min)
26. Open the notebook on the **demo** account (`authuser=1`) and check that it's on a **T4** GPU
27. Settings: `SISTER = "<Sister>"`, **4-bit**, **2 rounds**, folders `<sister>_v1_lora` and `<sister>v1_gguf`
28. Upload `train.jsonl` + `val.jsonl` through the Files panel
29. Run everything up to the chat cell. Training takes about 50 min, with a report every 5 min.
30. Save the adapter, then make the GGUF (about 15 min)
31. 🗑️ **Check that the demo account's Bin is empty**
32. Copy the GGUF to the demo account's Drive. 🙋 **User allows the Drive pop-up** if it appears.

### ⬇️ Part 8: get her onto the PC (Claude, ~20 min)
33. Download `<sister>-v1-stheno-q8_0.gguf` from Drive to the PC
34. Check the size matches exactly
35. Move it to the `ollama` folder and make `Modelfile_<Sister>V1` (same cheat sheet as training)
36. `ollama create <Sister>_ryuu_v1 -f ollama\Modelfile_<Sister>V1`

### 📊 Part 9: test her (Claude + user, ~1 h)
37. Make a **baseline**, `<sister>-base`: plain Stheno with the same cheat sheet, no training
38. `py eval\fact_test.py --sister <Sister> <Sister>_ryuu_v1 <sister>-base`
39. `py eval\run_compare.py --sister <Sister> <Sister>_ryuu_v1 <sister>-base`
40. `py eval\score.py auto <Sister>_ryuu_v1 <sister>-base`, then `blind`
41. Claude judges all 100 blind pairs
42. 🙋 **User judges 20 pairs**
43. Unblind, and Claude explains the results simply

### 🧹 Part 10: back up and clean up (Claude, ~15 min)
44. Share the GGUF with Adithya (adithya210207.v) → **"Make a copy"** → rename it (no "Copy of")
45. Delete the demo account's copy **and empty its Bin**
46. Delete the duplicate GGUF in the `ollama` folder (Ollama keeps its own copy)
47. Delete `<sister>-base`
48. Save the adapter zip to `output/<Sister>/adapters/`
49. Tick her column in the progress table, then update **HANDOFF.md** and memory

(Claude confirms with the user before each permanent delete in steps 45–47.)

---

### ✅ A sister is done when
- `<Sister>_ryuu_v1` is in Ollama
- She beats plain Stheno in the blind test
- Facts at about 40+/45, tricks at about 2/18 or fewer
- A backup is in Adithya's Drive

### 🙋 The user's 4 jobs per sister
| Step | Job |
|---|---|
| 8 | Check her fact sheet |
| 18–19 | Paste the ChatGPT/Gemini prompts and their answers |
| 32 | Allow the Drive pop-up (maybe) |
| 42 | Judge 20 pairs |

### ⏱ Time
About **1 day per sister** (mostly waiting), so about **4 days** for all four. Free Colab GPU allows about one sister per day per account.
