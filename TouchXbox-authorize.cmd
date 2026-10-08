@echo off
setlocal DisableDelayedExpansion
set "TOUCHXBOX_AUTH_FILE=%~f0"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$source=[IO.File]::ReadAllText($env:TOUCHXBOX_AUTH_FILE,[Text.Encoding]::UTF8); $marker='#'+'<TOUCHXBOX_POWERSHELL>'; $offset=$source.IndexOf($marker,[StringComparison]::Ordinal); if($offset -lt 0){exit 1}; & ([scriptblock]::Create($source.Substring($offset+$marker.Length)))"
set "TOUCHXBOX_AUTH_EXIT=%ERRORLEVEL%"
echo.
pause
exit /b %TOUCHXBOX_AUTH_EXIT%
#<TOUCHXBOX_POWERSHELL>
# Single-file launcher. Only Windows PowerShell is required initially.
# ADB is reused when available, otherwise fetched from Google's SDK repository.
# The installed APK supplies BridgeDaemon; no APK, PS1 or Shizuku is required here.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$script:AdbPath = $null
$script:Serial = $null
$script:Secret = ''

function Protect-Output([string]$Text) {
    # Never print a provisioned activation key, including in an ADB error.
    if ($script:Secret) { $Text = $Text.Replace($script:Secret, '[redacted]') }
    return $Text
}

function Quote-NativeArgument([string]$Value) {
    $quoted = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
    $quoted = [regex]::Replace($quoted, '(\\+)$', '$1$1')
    return '"' + $quoted + '"'
}

function Invoke-Adb([string[]]$Arguments, [switch]$AllowFailure, [int]$TimeoutMs = 30000) {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $script:AdbPath
    $info.Arguments = ($Arguments | ForEach-Object { Quote-NativeArgument $_ }) -join ' '
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = [Text.Encoding]::UTF8
    $info.StandardErrorEncoding = [Text.Encoding]::UTF8
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    try {
        $null = $process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (!$process.WaitForExit($TimeoutMs)) {
            try { $process.Kill() } catch {}
            throw 'ADB 操作超时，请检查 USB 连接和设备上的调试授权提示。'
        }
        $output = Protect-Output (($stdout.Result + "`n" + $stderr.Result).Trim())
        $result = [pscustomobject]@{ Code = $process.ExitCode; Text = $output }
        if ($result.Code -ne 0 -and !$AllowFailure) { throw "ADB 操作失败：$output" }
        return $result
    } finally { $process.Dispose() }
}

function Device-Adb([string[]]$Arguments, [switch]$AllowFailure) {
    return Invoke-Adb -Arguments (@('-s', $script:Serial) + $Arguments) -AllowFailure:$AllowFailure
}

function Test-AdbExecutable([string]$Path) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    $script:AdbPath = [IO.Path]::GetFullPath($Path)
    try {
        $result = Invoke-Adb -Arguments @('version') -AllowFailure -TimeoutMs 10000
        return $result.Code -eq 0 -and $result.Text -match 'Android Debug Bridge version'
    } catch { return $false }
}

function Get-AdbDownload {
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    Write-Host '未找到可用 ADB，正在从 Google 官方仓库下载（需要联网）……'
    $response = Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/android/repository/repository2-1.xml' -TimeoutSec 30
    $settings = New-Object Xml.XmlReaderSettings
    $settings.DtdProcessing = [Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $reader = [Xml.XmlReader]::Create((New-Object IO.StringReader($response.Content)), $settings)
    $document = New-Object Xml.XmlDocument
    $document.XmlResolver = $null
    try { $document.Load($reader) } finally { $reader.Dispose() }
    $node = $document.SelectSingleNode("//*[local-name()='remotePackage' and @path='platform-tools']/*[local-name()='archives']/*[local-name()='archive'][*[local-name()='host-os']='windows']/*[local-name()='complete']")
    if (!$node) { throw '官方仓库没有提供 Windows ADB 下载信息。' }
    $archiveName = [string]$node.url
    $checksum = ([string]$node.checksum).Trim().ToLowerInvariant()
    $archiveSize = [long]$node.size
    if ($archiveName -notmatch '^platform-tools_r[0-9.]+-win\.zip$' -or $checksum -notmatch '^[0-9a-f]{40}$' -or $archiveSize -lt 100000 -or $archiveSize -gt 100MB) {
        throw '官方 ADB 下载信息格式不符合预期，已停止。'
    }
    $cacheRoot = [IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'TouchXbox\adb-cache'))
    $null = New-Item -ItemType Directory -Path $cacheRoot -Force
    $stage = Join-Path $cacheRoot ('package-' + [Guid]::NewGuid().ToString('N'))
    $null = New-Item -ItemType Directory -Path $stage
    $keep = $false
    try {
        $zip = Join-Path $stage 'platform-tools.zip'
        $oldProgress = $ProgressPreference
        try {
            $ProgressPreference = 'SilentlyContinue'
            Invoke-WebRequest -UseBasicParsing -Uri ('https://dl.google.com/android/repository/' + $archiveName) -OutFile $zip -TimeoutSec 120
        } finally { $ProgressPreference = $oldProgress }
        if ((Get-Item -LiteralPath $zip).Length -ne $archiveSize -or (Get-FileHash -LiteralPath $zip -Algorithm SHA1).Hash.ToLowerInvariant() -ne $checksum) {
            throw 'ADB 下载校验失败，请检查网络后重试。'
        }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [IO.Compression.ZipFile]::OpenRead($zip)
        try {
            $expandedSize = 0L
            foreach ($entry in $archive.Entries) {
                $target = [IO.Path]::GetFullPath((Join-Path $stage $entry.FullName))
                if (!$target.StartsWith($stage + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'ADB 压缩包包含异常路径。' }
                $expandedSize += $entry.Length
                if ($expandedSize -gt 300MB) { throw 'ADB 压缩包大小异常。' }
            }
        } finally { $archive.Dispose() }
        Expand-Archive -LiteralPath $zip -DestinationPath $stage
        $candidate = Join-Path $stage 'platform-tools\adb.exe'
        if (!(Test-AdbExecutable $candidate)) { throw '下载的 ADB 无法运行，请检查 Windows 安全软件或系统兼容性。' }
        Remove-Item -LiteralPath $zip
        $keep = $true
        return $script:AdbPath
    } finally {
        if (!$keep) {
            $resolvedStage = [IO.Path]::GetFullPath($stage)
            if ($resolvedStage.StartsWith($cacheRoot + '\', [StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolvedStage -Leaf) -match '^package-[0-9a-f]{32}$') {
                Remove-Item -LiteralPath $resolvedStage -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
    }
}

function Find-Adb {
    $candidates = New-Object 'Collections.Generic.List[string]'
    $command = Get-Command adb.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { $candidates.Add($command.Source) }
    foreach ($sdk in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)) {
        if ($sdk) { $candidates.Add((Join-Path $sdk 'platform-tools\adb.exe')) }
    }
    $candidates.Add((Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'))
    $cache = Join-Path $env:LOCALAPPDATA 'TouchXbox\adb-cache'
    if (Test-Path -LiteralPath $cache) {
        foreach ($folder in (Get-ChildItem -LiteralPath $cache -Directory -Filter 'package-*' | Sort-Object LastWriteTime -Descending)) {
            $candidates.Add((Join-Path $folder.FullName 'platform-tools\adb.exe'))
        }
    }
    $ownDirectory = Split-Path -Parent $env:TOUCHXBOX_AUTH_FILE
    $candidates.Add((Join-Path $ownDirectory 'platform-tools\adb.exe'))
    $candidates.Add((Join-Path $ownDirectory 'adb.exe'))
    $candidates.Add('D:\platform-tools\adb.exe')
    $candidates.Add('C:\platform-tools\adb.exe')
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (Test-AdbExecutable $candidate) { return $script:AdbPath }
    }
    return Get-AdbDownload
}

function Select-Device {
    $listing = (Invoke-Adb -Arguments @('devices', '-l')).Text
    $ready = @([regex]::Matches($listing, '(?m)^(\S+)\s+device(?:[ \t]+([^\r\n]*))?\s*$'))
    if (!$ready.Count) {
        if ($listing -match '(?m)^\S+\s+unauthorized\b') { throw '请解锁设备，在设备上勾选“始终允许使用这台计算机进行调试”，点击允许后重新运行。' }
        if ($listing -match '(?m)^\S+\s+offline\b') { throw '设备处于 offline 状态，请重新插拔 USB 并确认 USB 调试已开启。' }
        throw '没有检测到设备。请开启 USB 调试、使用可传输数据的 USB 线连接电脑；必要时安装设备厂商的 USB 驱动。'
    }
    $index = 0
    if ($ready.Count -gt 1) {
        Write-Host '检测到多台设备，请选择要授权的一台：'
        for ($i = 0; $i -lt $ready.Count; $i++) { Write-Host ('{0}. {1}  {2}' -f ($i + 1), $ready[$i].Groups[1].Value, $ready[$i].Groups[2].Value) }
        $choice = Read-Host '输入序号（留空退出）'
        $number = 0
        if (![int]::TryParse($choice, [ref]$number) -or $number -lt 1 -or $number -gt $ready.Count) { throw '未选择有效设备，未执行授权。' }
        $index = $number - 1
    }
    $script:Serial = $ready[$index].Groups[1].Value
    Write-Host ('设备：' + $script:Serial)
}

function Read-Exactly($Stream, [int]$Length) {
    $buffer = New-Object byte[] $Length
    $offset = 0
    while ($offset -lt $Length) {
        $count = $Stream.Read($buffer, $offset, $Length - $offset)
        if ($count -le 0) { throw '输入服务提前断开连接。' }
        $offset += $count
    }
    return ,$buffer
}

function Get-Proof([byte[]]$Key, [byte]$Role, [byte[]]$A, [byte[]]$B) {
    $hmac = New-Object Security.Cryptography.HMACSHA256
    try {
        $hmac.Key = $Key
        return ,($hmac.ComputeHash([byte[]](@($Role) + $A + $B)))
    } finally { $hmac.Dispose() }
}

function Test-Bridge([byte[]]$Key) {
    $forward = $null
    $client = $null
    try {
        $portText = (Device-Adb -Arguments @('forward', 'tcp:0', 'tcp:37684')).Text.Trim()
        if ($portText -notmatch '^\d{1,5}$' -or [int]$portText -lt 1 -or [int]$portText -gt 65535) { throw '无法建立临时输入服务检测连接。' }
        $forward = 'tcp:' + $portText
        $client = New-Object Net.Sockets.TcpClient
        $connecting = $client.ConnectAsync('127.0.0.1', [int]$portText)
        if (!$connecting.Wait(5000)) { throw '连接输入服务超时。' }
        $stream = $client.GetStream()
        $stream.ReadTimeout = 5000
        $stream.WriteTimeout = 5000
        $challenge = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($challenge) } finally { $rng.Dispose() }
        $stream.Write($challenge, 0, 32)
        $nonce = Read-Exactly $stream 32
        $serverProof = Read-Exactly $stream 32
        $expected = Get-Proof $Key 1 $challenge $nonce
        $difference = 0
        for ($i = 0; $i -lt 32; $i++) { $difference = $difference -bor ($serverProof[$i] -bxor $expected[$i]) }
        if ($difference -ne 0) { throw '输入服务身份验证失败，请重新运行授权。' }
        $clientProof = Get-Proof $Key 2 $challenge $nonce
        $stream.Write($clientProof, 0, 32)
        if ($stream.ReadByte() -ne 1) { throw '输入服务拒绝了本次授权。' }
        # Java DataOutputStream.writeUTF("PROBE"): two-byte length + ASCII.
        $probe = [byte[]](0, 5, 80, 82, 79, 66, 69)
        $stream.Write($probe, 0, $probe.Length)
        $lengthBytes = Read-Exactly $stream 2
        $length = ([int]$lengthBytes[0] -shl 8) -bor [int]$lengthBytes[1]
        if ($length -gt 8192) { throw '输入服务响应长度异常。' }
        $message = [Text.Encoding]::UTF8.GetString((Read-Exactly $stream $length))
        if (!$message.StartsWith('OK:')) { throw "输入服务不能创建系统手柄：$message" }
        return $message
    } finally {
        if ($client) { $client.Dispose() }
        if ($forward) { $null = Device-Adb -Arguments @('forward', '--remove', $forward) -AllowFailure }
    }
}

function Start-Authorization {
    Write-Host 'TouchXbox 独立授权工具' -ForegroundColor Cyan
    Write-Host '请先保存正在编辑的模板。激活会重启 TouchXbox，保留已保存的模板。'
    Write-Host '手机或平板需已安装 TouchXbox，并开启 USB 调试。无需管理员权限。'
    Write-Host ''
    $script:AdbPath = Find-Adb
    Select-Device
    $currentUser = (Device-Adb -Arguments @('shell', 'am', 'get-current-user')).Text.Trim()
    if ($currentUser -notmatch '^\d+$') { throw '无法识别当前 Android 用户。' }
    $packages = (Device-Adb -Arguments @('shell', 'pm', 'list', 'packages', '-U', '--user', $currentUser, 'dev.touchxbox.pad')).Text
    if ($packages -notmatch '(?m)^package:dev\.touchxbox\.pad\s+uid:(\d+)\s*$') { throw '当前设备用户没有安装 TouchXbox，请先安装 APK，再运行本脚本。' }
    $appUid = $Matches[1]
    if ([long]$appUid -lt 10000) { throw 'TouchXbox 的应用 UID 异常。' }
    $paths = (Device-Adb -Arguments @('shell', 'pm', 'path', '--user', $currentUser, 'dev.touchxbox.pad')).Text
    $baseApk = [regex]::Match($paths, '(?m)^package:(/data/app/[A-Za-z0-9_./=+~-]+/base\.apk)\s*$')
    if (!$baseApk.Success) { throw '无法找到设备上已安装的 TouchXbox APK。' }
    $packagePath = $baseApk.Groups[1].Value
    $access = Device-Adb -Arguments @('shell', 'test -r /dev/uhid && test -w /dev/uhid') -AllowFailure
    if ($access.Code -ne 0) { throw '此设备未向 ADB shell 开放 /dev/uhid，当前授权方式不可用。' }

    Write-Host '正在激活已安装的 TouchXbox……'
    $null = Device-Adb -Arguments @('shell', 'am', 'force-stop', '--user', $currentUser, 'dev.touchxbox.pad')
    $processes = (Device-Adb -Arguments @('shell', 'ps', '-A', '-o', 'PID,ARGS')).Text
    foreach ($match in [regex]::Matches($processes, '(?m)^\s*(\d+)\s+touchxbox-bridge(?:\s|$)')) {
        $null = Device-Adb -Arguments @('shell', 'kill', $match.Groups[1].Value) -AllowFailure
    }
    $key = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($key) } finally { $rng.Dispose() }
    $script:Secret = ([BitConverter]::ToString($key)).Replace('-', '').ToLowerInvariant()
    $keyPath = '/data/local/tmp/touchxbox-activation-' + [Guid]::NewGuid().ToString('N') + '.key'
    try {
        $null = Device-Adb -Arguments @('shell', "umask 077; printf '%s' '$script:Secret' > '$keyPath'")
        $opened = Device-Adb -Arguments @('shell', 'am', 'start', '--user', $currentUser, '-n', 'dev.touchxbox.pad/.MainActivity')
        if ($opened.Text -match '(?m)^Error') { throw 'TouchXbox 无法打开，请先解锁设备再重试。' }
        $provision = Device-Adb -Arguments @('shell', 'am', 'broadcast', '--user', $currentUser, '-a', 'dev.touchxbox.pad.ACTIVATE', '--include-stopped-packages', '-n', 'dev.touchxbox.pad/.ActivationReceiver', '--es', 'token', $script:Secret)
        if ($provision.Text -notmatch 'Broadcast completed:\s*result=1(?:\s|,|$)') { throw '应用未接受授权，请安装与此工具兼容的 TouchXbox 版本（当前为 0.5.3）。' }
        $launch = "CLASSPATH='$packagePath' nohup app_process / --nice-name=touchxbox-bridge dev.touchxbox.pad.BridgeDaemon $appUid '$keyPath' >/data/local/tmp/touchxbox-bridge.log 2>&1 </dev/null &"
        $null = Device-Adb -Arguments @('shell', $launch)
        $ready = $false
        for ($attempt = 0; $attempt -lt 15; $attempt++) {
            Start-Sleep -Milliseconds 300
            $log = Device-Adb -Arguments @('shell', 'cat', '/data/local/tmp/touchxbox-bridge.log') -AllowFailure
            if ($log.Text -match ('bridge ready; app uid=' + $appUid + ';')) { $ready = $true; break }
        }
        if (!$ready) { throw ('输入服务启动失败：' + $log.Text) }
        $null = Test-Bridge $key
    } finally {
        # Delete this run's exact temporary key path; never enumerate device files.
        $null = Device-Adb -Arguments @('shell', 'rm', '-f', $keyPath) -AllowFailure
        [Array]::Clear($key, 0, $key.Length)
        $script:Secret = ''
    }
    $overlay = Device-Adb -Arguments @('shell', 'appops', 'set', '--user', $currentUser, 'dev.touchxbox.pad', 'SYSTEM_ALERT_WINDOW', 'allow') -AllowFailure
    $overlayStatus = Device-Adb -Arguments @('shell', 'appops', 'get', '--user', $currentUser, 'dev.touchxbox.pad', 'SYSTEM_ALERT_WINDOW') -AllowFailure
    $null = Device-Adb -Arguments @('shell', 'am', 'start', '--user', $currentUser, '-n', 'dev.touchxbox.pad/.MainActivity')
    Write-Host ''
    Write-Host '输入服务激活成功，身份验证与 /dev/uhid 访问检测通过。' -ForegroundColor Green
    if ($overlay.Code -eq 0 -and $overlayStatus.Text -match 'SYSTEM_ALERT_WINDOW:\s*allow\b') {
        Write-Host '悬浮窗权限已允许。返回 TouchXbox，点击模板即可启动。'
    } else {
        Write-Host '还需手动开启悬浮窗：TouchXbox → 齿轮 → 悬浮窗权限 → 允许。' -ForegroundColor Yellow
    }
    Write-Host '现在可以断开 USB。设备重启或输入服务退出后，重新运行此脚本。'
}

try {
    Start-Authorization
    exit 0
} catch {
    Write-Host ''
    Write-Host ('未完成授权：' + (Protect-Output $_.Exception.Message)) -ForegroundColor Red
    exit 1
}
