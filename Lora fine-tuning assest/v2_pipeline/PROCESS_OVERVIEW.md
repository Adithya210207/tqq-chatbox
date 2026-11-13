# How the Miku LoRA is made: the whole process in plain words

*(Written 2026-11-09, during Miku's overnight run.)*

## The goal

Stheno already knows how to chat. Training a **LoRA** on Miku teaches it to talk *like Miku*. To do that, it needs lots of example chats where someone talks to Miku and she answers in her own voice.

## The problem we started with

Your subtitles give us Miku's real lines, but:

- the lines were chopped into tiny pieces,
- she's mostly talking to her sisters, not to "you",
- 732 lines aren't enough examples on their own.

So we build the training data in 5 steps.

## The steps

### Step 1: clean up the real anime lines ✅ done

- Subtitle pieces from the same person get glued back into full sentences.
- The anime gets cut into short scenes, and each scene becomes one example chat.
- No AI is used here. It's 100% real dialogue.

### Step 2: reverse prompts ✅ done (653 lines)

- We take each real Miku line and ask an AI: *"What might someone have said to get this reply?"*
- Example: Miku's real line is *"...I don't like crowds."* The AI writes the message before it: *"Want to come to the festival with us?"*
- **Her line is never changed.** The AI only writes the other person's message.
- That gives 653 real Miku answers, each paired with a natural question.

### Step 3: roleplay chats ⏳ running overnight (200 chats)

- These are brand-new conversations about everyday topics: dinner, exams, gifts, jealousy...
- **Two AIs talk to each other:**
  - **Rocinante-XL plays Miku** (about 2 of every 3 chats; gemma4 plays her in the rest). It gets her character card plus 15 of her real lines as a voice guide.
  - **gemma4 plays the other person** (Fuutarou, a classmate or a new friend).
- Each chat is 6–10 messages, built one message at a time.
- Filters automatically throw out bad chats: repeats, emojis, saying her own name, assistant-style talk, and so on. About 2 in 3 chats pass.
- This teaches her to handle topics the anime never covered.

### Step 4: review 👀

- **Claude** goes through everything and fixes or rejects bad lines.
- Then **you** spot-check a few with `py step4_review.py --sister Miku`, pressing `y` or `n`.

### Step 5: assemble

- `py step5_assemble.py --sister Miku` combines everything into two files:
  - `train.jsonl` is what the LoRA learns from.
  - `val.jsonl` is about 10% held back to test whether it's really learning or just memorising.

## After the data

### Step 6: train on Google Colab

Upload `train_qlora_colab.ipynb` and the two files, pick the free T4 GPU, and run the cells. It takes about 30–60 minutes. You get back a Miku `.gguf` file.

### Step 7: chat with her on your laptop

Load that file into Ollama with `ollama create miku -f Modelfile_Miku` and talk to her with `ollama run miku`.

## Then

- **Round 2 (optional):** use your trained Miku to play herself in step 3 (`--her-model miku`). That gives better chats, so you train again.
- Repeat for Ichika, Nino, Yotsuba and Itsuki.

For the detailed commands and troubleshooting, see `GUIDE.md`.
