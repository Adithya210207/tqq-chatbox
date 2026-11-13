@echo off
cd /d "C:\Users\user\Documents\My Projects\TQQ Chatbox\Lora fine-tuning assest\v2_pipeline"
set PYTHONIOENCODING=utf-8
echo START %date% %time% > eval\nino_log.txt
py eval\fact_test.py --sister Nino Nino_ryuu_v1 nino-base >> eval\nino_log.txt 2>&1
echo FACTS_DONE %time% >> eval\nino_log.txt
py eval\run_compare.py --sister Nino Nino_ryuu_v1 nino-base >> eval\nino_log.txt 2>&1
echo ALL_FINISHED %time% >> eval\nino_log.txt
