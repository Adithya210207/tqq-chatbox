@echo off
cd /d "C:\Users\user\Documents\My Projects\TQQ Chatbox\Lora fine-tuning assest\v2_pipeline"
set PYTHONIOENCODING=utf-8
echo START %date% %time% > eval\itsuki_log.txt
py eval\itsuki_secret_check.py Itsuki_ryuu_v1 itsuki-base >> eval\itsuki_log.txt 2>&1
echo SECRET_DONE %time% >> eval\itsuki_log.txt
py eval\fact_test.py --sister Itsuki Itsuki_ryuu_v1 itsuki-base >> eval\itsuki_log.txt 2>&1
echo FACTS_DONE %time% >> eval\itsuki_log.txt
py eval\run_compare.py --sister Itsuki Itsuki_ryuu_v1 itsuki-base >> eval\itsuki_log.txt 2>&1
echo ALL_FINISHED %time% >> eval\itsuki_log.txt
