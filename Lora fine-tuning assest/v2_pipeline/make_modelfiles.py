"""Writes ollama/Modelfile_<Sister> for each sister (already done for you).
Edit STAGE / PARTNER below and re-run `py make_modelfiles.py` to change who she is talking to."""
from common import PIPELINE_DIR, SISTERS, load_stage_prompts, system_prompt

STAGE = "season_2"      # season_1, season_2 or movie
PARTNER = "Fuutarou"    # who YOU are in the chat

TEMPLATE = '''{{ if .System }}<|start_header_id|>system<|end_header_id|>

{{ .System }}<|eot_id|>{{ end }}{{ if .Prompt }}<|start_header_id|>user<|end_header_id|>

{{ .Prompt }}<|eot_id|>{{ end }}<|start_header_id|>assistant<|end_header_id|>

{{ .Response }}<|eot_id|>'''

out_dir = PIPELINE_DIR / "ollama"
out_dir.mkdir(exist_ok=True)
for s in SISTERS:
    sys = system_prompt(s, STAGE, PARTNER, load_stage_prompts(s)).replace('"""', "'")
    text = f'''# Change this to the name of the .gguf file you downloaded from Colab:
FROM ./{s.lower()}-unsloth.Q4_K_M.gguf

TEMPLATE """{TEMPLATE}"""

SYSTEM """{sys}"""

PARAMETER temperature 0.8
PARAMETER top_p 0.9
PARAMETER repeat_penalty 1.1
PARAMETER num_ctx 4096
PARAMETER stop "<|eot_id|>"
PARAMETER stop "<|start_header_id|>"
'''
    (out_dir / f"Modelfile_{s}").write_text(text, encoding="utf-8")
    print("wrote", out_dir / f"Modelfile_{s}")
