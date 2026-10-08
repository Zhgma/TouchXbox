param(
    [Parameter(Mandatory=$true)][string]$ApkPath,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$BuildTools,
    [string]$OutputDirectory,
    [string]$Changelog = '',
    [string]$ChangelogFile,
    [string]$PreviousManifest
)
$ErrorActionPreference = 'Stop'
$ApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$BuildTools = (Resolve-Path -LiteralPath $BuildTools).Path
if (!$OutputDirectory) { $OutputDirectory = Join-Path $PSScriptRoot 'ota-release' }
$OutputDirectory = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDirectory)
if ($ChangelogFile) { $Changelog = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $ChangelogFile).Path) }
if ($Changelog.Length -gt 16000) { throw 'Changelog must not exceed 16000 characters' }

$signature = & "$JavaHome/bin/java.exe" -jar "$BuildTools/lib/apksigner.jar" verify --print-certs $ApkPath 2>&1
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $signature" }
$signatureText = $signature -join "`n"
$signerMatch = [regex]::Match($signatureText, 'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})')
if (!$signerMatch.Success -or $signatureText -match 'Signer #2 certificate') { throw 'Expected one APK signing certificate' }
$signer = $signerMatch.Groups[1].Value.ToLowerInvariant()
$badging = & "$BuildTools/aapt2.exe" dump badging $ApkPath 2>&1
if ($LASTEXITCODE -ne 0) { throw "Unable to read APK metadata: $badging" }
$badgingText = $badging -join "`n"
$packageMatch = [regex]::Match($badgingText, "package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'")
$sdkMatch = [regex]::Match($badgingText, "(?m)^sdkVersion:'(\d+)'")
if (!$packageMatch.Success -or !$sdkMatch.Success) { throw 'APK version or minimum SDK is missing' }
if ($packageMatch.Groups[1].Value -ne 'dev.touchxbox.pad') { throw 'APK is not TouchXbox' }
$versionCode = [long]$packageMatch.Groups[2].Value
$versionName = $packageMatch.Groups[3].Value
if ($versionCode -le 0 -or $versionName -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$') { throw 'Invalid release version' }
$size = (Get-Item -LiteralPath $ApkPath).Length
if ($size -le 0 -or $size -gt 95MB) { throw 'APK must be between 1 byte and 95 MiB' }
$sha256 = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($PreviousManifest) {
    $previous = Get-Content -LiteralPath $PreviousManifest -Raw | ConvertFrom-Json
    if ($previous.packageName -ne 'dev.touchxbox.pad') { throw 'Previous release package does not match' }
    if ($previous.signerSha256 -ne $signer) { throw 'Signing certificate changed; this APK cannot upgrade the previous release' }
    if ($versionCode -le [long]$previous.versionCode) { throw 'Increase Android versionCode before publishing a new release' }
}

$apkFile = "TouchXbox-$versionName-$versionCode-$($sha256.Substring(0,12)).apk"
$manifest = [ordered]@{
    schemaVersion = 1
    packageName = 'dev.touchxbox.pad'
    versionCode = $versionCode
    versionName = $versionName
    minSdk = [int]$sdkMatch.Groups[1].Value
    apkFile = $apkFile
    size = $size
    sha256 = $sha256
    signerSha256 = $signer
    publishedAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    changelog = $Changelog
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$target = Join-Path $OutputDirectory $apkFile
if ([IO.Path]::GetFullPath($target) -ne $ApkPath) { Copy-Item -LiteralPath $ApkPath -Destination $target -Force }
if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sha256) { throw 'Copied APK checksum mismatch' }
# Write metadata last; upload the APK before making latest.json or its Release public.
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'latest.json'), ($manifest | ConvertTo-Json -Depth 4) + "`n", [Text.UTF8Encoding]::new($false))
Write-Output "Prepared APK: $target"
Write-Output "Update metadata: $(Join-Path $OutputDirectory 'latest.json')"
Write-Output "Version: $versionName ($versionCode); SHA-256: $sha256"
