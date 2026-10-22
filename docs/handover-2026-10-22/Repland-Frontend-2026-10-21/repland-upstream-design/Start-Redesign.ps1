param([ValidatePattern('^[A-Za-z0-9_.-]+$')][string]$AvdName = 'Repland_API_36')
. "$PSScriptRoot/Activate-Redesign.ps1"
$replandAdb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$replandApk = Join-Path $PSScriptRoot 'app/build/outputs/apk/debug/app-debug.apk'
$replandPackage = 'com.swan1127.repland.redesign'
$replandLaunchLogs = Join-Path $PSScriptRoot '../verification'
New-Item -ItemType Directory -Path $replandLaunchLogs -Force | Out-Null
Start-Transcript -Path (Join-Path $replandLaunchLogs 'desktop-launch.log') -Append | Out-Null
try {
    if (-not (Test-Path -LiteralPath $replandApk)) {
        & "$PSScriptRoot/Build-Redesign.ps1" -Tasks ':app:assembleDebug'
        if ($LASTEXITCODE -ne 0) { throw 'The redesign APK could not be built.' }
    }
    function Get-RedesignDevice {
        $replandDevices = & $replandAdb devices
        foreach ($deviceLine in $replandDevices) {
            if ($deviceLine -match '^(emulator-\d+)\s+device\b') {
                $candidateSerial = $Matches[1]
                $candidateAvd = & $replandAdb -s $candidateSerial emu avd name
                if ($candidateAvd -contains $AvdName) { return $candidateSerial }
                throw "Another emulator is running: $candidateSerial. Close it before opening $AvdName."
            }
        }
    }
    $replandSerial = Get-RedesignDevice
    if (-not $replandSerial) {
        $replandExistingEmulator = Get-Process -Name 'qemu-system-x86_64','emulator' -ErrorAction SilentlyContinue
        if (-not $replandExistingEmulator) {
            $replandEmulator = Join-Path $env:ANDROID_HOME 'emulator/emulator.exe'
            $replandAvailableAvds = & $replandEmulator -list-avds
            if ($replandAvailableAvds -notcontains $AvdName) { throw "AVD $AvdName is missing." }
            Start-Process -FilePath $replandEmulator -ArgumentList @('-avd', $AvdName, '-no-snapshot-load', '-no-boot-anim') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $replandLaunchLogs 'emulator.stdout.log') -RedirectStandardError (Join-Path $replandLaunchLogs 'emulator.stderr.log')
        }
        $replandDeviceDeadline = [DateTime]::UtcNow.AddMinutes(3)
        do {
            Start-Sleep -Seconds 2
            $replandSerial = Get-RedesignDevice
        } until ($replandSerial -or [DateTime]::UtcNow -gt $replandDeviceDeadline)
        if (-not $replandSerial) { throw 'The emulator did not become available.' }
    }
    $replandBootDeadline = [DateTime]::UtcNow.AddMinutes(3)
    while ((& $replandAdb -s $replandSerial shell getprop sys.boot_completed).Trim() -ne '1') {
        if ([DateTime]::UtcNow -gt $replandBootDeadline) { throw 'The emulator did not finish booting.' }
        Start-Sleep -Seconds 2
    }
    & $replandAdb -s $replandSerial install -r $replandApk
    if ($LASTEXITCODE -ne 0) { throw 'The redesign APK could not be installed.' }
    & $replandAdb -s $replandSerial shell am start -n "$replandPackage/com.swan1127.repland.MainActivity"
    if ($LASTEXITCODE -ne 0) { throw 'The redesign application could not be opened.' }
    if (-not ('Repland.RedesignWindow' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
namespace Repland {
    public static class RedesignWindow {
        [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr window, int command);
        [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr window);
    }
}
'@
    }
    $replandEmulatorWindows = Get-Process -Name 'emulator','qemu-system-x86_64' -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne [IntPtr]::Zero }
    foreach ($emulatorWindow in $replandEmulatorWindows) {
        [Repland.RedesignWindow]::ShowWindowAsync($emulatorWindow.MainWindowHandle, 9) | Out-Null
        [Repland.RedesignWindow]::SetForegroundWindow($emulatorWindow.MainWindowHandle) | Out-Null
    }
} catch {
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Repland startup failed') | Out-Null
    throw
} finally {
    Stop-Transcript | Out-Null
}
