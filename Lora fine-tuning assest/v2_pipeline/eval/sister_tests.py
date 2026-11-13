"""Loads eval/<sister>_test_100.py for the eval scripts.

Each sister's test file defines:
  TESTS        100 (opener, follow-up or None) chats      -> run_compare.py
  CATEGORIES   [(first, last, name), ...] test ranges     -> score.py unblind
  TRAP_CHECKS  {test number: regex "she did the task"}    -> score.py auto
  FACTS        [(question, regex right answer), ...]      -> fact_test.py
"""
import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))          # so eval scripts can import common


def load(sister):
    path = HERE / f"{sister.lower()}_test_100.py"
    if not path.exists():
        sys.exit(f"No test file for {sister}: write {path.name} first (OFFICIAL_PLAN Part 6).")
    spec = importlib.util.spec_from_file_location(path.stem, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod
