@echo off
cd /d "C:\Users\user\Documents\My Projects\TQQ Chatbox\Lora fine-tuning assest\v2_pipeline"
set PYTHONIOENCODING=utf-8
echo START %date% %time% > eval\ichika_log.txt
py eval\ichika_secret_check.py Ichika_ryuu_v1 ichika-base >> eval\ichika_log.txt 2>&1
echo SECRET_DONE %time% >> eval\ichika_log.txt
py eval\fact_test.py --sister Ichika Ichika_ryuu_v1 ichika-base >> eval\ichika_log.txt 2>&1
echo FACTS_DONE %time% >> eval\ichika_log.txt
py eval\run_compare.py --sister Ichika Ichika_ryuu_v1 ichika-base >> eval\ichika_log.txt 2>&1
echo ALL_FINISHED %time% >> eval\ichika_log.txt
