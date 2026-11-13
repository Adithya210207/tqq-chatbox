# Runs step 2 and step 3 for one sister, back to back, with a log file.
# Keeps the PC awake while it runs (only while this window is open).
#
# Start it by right-clicking > "Run with PowerShell", or in PowerShell:
#   powershell -ExecutionPolicy Bypass -File run_overnight.ps1 -Sister Miku -Count 200
#
# Safe to stop (close the window) and start again: both steps continue
# where they left off.
param([string]$Sister = "Miku", [int]$Count = 200)

Set-Location $PSScriptRoot
$env:PYTHONIOENCODING = "utf-8"

# Ask Windows not to sleep while this window runs (like a video player does).
Add-Type -Name Power -Namespace Win32 -MemberDefinition '[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint esFlags);'
[Win32.Power]::SetThreadExecutionState(0x80000001) | Out-Null

$log = Join-Path $PSScriptRoot "output\$Sister\overnight_log.txt"
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
function Log($line) { Write-Host $line; Add-Content -Path $log -Value $line -Encoding UTF8 }

Log "=== Started $(Get-Date) | $Sister | $Count chats ==="
Log "--- STEP 2: reverse prompts ---"
py -u step2_reverse_prompts.py --sister $Sister | ForEach-Object { Log $_ }
Log "--- STEP 3: roleplay conversations ---"
py -u step3_synthetic.py --sister $Sister --count $Count | ForEach-Object { Log $_ }
Log "=== Finished $(Get-Date) ==="

[Win32.Power]::SetThreadExecutionState(0x80000000) | Out-Null
Write-Host ""
Write-Host "All done. You can close this window." -ForegroundColor Green
