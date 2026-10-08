param([string]$Adb='adb',[string]$Serial)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/find-adb.ps1"
$Adb=Resolve-TouchXboxAdb $Adb
$deviceArgs=@();if($Serial){$deviceArgs=@('-s',$Serial)}
& $Adb @deviceArgs shell am force-stop dev.touchxbox.pad
if($LASTEXITCODE -ne 0){throw 'Could not stop application'}
$processText=(& $Adb @deviceArgs shell ps -A -o PID,ARGS) -join "`n"
if($LASTEXITCODE -ne 0){throw 'Could not inspect processes'}
foreach($m in [regex]::Matches($processText,'(?m)^\s*(\d+)\s+touchxbox-bridge(?:\s|$)')){& $Adb @deviceArgs shell kill $m.Groups[1].Value}
Write-Output 'TouchXbox app and bridge stopped.'
