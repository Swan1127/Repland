param(
    [switch]$ShowSchedulePreview,
    [ValidatePattern('^[A-Za-z0-9_.-]+$')][string]$AvdName = 'Repland_API_36'
)
$ErrorActionPreference = 'Stop'
$frontendEnvironment = Join-Path $PSScriptRoot '../Environment/Activate-Repland.ps1'
if (Test-Path -LiteralPath $frontendEnvironment) { . $frontendEnvironment }
if ([string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
    if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT }
    elseif (Test-Path -LiteralPath "$env:LOCALAPPDATA/Android/Sdk") { $env:ANDROID_HOME = "$env:LOCALAPPDATA/Android/Sdk" }
    else { throw 'Set ANDROID_HOME to an Android SDK installation before launching the frontend.' }
}
$frontendAdb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
if (!(Test-Path -LiteralPath $frontendAdb)) { throw 'Android SDK platform-tools/adb.exe is missing. Check ANDROID_HOME.' }
$frontendApk = Join-Path $PSScriptRoot 'android/app/build/outputs/apk/frontendPreview/app-frontendPreview.apk'
if (!(Test-Path -LiteralPath $frontendApk)) { & "$PSScriptRoot/Build-Frontend.ps1" -SkipTests }
function Get-FrontendDevice {
    $frontendDevices = & $frontendAdb devices
    foreach ($deviceLine in $frontendDevices) {
        if ($deviceLine -match '^(emulator-\d+)\s+device\b') {
            $candidateSerial = $Matches[1]
            $candidateAvd = & $frontendAdb -s $candidateSerial emu avd name
            if ($candidateAvd -contains $AvdName) { return $candidateSerial }
        }
    }
}
$frontendSerial = Get-FrontendDevice
if (!$frontendSerial) {
    $existingEmulator = Get-Process -Name 'qemu-system-x86_64' -ErrorAction SilentlyContinue
    if ($existingEmulator -and !($existingEmulator | Where-Object { $_.MainWindowTitle -match [regex]::Escape($AvdName) })) {
        throw 'Another emulator is already running. Close it before opening this copy to avoid running two emulators.'
    }
    if (!$existingEmulator) {
        $frontendEmulator = Join-Path $env:ANDROID_HOME 'emulator/emulator.exe'
        if (!(Test-Path -LiteralPath $frontendEmulator)) { throw 'Install the Android SDK emulator package before launching an AVD.' }
        $frontendAvailableAvds = & $frontendEmulator -list-avds
        if ($frontendAvailableAvds -notcontains $AvdName) { throw "Create AVD '$AvdName' in Android Studio or pass -AvdName with an existing AVD name." }
        $frontendLogs = Join-Path $PSScriptRoot '.frontend-preview'
        New-Item -ItemType Directory -Force -Path $frontendLogs | Out-Null
        Start-Process -WindowStyle Hidden -FilePath $frontendEmulator -ArgumentList '-avd',$AvdName,'-memory','1536','-no-snapshot-load' -RedirectStandardOutput "$frontendLogs/emulator.stdout.log" -RedirectStandardError "$frontendLogs/emulator.stderr.log" | Out-Null
    }
    $frontendDeadline = [DateTime]::UtcNow.AddMinutes(3)
    do {
        Start-Sleep -Seconds 2
        $frontendSerial = Get-FrontendDevice
    } until ($frontendSerial -or [DateTime]::UtcNow -gt $frontendDeadline)
    if (!$frontendSerial) { throw 'Emulator did not become available.' }
}
$frontendBootDeadline = [DateTime]::UtcNow.AddMinutes(3)
while ((& $frontendAdb -s $frontendSerial shell getprop sys.boot_completed).Trim() -ne '1') {
    if ([DateTime]::UtcNow -gt $frontendBootDeadline) { throw 'Emulator boot timed out.' }
    Start-Sleep -Seconds 2
}
& $frontendAdb -s $frontendSerial install -r $frontendApk
if ($LASTEXITCODE -ne 0) { throw 'Frontend copy installation failed; no packages were uninstalled.' }
$frontendActivity = if ($ShowSchedulePreview) { 'com.swan1127.repland.designpreview.SchedulePreviewActivity' } else { 'com.swan1127.repland.MainActivity' }
& $frontendAdb -s $frontendSerial shell am start -n "com.swan1127.repland.frontend/$frontendActivity"
if ($LASTEXITCODE -ne 0) { throw 'Frontend copy launch failed.' }

# A desktop launch should reveal the interactive emulator without a console window.
$frontendWindows = Get-Process -Name 'emulator','qemu-system-x86_64' -ErrorAction SilentlyContinue |
    Where-Object { $_.MainWindowHandle -ne [IntPtr]::Zero }
if ($frontendWindows) {
    if (-not ('Repland.FrontendWindow' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
namespace Repland {
    public static class FrontendWindow {
        [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr window, int command);
        [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr window);
    }
}
'@
    }
    foreach ($frontendWindow in $frontendWindows) {
        [Repland.FrontendWindow]::ShowWindowAsync($frontendWindow.MainWindowHandle, 9) | Out-Null
        [Repland.FrontendWindow]::SetForegroundWindow($frontendWindow.MainWindowHandle) | Out-Null
    }
}
