param([string]$JavaHome, [string]$BuildTools, [string]$AndroidJar, [string]$BuildDir, [string]$SigningKey, [string]$OutputApk)
$ErrorActionPreference = 'Stop'
if (!$BuildDir) { $BuildDir = Join-Path $PSScriptRoot 'build' }
$BuildDir=$ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($BuildDir)
if (!$JavaHome -or !$BuildTools -or !$AndroidJar) { throw 'Pass -JavaHome JDK17 -BuildTools SDK/build-tools/34.0.0 -AndroidJar SDK/platforms/android-34/android.jar' }
$JavaHome=(Resolve-Path -LiteralPath $JavaHome).Path
$BuildTools=(Resolve-Path -LiteralPath $BuildTools).Path
$AndroidJar=(Resolve-Path -LiteralPath $AndroidJar).Path
$env:JAVA_HOME=$JavaHome
function Run([string]$exe,[string[]]$a) { & $exe @a; if($LASTEXITCODE -ne 0) { throw "Failed ($LASTEXITCODE): $exe" } }
$src=Join-Path $PSScriptRoot 'app/src/main'
[xml]$manifest=Get-Content -LiteralPath "$src/AndroidManifest.xml" -Raw
$versionName=$manifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
if($versionName -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'){throw 'Invalid APK versionName'}
if(!$OutputApk){$OutputApk=Join-Path $PSScriptRoot "TouchXbox-$versionName.apk"}
$OutputApk=$ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputApk)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputApk) | Out-Null
$classes=Join-Path $BuildDir 'classes'
$generated=Join-Path $BuildDir 'generated'
$dex=Join-Path $BuildDir 'dex'
$dependencies=@(Get-ChildItem "$PSScriptRoot/libs" -Filter '*.jar' | ForEach-Object {$_.FullName})
$compilePath=(@($AndroidJar)+$dependencies) -join [IO.Path]::PathSeparator
$buildRoot=[IO.Path]::GetFullPath($BuildDir).TrimEnd('\','/')
foreach($stage in @($classes,$generated,$dex)){
    $resolved=[IO.Path]::GetFullPath($stage)
    if(!$resolved.StartsWith($buildRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Build staging path escapes build directory'}
    if(Test-Path -LiteralPath $resolved){Remove-Item -LiteralPath $resolved -Recurse -Force}
}
New-Item -ItemType Directory -Force $BuildDir,$classes,$generated,$dex | Out-Null
Run "$BuildTools/aapt2.exe" @('compile','--dir',"$src/res",'-o',"$BuildDir/resources.zip")
Run "$BuildTools/aapt2.exe" @('link','-o',"$BuildDir/unsigned.apk",'-I',$AndroidJar,'--manifest',"$src/AndroidManifest.xml",'--java',$generated,'--auto-add-overlay','-A',"$src/assets","$BuildDir/resources.zip")
$files=@(Get-ChildItem "$src/java",$generated -Recurse -Filter '*.java' | ForEach-Object { '"'+$_.FullName.Replace('\','/')+'"' })
[IO.File]::WriteAllLines("$BuildDir/sources.txt",$files,[Text.UTF8Encoding]::new($false))
Run "$JavaHome/bin/javac.exe" @('-encoding','UTF-8','--release','8','-classpath',$compilePath,'-d',$classes,"@$BuildDir/sources.txt")
Run "$JavaHome/bin/jar.exe" @('cf',"$BuildDir/classes.jar",'-C',$classes,'.')
Run "$JavaHome/bin/java.exe" (@('-cp',"$BuildTools/lib/d8.jar",'com.android.tools.r8.D8','--lib',$AndroidJar,'--min-api','26','--output',$dex,"$BuildDir/classes.jar")+$dependencies)
Copy-Item -LiteralPath "$BuildDir/unsigned.apk" -Destination "$BuildDir/with-dex.apk" -Force
Run "$JavaHome/bin/jar.exe" @('uf',"$BuildDir/with-dex.apk",'-C',$dex,'classes.dex')
Run "$BuildTools/zipalign.exe" @('-f','4',"$BuildDir/with-dex.apk","$BuildDir/aligned.apk")
$key=Join-Path $BuildDir 'development.p12'
if($SigningKey){$key=(Resolve-Path -LiteralPath $SigningKey).Path}
if(!(Test-Path -LiteralPath $key)){ Run "$JavaHome/bin/keytool.exe" @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','touchxbox','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=TouchXbox Development') }
Run "$JavaHome/bin/java.exe" @('-jar',"$BuildTools/lib/apksigner.jar",'sign','--ks',$key,'--ks-pass','pass:android','--key-pass','pass:android','--out',$OutputApk,"$BuildDir/aligned.apk")
Run "$JavaHome/bin/java.exe" @('-jar',"$BuildTools/lib/apksigner.jar",'verify','--verbose',$OutputApk)
Write-Output "APK: $OutputApk"
