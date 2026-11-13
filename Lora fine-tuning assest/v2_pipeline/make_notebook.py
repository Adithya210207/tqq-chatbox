"""Builds train_qlora_colab.ipynb. You don't need to run this; the notebook is already built.
(Re-run with `py make_notebook.py` only if you edit the cells below.)"""
import json
from pathlib import Path

cells = []


def md(text):
    cells.append({"cell_type": "markdown", "metadata": {}, "source": text.strip("\n")})


def code(text):
    cells.append({"cell_type": "code", "metadata": {}, "execution_count": None,
                  "outputs": [], "source": text.strip("\n")})


md("""
# QLoRA training for one Nakano sister

**How to use this notebook:**
1. Open it on the **demo account** (Colab `?authuser=1`). Menu **Runtime → Change runtime type → T4 GPU** → Save.
2. Edit the settings cell (step 3): `SISTER`, `VERSION`, `LOAD_IN_4BIT`.
3. Upload `train.jsonl` and `val.jsonl` from `v2_pipeline/output/<Sister>/final/` with the **Files panel** (folder icon on the left → "Upload to session storage").
4. Run each cell **in order**, top to bottom. Wait for each one to finish (✓) before the next.
5. Before the Drive cell (step 11): **empty this account's Drive Bin**, or the copy can hang at "flushing" (binned files still use space).

Training one sister takes about **20–60 minutes** on the free T4. Colab disconnects if you leave the tab idle too long, so keep it open.
""")

md("## 1. Check that you got a GPU\nYou should see a table mentioning **Tesla T4**. If you see an error, redo step 1 above.")
code("!nvidia-smi")

md("## 2. Install the training tools (≈2–4 min)\n[Unsloth](https://github.com/unslothai/unsloth) makes QLoRA faster and lighter so it fits on the free GPU.")
code("""
%%capture
!pip install unsloth
""")

md("""
## 3. Settings — the only cell you normally edit
* `SISTER` — the sister being trained (also used in file names).
* `VERSION` — `v1`, `v2`, … so a retrain never overwrites an older file.
* `LOAD_IN_4BIT` — `True` = 4-bit (Miku's winning recipe). `False` = 8-bit.
* `EPOCHS` — how many times it reads your data. Start with 2. If the **validation loss** (step 8) starts going *up* while training loss goes down, use fewer.
* `LEARNING_RATE` — how big each correction is. 1e-4 is a gentle, safe value.
""")
code("""
SISTER = "Miku"                         # Ichika / Nino / Miku / Yotsuba / Itsuki
VERSION = "v2"                           # goes into every file name
LOAD_IN_4BIT = True                      # True = 4-bit (winning recipe), False = 8-bit
BASE_MODEL = "Sao10K/L3-8B-Stheno-v3.2"  # downloaded automatically from Hugging Face
MAX_SEQ_LEN = 2048                       # longest example, in tokens

EPOCHS = 2
LEARNING_RATE = 1e-4
LORA_RANK = 16                           # adapter size; 16 is a good default
BATCH_SIZE = 2
GRAD_ACCUM = 4                           # 2 x 4 = 8 examples per update step

# File names (no need to edit)
s = SISTER.lower()
ADAPTER_DIR = f"{s}_{VERSION}_lora"              # e.g. nino_v1_lora
GGUF_DIR = f"{s}{VERSION}_gguf"                   # e.g. ninov1_gguf
DRIVE_NAME = f"{s}-{VERSION}-stheno-q8_0.gguf"    # e.g. nino-v1-stheno-q8_0.gguf
print(SISTER, VERSION, "4-bit" if LOAD_IN_4BIT else "8-bit", EPOCHS, "epochs ->", ADAPTER_DIR, GGUF_DIR, DRIVE_NAME)
""")

md("## 4. Check your data is uploaded\nUpload `train.jsonl` and `val.jsonl` with the **Files panel** first (more reliable than an upload button). This cell only checks they are there and counts the chats.")
code("""
import os
missing = [f for f in ("train.jsonl", "val.jsonl") if not os.path.exists(f)]
assert not missing, f"Missing {missing}: upload them with the Files panel (folder icon, left), then re-run this cell"
for f in ("train.jsonl", "val.jsonl"):
    print(f, sum(1 for _ in open(f, encoding="utf-8")), "chats")
first = open("train.jsonl", encoding="utf-8").readline()
assert SISTER in first, f"train.jsonl does not look like {SISTER}'s data. Did you upload the right files?"
""")

md("## 5. Load Stheno in 4-bit or 8-bit (≈3–5 min the first time — it downloads the model)\nThis is the \"Q\" in QLoRA: the big model is squeezed so it fits in the T4's memory. Its original weights are frozen. If you change `LOAD_IN_4BIT` later, do **Runtime → Restart session** first.")
code("""
from unsloth import FastLanguageModel

model, tokenizer = FastLanguageModel.from_pretrained(
    model_name=BASE_MODEL,
    max_seq_length=MAX_SEQ_LEN,
    load_in_4bit=LOAD_IN_4BIT,
    load_in_8bit=not LOAD_IN_4BIT,
    dtype=None,            # auto-picks the best format for this GPU
)
""")

md("## 6. Attach the LoRA adapter\nThese are the small trainable layers — the only part that learns. Everything else stays frozen.")
code("""
model = FastLanguageModel.get_peft_model(
    model,
    r=LORA_RANK,
    lora_alpha=LORA_RANK,
    lora_dropout=0,
    target_modules=["q_proj", "k_proj", "v_proj", "o_proj",
                    "gate_proj", "up_proj", "down_proj"],
    bias="none",
    use_gradient_checkpointing="unsloth",   # saves memory
    random_state=3407,
)
model.print_trainable_parameters()
""")

md("""
## 7. Turn the conversations into the format Stheno understands
Stheno is built on Llama 3, so every chat is written in the Llama-3 chat template (special tokens around system / user / assistant turns).
The printout shows one example exactly as the model will see it.
""")
code("""
from datasets import load_dataset
from unsloth.chat_templates import get_chat_template

tokenizer = get_chat_template(tokenizer, chat_template="llama-3")

def to_text(batch):
    return {"text": [tokenizer.apply_chat_template(m, tokenize=False, add_generation_prompt=False)
                     for m in batch["messages"]]}

data = load_dataset("json", data_files={"train": "train.jsonl", "validation": "val.jsonl"})
data = data.map(to_text, batched=True, remove_columns=data["train"].column_names)
print(data)
print("\\n----- example -----\\n")
print(data["train"][0]["text"][:1500])
""")

md("""
## 8. Train
**Loss-only-on-her-lines:** `train_on_responses_only` makes the model learn only from what *she* says, not from the user's messages.

While it runs you'll see a table:
* **Training Loss** should go down steadily.
* **Validation Loss** is the honest score (data it never trains on). It should go down, then level off. If it climbs for several checkpoints in a row, the model is memorising — next time lower `EPOCHS`.
""")
code("""
from trl import SFTTrainer, SFTConfig
from unsloth import is_bfloat16_supported
from unsloth.chat_templates import train_on_responses_only

trainer = SFTTrainer(
    model=model,
    tokenizer=tokenizer,
    train_dataset=data["train"],
    eval_dataset=data["validation"],
    args=SFTConfig(
        dataset_text_field="text",
        per_device_train_batch_size=BATCH_SIZE,
        gradient_accumulation_steps=GRAD_ACCUM,
        num_train_epochs=EPOCHS,
        learning_rate=LEARNING_RATE,
        warmup_ratio=0.05,
        lr_scheduler_type="cosine",
        optim="adamw_8bit",
        weight_decay=0.01,
        fp16=not is_bfloat16_supported(),
        bf16=is_bfloat16_supported(),
        logging_steps=10,
        eval_strategy="steps",
        eval_steps=25,
        save_strategy="no",
        output_dir="outputs",
        report_to="none",
        seed=3407,
    ),
)
trainer = train_on_responses_only(
    trainer,
    instruction_part="<|start_header_id|>user<|end_header_id|>\\n\\n",
    response_part="<|start_header_id|>assistant<|end_header_id|>\\n\\n",
)
stats = trainer.train()
print(stats)
""")

md("""
## 9. Talk to her
Change `stage` and `partner`, then run the next cell as many times as you like with different messages.
""")
code("""
import json
FastLanguageModel.for_inference(model)

# Re-use the exact character card she was trained with.
cards = {}
for line in open("train.jsonl", encoding="utf-8"):
    sys_prompt = json.loads(line)["messages"][0]["content"]
    for st in ("Season 1", "Season 2", "Hinode School Festival"):
        if st in sys_prompt and st not in cards:
            cards[st] = sys_prompt.split(" You are talking with")[0]

def make_system(stage="Season 2", partner="Fuutarou"):
    return cards[stage] + f" You are talking with {partner}."

history = []

def chat(message, stage="Season 2", partner="Fuutarou", reset=False):
    global history
    if reset:
        history = []
    history.append({"role": "user", "content": message})
    msgs = [{"role": "system", "content": make_system(stage, partner)}] + history
    ids = tokenizer.apply_chat_template(msgs, add_generation_prompt=True, return_tensors="pt").to("cuda")
    out = model.generate(input_ids=ids, max_new_tokens=200, do_sample=True,
                         temperature=0.8, top_p=0.9, repetition_penalty=1.1)
    reply = tokenizer.decode(out[0][ids.shape[1]:], skip_special_tokens=True).strip()
    history.append({"role": "assistant", "content": reply})
    print(f"{SISTER}: {reply}")

print("Stages available:", list(cards))
""")
code("""
chat("Hey, what are you doing here so late?", reset=True)
""")
code("""
chat("I brought you something. Guess what it is.")
""")

md("## 10. Save the adapter (≈100–200 MB)\nThis small folder *is* your trained sister. It is zipped here and copied to Drive together with the GGUF in step 11.")
code("""
model.save_pretrained(ADAPTER_DIR)
tokenizer.save_pretrained(ADAPTER_DIR)
ADAPTER_ZIP = f"{ADAPTER_DIR}_{'4bit' if LOAD_IN_4BIT else '8bit'}_r{LORA_RANK}_{EPOCHS}ep_facts.zip"
!zip -qr {ADAPTER_ZIP} {ADAPTER_DIR}
print("Saved", ADAPTER_ZIP)
""")

md("""
## 11. Make a GGUF and copy it to Drive (≈15–25 min)
This merges the adapter into Stheno and compresses it to one `q8_0` `.gguf` file (~8.5 GB) for Ollama on your PC.
Then it copies the GGUF **and** the adapter zip to **this account's** Google Drive (My Drive). A direct download is far too slow for 8.5 GB.
* Empty this account's Drive **Bin** first, or the copy can hang at "flushing".
* Allow the Drive pop-up if it appears. Only the account running Colab can be mounted.
""")
code("""
model.save_pretrained_gguf(GGUF_DIR, tokenizer, quantization_method="q8_0")
import glob
# newer Unsloth writes to "<dir>_gguf/", older to "<dir>/"
gguf_files = glob.glob(f"{GGUF_DIR}*/*.gguf")
print(gguf_files)
assert gguf_files, "No GGUF found"
""")
code("""
from google.colab import drive
import shutil, os
drive.mount("/content/drive")
src = gguf_files[0]
shutil.copy(src, f"/content/drive/MyDrive/{DRIVE_NAME}")
shutil.copy(ADAPTER_ZIP, f"/content/drive/MyDrive/{ADAPTER_ZIP}")
print("Copied", src, os.path.getsize(src), "bytes ->", DRIVE_NAME)
print("Copied", ADAPTER_ZIP)
drive.flush_and_unmount()
print("Flushed to Drive. Note the byte count above to check the download on the PC.")
""")

md("""
## 12. Run her in Ollama on your PC
Download the `.gguf` from Drive into `v2_pipeline/ollama/` and check its size matches the byte count above.
Make `ollama/Modelfile_<Sister>V1` with the same cheat sheet as training and `FROM ./<the gguf name>`. Then in PowerShell, inside `v2_pipeline`:

```
ollama create Ichika_ryuu_v1 -f ollama/Modelfile_IchikaV1
ollama run Ichika_ryuu_v1
```
""")

nb = {"cells": cells, "metadata": {
    "accelerator": "GPU", "colab": {"provenance": [], "gpuType": "T4"},
    "kernelspec": {"display_name": "Python 3", "name": "python3"},
    "language_info": {"name": "python"}}, "nbformat": 4, "nbformat_minor": 0}

out = Path(__file__).with_name("train_qlora_colab.ipynb")
out.write_text(json.dumps(nb, indent=1, ensure_ascii=False), encoding="utf-8")
print("Wrote", out)
