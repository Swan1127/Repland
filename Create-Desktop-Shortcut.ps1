$ErrorActionPreference = 'Stop'
$frontendLauncher = Join-Path $PSScriptRoot 'Start-Frontend.ps1'
if (!(Test-Path -LiteralPath $frontendLauncher)) { throw 'Frontend launcher is missing.' }
$frontendDesktop = [Environment]::GetFolderPath('Desktop')
$frontendShell = New-Object -ComObject WScript.Shell
$frontendLinkPath = Join-Path $frontendDesktop ([string]::Concat('Repland ', [char]0x524D, [char]0x7AEF, [char]0x65B0, [char]0x7248, '.lnk'))
$frontendLink = $frontendShell.CreateShortcut($frontendLinkPath)
$frontendLink.TargetPath = Join-Path $env:WINDIR 'System32/WindowsPowerShell/v1.0/powershell.exe'
$frontendLink.Arguments = '-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $frontendLauncher + '"'
$frontendLink.WorkingDirectory = $PSScriptRoot
$frontendLink.Description = 'Open the latest Repland frontend build.'
$frontendIcon = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../Environment/Android/sdk/emulator/emulator.exe'))
if (Test-Path -LiteralPath $frontendIcon) { $frontendLink.IconLocation = "$frontendIcon,0" }
$frontendLink.Save()
$frontendSavedLink = $frontendShell.CreateShortcut($frontendLinkPath)
if ($frontendSavedLink.TargetPath -ne $frontendLink.TargetPath -or $frontendSavedLink.Arguments -ne $frontendLink.Arguments) {
    throw 'Desktop shortcut verification failed.'
}
[PSCustomObject]@{Path=$frontendLinkPath;Target=$frontendSavedLink.TargetPath;Arguments=$frontendSavedLink.Arguments;Verified=$true} | ConvertTo-Json
