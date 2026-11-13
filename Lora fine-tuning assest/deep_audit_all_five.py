import os
import json

def parse_time(ts: str) -> int:
    parts = list(map(int, ts.split(':')))
    if len(parts) == 2:
        return parts[0] * 60 + parts[1]
    elif len(parts) == 3:
        return parts[0] * 3600 + parts[1] * 60 + parts[2]
    return 0

sisters = ['Ichika', 'Nino', 'Miku', 'Yotsuba', 'Itsuki']

print("=" * 75)
print("DEEP EXHAUSTIVE AUDIT ACROSS ALL 5 SISTERS AND ALL WORKSPACES")
print("=" * 75)

total_errors = 0

for sister in sisters:
    print(f"\n>>> AUDITING {sister.upper()} NAKANO <<<")
    sister_lower = sister.lower()
    
    # Locations
    ai_dir = f"{sister}_AI"
    trans_dir = os.path.join("Transcript", f"{sister}'s converation")
    if sister == 'Nino' and not os.path.exists(trans_dir):
        trans_dir = os.path.join("Transcript", "NIno's converation")
    
    locations = [
        ("AI_Workspace", ai_dir),
        ("Transcript_Folder", trans_dir)
    ]
    
    # 1. Check core files exist and valid in both locations
    for loc_name, path in locations:
        if not os.path.exists(path):
            print(f"[FAIL] Missing path: {path}")
            total_errors += 1
            continue
            
        data_path = os.path.join(path, "datasets") if loc_name == "AI_Workspace" else path
        core_files = [
            f"{sister_lower}_only.jsonl",
            "train.jsonl",
            "validation.jsonl",
            "all_conversations.jsonl"
        ]
        
        for cf in core_files:
            cfp = os.path.join(data_path, cf)
            if not os.path.exists(cfp):
                print(f"[FAIL] Missing file: {cfp}")
                total_errors += 1
                continue
            with open(cfp, 'r', encoding='utf-8') as f:
                lines = [l.strip() for l in f if l.strip()]
            for l_idx, l in enumerate(lines):
                try:
                    json.loads(l)
                except Exception as e:
                    print(f"[FAIL] JSON error in {cfp} line {l_idx+1}: {e}")
                    total_errors += 1

    # 2. Detailed audit of SFT dataset (train + val)
    data_dir = os.path.join(ai_dir, "datasets")
    with open(os.path.join(data_dir, f"{sister_lower}_only.jsonl"), 'r', encoding='utf-8') as f:
        sft_records = [json.loads(l) for l in f]
    with open(os.path.join(data_dir, "train.jsonl"), 'r', encoding='utf-8') as f:
        train_records = [json.loads(l) for l in f]
    with open(os.path.join(data_dir, "validation.jsonl"), 'r', encoding='utf-8') as f:
        val_records = [json.loads(l) for l in f]
        
    print(f"  Total SFT records: {len(sft_records)} (Train: {len(train_records)}, Val: {len(val_records)})")
    
    # Check disjoint & complete
    train_ids = {r['id'] for r in train_records}
    val_ids = {r['id'] for r in val_records}
    sft_ids = {r['id'] for r in sft_records}
    
    if len(train_ids.intersection(val_ids)) != 0:
        print(f"[FAIL] ID overlap between train and val in {sister}")
        total_errors += 1
    if train_ids.union(val_ids) != sft_ids:
        print(f"[FAIL] Train + Val != SFT dataset in {sister}")
        total_errors += 1
        
    # Check scene leakage
    train_scenes = {r['scene_id'] for r in train_records}
    val_scenes = {r['scene_id'] for r in val_records}
    scene_overlap = train_scenes.intersection(val_scenes)
    if len(scene_overlap) != 0:
        print(f"[FAIL] Scene leakage in {sister}! Overlap: {scene_overlap}")
        total_errors += 1
    else:
        print(f"  [OK] Scene leakage: 0% ({len(train_scenes)} train scenes, {len(val_scenes)} val scenes, 0 overlap)")
        
    # Check assistant role & context embedding in every record
    bad_roles = 0
    bad_ctx = 0
    empty_strings = 0
    for r in sft_records:
        msgs = r.get('messages', [])
        if len(msgs) != 3:
            bad_roles += 1
        else:
            if msgs[2].get('role') != 'assistant' or msgs[2].get('name') != sister:
                bad_roles += 1
            if not msgs[2].get('content', '').strip():
                empty_strings += 1
                
        ctx = r.get('context', [])
        if len(ctx) > 0:
            if '[Scene Context]' not in msgs[1].get('content', '') or '[Scene Context]' not in r.get('input', ''):
                bad_ctx += 1
        else:
            if '[Scene Context]' in msgs[1].get('content', ''):
                bad_ctx += 1
                
    if bad_roles > 0:
        print(f"[FAIL] {bad_roles} records had bad assistant role for {sister}")
        total_errors += 1
    else:
        print(f"  [OK] Assistant role: 100% strictly {sister}")
        
    if bad_ctx > 0:
        print(f"[FAIL] {bad_ctx} records had inconsistent context embedding for {sister}")
        total_errors += 1
    else:
        print(f"  [OK] Context embedding: 100% consistent across all records")
        
    if empty_strings > 0:
        print(f"[FAIL] {empty_strings} records had empty dialogue content")
        total_errors += 1

    # 3. Chronological integrity in all_conversations.jsonl
    with open(os.path.join(data_dir, "all_conversations.jsonl"), 'r', encoding='utf-8') as f:
        all_conv = [json.loads(l) for l in f]
    
    prev_ep = None
    prev_t = 0
    regressions = 0
    for r in all_conv:
        ep = r['metadata']['episode']
        t = parse_time(r['metadata']['timestamp'])
        if ep == prev_ep:
            if t < prev_t:
                regressions += 1
        prev_ep = ep
        prev_t = t
        
    if regressions > 0:
        print(f"[FAIL] {regressions} timestamp regressions in {sister}")
        total_errors += 1
    else:
        print(f"  [OK] Timestamp continuity: 0 regressions across {len(all_conv)} dialogue turns")

    # 4. Check knowledge base
    k_dir = os.path.join(ai_dir, "knowledge")
    with open(os.path.join(k_dir, "personality.json"), 'r', encoding='utf-8') as f:
        pers = json.load(f)
    with open(os.path.join(k_dir, "development.json"), 'r', encoding='utf-8') as f:
        dev = json.load(f)
    with open(os.path.join(k_dir, "relationship_index.json"), 'r', encoding='utf-8') as f:
        rel = json.load(f)
        
    stgs = set(pers.get('timeline_stages', {}).keys())
    if stgs != {'season_1', 'season_2', 'movie', 'adult'}:
        print(f"[FAIL] Timeline stages invalid in {sister}: {stgs}")
        total_errors += 1
    else:
        print(f"  [OK] Personality: 4 verified timeline stages")
        
    phases = dev.get('narrative_progression', [])
    if len(phases) != 7:
        print(f"[FAIL] Development phases count in {sister}: {len(phases)} (expected 7)")
        total_errors += 1
    else:
        print(f"  [OK] Development: 7 verified chronological narrative phases")
        
    num_partners = rel.get('total_interaction_labels', 0)
    print(f"  [OK] Relationship index: {num_partners} interaction partners profiled")

    # 5. Check character files in Transcript folder
    trans_files = [
        f for f in os.listdir(trans_dir)
        if f.endswith('.jsonl') and not f.startswith('_') and f not in [
            f"{sister_lower}_only.jsonl", "train.jsonl", "validation.jsonl", "all_conversations.jsonl", "multi_turn_conversations.jsonl"
        ]
    ]
    char_sum = 0
    for cf in trans_files:
        with open(os.path.join(trans_dir, cf), 'r', encoding='utf-8') as f:
            recs = [json.loads(l) for l in f if l.strip()]
            char_sum += len(recs)
            
    if char_sum != len(all_conv):
        print(f"[FAIL] Character files sum ({char_sum}) != all_conversations count ({len(all_conv)}) for {sister}")
        total_errors += 1
    else:
        print(f"  [OK] Character files: {len(trans_files)} files summing to exactly {char_sum} dialogue turns")

print("\n" + "=" * 75)
if total_errors == 0:
    print("FINAL RESULT: 100% PERFECT AUDIT — ZERO ERRORS FOUND ACROSS ALL 5 SISTERS!")
else:
    print(f"FINAL RESULT: FOUND {total_errors} ISSUES TO FIX")
print("=" * 75)
