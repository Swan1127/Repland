[CmdletBinding(SupportsShouldProcess = $true)]
param([string]$Serial)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Get-ReplandAdb {
    $sdkCandidates = @()
    $localProperties = Join-Path $PSScriptRoot 'android/local.properties'
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -Last 1
        if ($sdkLine) {
            $sdkValue = ($sdkLine -replace '^\s*sdk\.dir\s*=\s*', '').Trim()
            $sdkValue = [regex]::Replace($sdkValue, '\\([\\:= ])', '$1')
            $sdkCandidates += $sdkValue
        }
    }
    $sdkCandidates += @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    if ($env:LOCALAPPDATA) { $sdkCandidates += Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    foreach ($sdk in @($sdkCandidates | Where-Object { ![string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)) {
        $candidate = Join-Path $sdk 'platform-tools/adb.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return (Resolve-Path -LiteralPath $candidate).Path }
    }
    $pathAdb = Get-Command adb.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($pathAdb) { return $pathAdb.Source }
    throw 'adb.exe was not found. Set sdk.dir in android/local.properties, set ANDROID_HOME / ANDROID_SDK_ROOT, or put Android SDK platform-tools on PATH.'
}

function ConvertTo-ReplandNativeArgument([string]$Value) {
    if ($Value.Length -gt 0 -and $Value -notmatch '[\s"]') { return $Value }
    $escaped = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
    $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
    return '"' + $escaped + '"'
}

function Invoke-ReplandAdb([string[]]$Arguments) {
    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = $adbPath
    $processInfo.Arguments = ($Arguments | ForEach-Object { ConvertTo-ReplandNativeArgument $_ }) -join ' '
    $processInfo.UseShellExecute = $false
    $processInfo.CreateNoWindow = $true
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $processInfo
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            Output = ($stdout.Result + [Environment]::NewLine + $stderr.Result).Trim()
        }
    } finally { $process.Dispose() }
}

$apkPath = Join-Path $PSScriptRoot 'android/app/build/outputs/apk/internal/app-internal.apk'
if (!(Test-Path -LiteralPath $apkPath -PathType Leaf)) {
    throw 'The internal APK is missing. Run .\Build-App.ps1 first, then run .\Start-App.ps1 again.'
}
$adbPath = Get-ReplandAdb
$inventory = Invoke-ReplandAdb -Arguments @('devices', '-l')
if ($inventory.ExitCode -ne 0) { throw "Cannot list Android devices: $($inventory.Output)" }
$devices = @($inventory.Output -split '\r?\n' | Where-Object { $_ -match '^([^\s]+)\s+device\b' } |
    ForEach-Object { [regex]::Match($_, '^([^\s]+)').Groups[1].Value })
if (![string]::IsNullOrWhiteSpace($Serial)) {
    if ($devices -notcontains $Serial) {
        throw "Device '$Serial' is not ready or not authorized. Check the USB debugging prompt and 'adb devices'.`n$($inventory.Output)"
    }
    $deviceSerial = $Serial
} elseif ($devices.Count -eq 1) {
    $deviceSerial = $devices[0]
} elseif ($devices.Count -eq 0) {
    throw "No authorized Android device is connected. Connect a phone with USB debugging or start an emulator, then retry.`n$($inventory.Output)"
} else {
    throw "Multiple Android devices are connected ($($devices -join ', ')). Run .\Start-App.ps1 -Serial <device-id> to choose one."
}

$boot = Invoke-ReplandAdb -Arguments @('-s', $deviceSerial, 'shell', 'getprop', 'sys.boot_completed')
if ($boot.ExitCode -ne 0 -or $boot.Output.Trim() -ne '1') {
    throw "Device '$deviceSerial' has not finished starting. Wait until its home screen is ready, then retry."
}
$packageName = 'com.swan1127.repland.current'
Write-Host "Device: $deviceSerial"
Write-Host "APK: $apkPath"
if ($PSCmdlet.ShouldProcess($deviceSerial, "Install/upgrade $packageName and open its main screen")) {
    $installation = Invoke-ReplandAdb -Arguments @('-s', $deviceSerial, 'install', '-r', $apkPath)
    Write-Host $installation.Output
    if ($installation.ExitCode -ne 0 -or $installation.Output -notmatch '(?m)^Success\s*$') {
        throw "Installation failed. No app was uninstalled or data cleared. For a signing/version conflict, use an APK signed with the installed app's key and a compatible version.`n$($installation.Output)"
    }
    $launch = Invoke-ReplandAdb -Arguments @('-s', $deviceSerial, 'shell', 'am', 'start', '-W', '-n', "$packageName/com.swan1127.repland.MainActivity")
    Write-Host $launch.Output
    if ($launch.ExitCode -ne 0 -or $launch.Output -match '(?im)^\s*(Error:|Error type|Exception)') {
        throw "App launch failed on '$deviceSerial'. The installed app and its data remain in place.`n$($launch.Output)"
    }
}
