param([string]$Adb='adb',[string]$Serial,[switch]$SkipInstall,[string]$Apk)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/find-adb.ps1"
$Adb=Resolve-TouchXboxAdb $Adb
$deviceArgs=@()
if($Serial){$deviceArgs=@('-s',$Serial)}
function Invoke-Adb([string[]]$a){$output=& $Adb @deviceArgs @a 2>&1;if($LASTEXITCODE -ne 0){throw ($output -join "`n")};$output}
$deviceText=(& $Adb devices) -join "`n"
if(!$Serial){$connected=@([regex]::Matches($deviceText,'(?m)^(\S+)\s+device\s*$'));if($connected.Count -ne 1){throw "Connect exactly one authorized phone or pass -Serial. `n$deviceText"};$Serial=$connected[0].Groups[1].Value;$deviceArgs=@('-s',$Serial)}
if(!$SkipInstall){
    if(!$Apk){
        [xml]$manifest=Get-Content -LiteralPath "$PSScriptRoot/app/src/main/AndroidManifest.xml" -Raw
        $version=$manifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
        $Apk=Join-Path $PSScriptRoot "TouchXbox-$version.apk"
        if(!(Test-Path -LiteralPath $Apk)){
            $pattern='^TouchXbox-'+[regex]::Escape($version)+'-\d+-[0-9a-f]{12}\.apk$'
            $candidates=@(Get-ChildItem -LiteralPath $PSScriptRoot -File -Filter '*.apk' | Where-Object {$_.Name -match $pattern})
            if($candidates.Count -ne 1){throw "Place the release APK in this directory, or pass -Apk with its path"}
            $Apk=$candidates[0].FullName
        }
    }
    Invoke-Adb @('install','-r',(Resolve-Path -LiteralPath $Apk).Path)
}
Invoke-Adb @('shell','am','force-stop','dev.touchxbox.pad') | Out-Null
$uidText=(Invoke-Adb @('shell','pm','list','packages','-U','dev.touchxbox.pad')) -join "`n"
if($uidText -notmatch 'package:dev\.touchxbox\.pad\s+uid:(\d+)'){throw "Cannot resolve application UID: $uidText"}
$appUid=$Matches[1]
$packagePath=(Invoke-Adb @('shell','pm','path','dev.touchxbox.pad') | Where-Object {$_ -match '^package:.*/base.apk'} | Select-Object -First 1).Trim() -replace '^package:',''
if($packagePath -notmatch '^/data/app/[A-Za-z0-9_./=+~-]+/base\.apk$'){throw 'Unexpected package path'}
# Only stop this app's privileged helper; do not touch other ADB services.
$processText=(Invoke-Adb @('shell','ps','-A','-o','PID,ARGS')) -join "`n"
foreach($match in [regex]::Matches($processText,'(?m)^\s*(\d+)\s+touchxbox-bridge(?:\s|$)')){Invoke-Adb @('shell','kill',$match.Groups[1].Value) | Out-Null}
$keyBytes=New-Object byte[] 32
$rng=[Security.Cryptography.RandomNumberGenerator]::Create();$rng.GetBytes($keyBytes);$rng.Dispose()
$token=([BitConverter]::ToString($keyBytes)).Replace('-','').ToLowerInvariant()
Invoke-Adb @('shell',"umask 077; printf '%s' '$token' > /data/local/tmp/touchxbox-activation.key") | Out-Null
Invoke-Adb @('shell','am','start','-n','dev.touchxbox.pad/.MainActivity') | Out-Null
$provision=Invoke-Adb @('shell','am','broadcast','-a','dev.touchxbox.pad.ACTIVATE','--include-stopped-packages','-n','dev.touchxbox.pad/.ActivationReceiver','--es','token',$token)
if(($provision -join "`n") -notmatch 'result=1'){throw 'App activation-key provisioning failed'}
$launch="CLASSPATH='$packagePath' nohup app_process / --nice-name=touchxbox-bridge dev.touchxbox.pad.BridgeDaemon $appUid /data/local/tmp/touchxbox-activation.key >/data/local/tmp/touchxbox-bridge.log 2>&1 </dev/null &"
Invoke-Adb @('shell',$launch) | Out-Null
Start-Sleep -Milliseconds 900
$log=Invoke-Adb @('shell','cat','/data/local/tmp/touchxbox-bridge.log')
$log
if(($log -join "`n") -notmatch 'bridge ready'){throw 'Bridge did not report ready. Check log above.'}
Invoke-Adb @('shell','am','start','-n','dev.touchxbox.pad/.MainActivity') | Out-Null
Write-Output 'Bridge activated. Allow overlay permission in the app, then start the controller.'
