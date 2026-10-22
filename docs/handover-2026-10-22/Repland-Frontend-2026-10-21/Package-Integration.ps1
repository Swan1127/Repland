param()

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
. (Join-Path $PSScriptRoot 'Package-Redesign.ps1')
$evidence = Join-Path $PSScriptRoot 'verification'
$summary = Get-Content -LiteralPath (Join-Path $evidence 'release-summary.json') -Raw | ConvertFrom-Json
if ($summary.Failures -ne 0 -or $summary.Errors -ne 0 -or $summary.Skipped -ne 0) { throw 'Integration tests must pass before packaging.' }
$source = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'repland-upstream-design'))
$baseline = Join-Path $PSScriptRoot 'Repland-前端阶段基线-F631-2026-10-22.zip'
$baselineHashes = [System.Collections.Generic.Dictionary[string,string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$archive = [System.IO.Compression.ZipFile]::OpenRead($baseline)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName.EndsWith('/')) { continue }
        $relative = $entry.FullName.Substring($entry.FullName.IndexOf('/') + 1)
        $stream = $entry.Open()
        $hasher = [System.Security.Cryptography.SHA256]::Create()
        try { $baselineHashes[$relative] = [System.BitConverter]::ToString($hasher.ComputeHash($stream)).Replace('-', '') }
        finally { $hasher.Dispose(); $stream.Dispose() }
    }
} finally { $archive.Dispose() }
$paths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$changes = @(
    foreach ($file in Get-SourceFiles $source) {
        $relative = $file.FullName.Substring($source.Length + 1).Replace('\', '/')
        $null = $paths.Add($relative)
        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        $previous = if ($baselineHashes.ContainsKey($relative)) { $baselineHashes[$relative] } else { '' }
        if ($hash -ne $previous) {
            [pscustomobject]@{ Status = $(if ($previous) { 'Modified' } else { 'Added' }); Path = $relative; BaselineSHA256 = $previous; CurrentSHA256 = $hash }
        }
    }
    foreach ($relative in $baselineHashes.Keys) {
        if (-not $paths.Contains($relative)) { [pscustomobject]@{ Status = 'Removed'; Path = $relative; BaselineSHA256 = $baselineHashes[$relative]; CurrentSHA256 = '' } }
    }
)
$changes | Sort-Object Status,Path | Export-Csv -LiteralPath (Join-Path $evidence 'integration-source-change-manifest.csv') -NoTypeInformation -Encoding UTF8
$testSource = Join-Path $source 'app/build/test-results/testDebugUnitTest'
$testDestination = Join-Path $evidence 'integration-unit-results'
New-Item -ItemType Directory -Path $testDestination -Force | Out-Null
Get-ChildItem -LiteralPath $testSource -Filter 'TEST-*.xml' | Copy-Item -Destination $testDestination -Force
$parts = @(
    'README-交付说明.md', 'Repland-新版-2026-10-22.apk',
    'Repland-前端改造源码-2026-10-22.zip', 'Repland-前端阶段基线-F631-2026-10-22.zip',
    'Package-Redesign.ps1', 'Package-Integration.ps1',
    'integration-release-validation-2026-10-22.log', 'release-validation-2026-10-22.log'
)
$bundlePath = Join-Path $PSScriptRoot 'Repland-多Agent与记忆模型融合交付-2026-10-22.zip'
$zipStream = [System.IO.File]::Open($bundlePath, [System.IO.FileMode]::Create)
$bundle = [System.IO.Compression.ZipArchive]::new($zipStream, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($relative in $parts) {
        $file = Join-Path $PSScriptRoot $relative
        $null = [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($bundle, $file, $relative, [System.IO.Compression.CompressionLevel]::Optimal)
    }
    foreach ($file in Get-ChildItem -LiteralPath $evidence -File -Recurse) {
        if ($file.Name -match '^emulator.*\.log$') { continue }
        $relative = $file.FullName.Substring($PSScriptRoot.Length + 1).Replace('\', '/')
        $null = [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($bundle, $file.FullName, $relative, [System.IO.Compression.CompressionLevel]::Optimal)
    }
} finally { $bundle.Dispose(); $zipStream.Dispose() }
Get-FileHash -LiteralPath $bundlePath -Algorithm SHA256 | Format-List
