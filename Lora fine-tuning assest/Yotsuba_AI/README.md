# Yotsuba Nakano AI Model Training & Fine-Tuning Guide

This directory contains the complete knowledge base, verified training datasets, and guidelines for fine-tuning a persona model of Yotsuba Nakano from *The Quintessential Quintuplets*.

## Project Architecture

* **`source/`**: Subtitle transcripts and `master_transcript.json`.
* **`knowledge/`**:
  * `personality.json`: Core identity, voice, mannerisms, and 4 selectable `timeline_stages` (`season_1`, `season_2`, `movie`, `adult`).
  * `development.json`: 7 chronological narrative phases with plot milestones (Canon) vs. behavioral evolution (Analysis).
  * `relationship_index.json`: Complete relationship profiles for all 47 interaction labels.
* **`datasets/`**:
  * `yotsuba_only.jsonl`: 750 pure SFT training examples (Yotsuba is exclusively Assistant, with 3–5 lines of context).
  * `train.jsonl` (669 examples) & `validation.jsonl` (81 examples): **Leakage-free scene-based split**.
  * `multi_turn_conversations.jsonl`: Reconstructed continuous scene dialogues.
  * `all_conversations.jsonl`: 1500 two-way exchanges in strict global chronological order.

## Recommended Hyperparameters (LoRA / QLoRA)

```yaml
learning_rate: 2e-4
lr_scheduler: "cosine"
warmup_ratio: 0.05
epochs: 3
per_device_train_batch_size: 4
gradient_accumulation_steps: 4
max_seq_length: 2048
lora_r: 16
lora_alpha: 32
lora_dropout: 0.05
target_modules: ["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"]
```
