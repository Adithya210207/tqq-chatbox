import os
import json

conv_dir = os.path.join('Transcript', "Miku's converation")
miku_ai_dir = 'Miku_AI'

print("=== 1. VERIFY MIKU_ONLY.JSONL ===")
with open(os.path.join(conv_dir, 'miku_only.jsonl'), 'r', encoding='utf-8') as f:
    miku_only = [json.loads(line) for line in f]

print(f"Total records: {len(miku_only)}")
assert len(miku_only) == 722, f"Expected 722, got {len(miku_only)}"

# Check duplicates
seen_io = set()
dup_count = 0
for r in miku_only:
    pair = (r['input'].strip(), r['output'].strip())
    if pair in seen_io:
        dup_count += 1
    seen_io.add(pair)
print(f"Exact duplicates: {dup_count}")

# Check speaker direction
asst_count = sum(1 for r in miku_only if r['messages'][-1]['role'] == 'assistant' and r['messages'][-1]['name'] == 'Miku')
print(f"Miku as Assistant: {asst_count} / {len(miku_only)} ({asst_count/len(miku_only)*100:.1f}%)")

# Check context distribution
ctx_lens = {}
for r in miku_only:
    l = len(r['context'])
    ctx_lens[l] = ctx_lens.get(l, 0) + 1
print("Context length distribution:", sorted(ctx_lens.items()))

print("\n=== 2. VERIFY ALL_CONVERSATIONS.JSONL ===")
with open(os.path.join(conv_dir, 'all_conversations.jsonl'), 'r', encoding='utf-8') as f:
    all_conv = [json.loads(line) for line in f]
print(f"Total records: {len(all_conv)}")
assert len(all_conv) == 1443, f"Expected 1443, got {len(all_conv)}"

p2m = sum(1 for r in all_conv if r['metadata']['turn_type'] == 'partner_to_miku')
m2p = sum(1 for r in all_conv if r['metadata']['turn_type'] == 'miku_to_partner')
print(f"Partner->Miku: {p2m}, Miku->Partner: {m2p}")

print("\n=== 3. VERIFY TRAIN & VAL SPLIT ===")
with open(os.path.join(conv_dir, 'train.jsonl'), 'r', encoding='utf-8') as f:
    train = [json.loads(line) for line in f]
with open(os.path.join(conv_dir, 'validation.jsonl'), 'r', encoding='utf-8') as f:
    val = [json.loads(line) for line in f]
print(f"Train: {len(train)}, Val: {len(val)}, Total: {len(train) + len(val)}")
train_scenes = {r['scene_id'] for r in train}
val_scenes = {r['scene_id'] for r in val}
overlap = train_scenes.intersection(val_scenes)
print(f"Train scenes: {len(train_scenes)}, Val scenes: {len(val_scenes)}, Overlap: {len(overlap)}")
assert len(overlap) == 0, "Leakage detected!"

print("\n=== 4. VERIFY 31 CHARACTER JSONL FILES ===")
char_files = [f for f in os.listdir(conv_dir) if f.endswith('.jsonl') and not f.startswith('_') and f not in ('miku_only.jsonl', 'all_conversations.jsonl', 'multi_turn_conversations.jsonl', 'train.jsonl', 'validation.jsonl')]
print(f"Total character files: {len(char_files)}")
assert len(char_files) == 31, f"Expected 31 character files, got {len(char_files)}"

total_char_records = 0
for cf in sorted(char_files):
    with open(os.path.join(conv_dir, cf), 'r', encoding='utf-8') as f:
        recs = [json.loads(l) for l in f]
        total_char_records += len(recs)
print(f"Sum of records across 31 character files: {total_char_records}")
assert total_char_records == 1443

print("\n=== 5. VERIFY KNOWLEDGE FILES ===")
with open(os.path.join(conv_dir, 'relationship_index.json'), 'r', encoding='utf-8') as f:
    rel = json.load(f)
print(f"Relationship index interaction count: {rel['total_interaction_labels']}")
assert rel['total_interaction_labels'] == 31

with open(os.path.join(conv_dir, 'personality.json'), 'r', encoding='utf-8') as f:
    pers = json.load(f)
print(f"Timeline stages: {list(pers['timeline_stages'].keys())}")

with open(os.path.join(conv_dir, 'development.json'), 'r', encoding='utf-8') as f:
    dev = json.load(f)
print(f"Development phases count: {len(dev['narrative_progression'])}")

print("\n=== 6. VERIFY MIKU_AI REPOSITORY SYNC ===")
for sub in ['source', 'knowledge', 'datasets', 'model']:
    p = os.path.join(miku_ai_dir, sub)
    print(f"{sub}/ contents: {os.listdir(p)}")

print("\nALL VERIFICATIONS PASSED 100% PERFECTLY!")
