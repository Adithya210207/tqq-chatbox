@echo off
cd /d "C:\Users\user\Documents\My Projects\TQQ Chatbox\Lora fine-tuning assest\v2_pipeline"
set PYTHONIOENCODING=utf-8
echo START %date% %time% > eval\yotsuba_log.txt
py eval\yotsuba_kyoto_check.py Yotsuba_ryuu_v1 yotsuba-base >> eval\yotsuba_log.txt 2>&1
echo KYOTO_DONE %time% >> eval\yotsuba_log.txt
py eval\fact_test.py --sister Yotsuba Yotsuba_ryuu_v1 yotsuba-base >> eval\yotsuba_log.txt 2>&1
echo FACTS_DONE %time% >> eval\yotsuba_log.txt
py eval\run_compare.py --sister Yotsuba Yotsuba_ryuu_v1 yotsuba-base >> eval\yotsuba_log.txt 2>&1
echo ALL_FINISHED %time% >> eval\yotsuba_log.txt
