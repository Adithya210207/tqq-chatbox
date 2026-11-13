@echo off
cd /d "C:\Users\user\Documents\My Projects\TQQ Chatbox\Lora fine-tuning assest\v2_pipeline"
set PYTHONIOENCODING=utf-8
echo START %date% %time% > eval\mikuv2_log.txt
py eval\miku_invention_test.py Miku_ryuu_v2 Miku_ryuu_v1 >> eval\mikuv2_log.txt 2>&1
echo INVENTION_DONE %time% >> eval\mikuv2_log.txt
py eval\fact_test.py --sister Miku Miku_ryuu_v2 Miku_ryuu_v1 >> eval\mikuv2_log.txt 2>&1
echo FACTS_DONE %time% >> eval\mikuv2_log.txt
py eval\run_compare.py --sister Miku Miku_ryuu_v2 Miku_ryuu_v1 >> eval\mikuv2_log.txt 2>&1
echo ALL_FINISHED %time% >> eval\mikuv2_log.txt
