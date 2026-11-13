"""Writes ollama/Modelfile_MikuV2 (template/params copied from Modelfile_IchikaV1).
SYSTEM = common.system_prompt("Miku", "season_2", "Fuutarou", ...) = the v2 training prompt (with the 5 rules).
    py ollama\\make_modelfile_mikuv2.py
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import common  # noqa: E402

src = (HERE / "Modelfile_IchikaV1").read_text(encoding="utf-8")
template = src[src.index("TEMPLATE"):src.index('SYSTEM """')]
params = src[src.index("PARAMETER"):]
system = common.system_prompt("Miku", "season_2", "Fuutarou", common.load_stage_prompts("Miku"))
assert '"""' not in system
out = ("# Miku v2 (trained 2026-11-13 on Colab authuser=5): Stheno 8B + LoRA (4-bit base, 2 epochs, fact sheet + 5 rules "
       "in every example, +32 anti-invention gold chats), merged, Q8_0.\n"
       "# SYSTEM below is generated from common.system_prompt, identical to the training prompts.\n"
       "FROM ./miku-v2-stheno-q8_0.gguf\n" + template + f'SYSTEM """{system}"""\n' + params)
(HERE / "Modelfile_MikuV2").write_text(out, encoding="utf-8", newline="\n")
print(out[-900:])
