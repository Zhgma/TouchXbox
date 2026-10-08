param([Parameter(Mandatory=$true)][string]$JavaHome, [string]$TestDir)
$ErrorActionPreference = 'Stop'
if (!$TestDir) { $TestDir = Join-Path $PSScriptRoot 'ota-test-build' }
New-Item -ItemType Directory -Force -Path $TestDir | Out-Null
$source = Join-Path $PSScriptRoot 'app/src/main/java/dev/touchxbox/pad'
& "$JavaHome/bin/javac.exe" -encoding UTF-8 --release 8 -d $TestDir "$source/OtaSource.java" "$source/OtaRelease.java" "$source/OtaDownload.java" "$PSScriptRoot/tests/OtaTest.java"
if ($LASTEXITCODE -ne 0) { throw 'OTA test compilation failed' }
& "$JavaHome/bin/java.exe" -cp $TestDir dev.touchxbox.pad.OtaTest
if ($LASTEXITCODE -ne 0) { throw 'OTA tests failed' }
