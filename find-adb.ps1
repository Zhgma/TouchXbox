function Resolve-TouchXboxAdb([string]$Value) {
    if($Value -ne 'adb'){return $Value}
    $cmd=Get-Command adb -ErrorAction SilentlyContinue
    if($cmd){return $cmd.Source}
    foreach($candidate in @((Join-Path $PSScriptRoot 'platform-tools\adb.exe'),(Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),'D:\platform-tools\adb.exe')){
        if(Test-Path -LiteralPath $candidate){return $candidate}
    }
    throw 'ADB not found. Pass -Adb with the full path to adb.exe.'
}
