# Asks Windows not to idle-sleep while this script runs (no settings are changed).
# Ends by itself when eval\ichika_log.txt says ALL_FINISHED, or after 6 hours at most.
# A closed lid or pressing Sleep still sleeps the laptop.
Add-Type -Namespace Win32 -Name Power -MemberDefinition '[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint esFlags);'
$ES_CONTINUOUS = [uint32]2147483648
$ES_SYSTEM_REQUIRED = [uint32]1
[void][Win32.Power]::SetThreadExecutionState($ES_CONTINUOUS -bor $ES_SYSTEM_REQUIRED)
$log = Join-Path $PSScriptRoot 'ichika_log.txt'
$deadline = (Get-Date).AddHours(6)
while ((Get-Date) -lt $deadline) {
    if ((Test-Path $log) -and (Select-String -Path $log -Pattern 'ALL_FINISHED' -Quiet)) { break }
    Start-Sleep -Seconds 60
}
[void][Win32.Power]::SetThreadExecutionState($ES_CONTINUOUS)
