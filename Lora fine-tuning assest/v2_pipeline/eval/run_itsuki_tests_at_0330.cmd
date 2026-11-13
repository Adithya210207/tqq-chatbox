@echo off
rem Waits until the next 03:30, then runs Itsuki's three tests (secret check, fact test, 100-chat compare).
cd /d "C:\Users\user\Documents\My Projects\TQQ Chatbox\Lora fine-tuning assest\v2_pipeline"
echo WAITING_FOR_0330 since %date% %time% > eval\itsuki_wait.txt
powershell -NoProfile -Command "$t=[datetime]::Today.AddHours(3).AddMinutes(30); if($t -lt (Get-Date)){$t=$t.AddDays(1)}; Start-Sleep -Seconds ([int]($t-(Get-Date)).TotalSeconds)"
echo STARTED %date% %time% >> eval\itsuki_wait.txt
call eval\run_itsuki_tests.cmd
