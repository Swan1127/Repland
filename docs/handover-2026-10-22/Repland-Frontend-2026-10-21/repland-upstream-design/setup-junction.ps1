# Repland: rebuild the source junction that keeps a single copy of the sources.
# Run from the package root: .\setup-junction.ps1
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$target = Join-Path $root 'repland'
$linkParent = Join-Path $root 'app\src\main\java\com\swan1127'
$link = Join-Path $linkParent 'repland'

if (-not (Test-Path $target)) {
    Write-Error "repland/ not found next to this script. Run it from the package root."
}
if (Test-Path $link) {
    Write-Host "The junction already exists: $link"
    exit 0
}

New-Item -ItemType Directory -Force -Path $linkParent | Out-Null
cmd /c mklink /J "$link" "$target"
if ($LASTEXITCODE -ne 0) {
    Write-Error "mklink failed. Open an elevated PowerShell and run it again."
}
Write-Host "Junction created: $link -> $target"
