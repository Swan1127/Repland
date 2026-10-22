param([string]$OriginalZip = 'C:/Users/x16/Desktop/repland-upstream-design.zip')

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$source = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'repland-upstream-design'))
$evidence = Join-Path $PSScriptRoot 'verification'
$apk = Join-Path $source 'app/build/outputs/apk/debug/app-debug.apk'
$sourceZip = Join-Path $PSScriptRoot 'Repland-前端改造源码-2026-10-22.zip'
$deliveredApk = Join-Path $PSScriptRoot 'Repland-新版-2026-10-22.apk'

function Get-SourceFiles([string]$Directory) {
    foreach ($entry in Get-ChildItem -LiteralPath $Directory -Force) {
        if (($entry.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) { continue }
        if ($entry.PSIsContainer) {
            if ($entry.Name -notin @('.git', '.gradle', '.kotlin', 'build', 'apk')) { Get-SourceFiles $entry.FullName }
        } elseif ($entry.Name -ne 'local.properties' -and $entry.Extension -notin @('.log', '.jks', '.keystore', '.apk')) {
            $entry
        }
    }
}

$files = @(Get-SourceFiles $source | Sort-Object FullName)
$code = $files | Where-Object { $_.Extension -in @('.kt', '.xml', '.kts') }
$latestCode = ($code | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1).LastWriteTimeUtc
if ((Get-Item -LiteralPath $apk).LastWriteTimeUtc -lt $latestCode) { throw 'The APK predates a source edit. Build the final source first.' }

$originalHashes = [System.Collections.Generic.Dictionary[string,string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$archive = [System.IO.Compression.ZipFile]::OpenRead($OriginalZip)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName.EndsWith('/')) { continue }
        $relative = $entry.FullName.Substring($entry.FullName.IndexOf('/') + 1)
        if ($relative -match '(^|/)(build|apk|\.gradle|\.kotlin)/' -or $relative -eq 'local.properties') { continue }
        $stream = $entry.Open()
        $hasher = [System.Security.Cryptography.SHA256]::Create()
        try { $originalHashes[$relative] = [System.BitConverter]::ToString($hasher.ComputeHash($stream)).Replace('-', '') }
        finally { $hasher.Dispose(); $stream.Dispose() }
    }
} finally { $archive.Dispose() }

$currentPaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$manifest = @(
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($source.Length + 1).Replace('\', '/')
        $null = $currentPaths.Add($relative)
        $currentHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        $oldHash = if ($originalHashes.ContainsKey($relative)) { $originalHashes[$relative] } else { '' }
        if ($currentHash -ne $oldHash) {
            [PSCustomObject]@{ Status = $(if ($oldHash) { 'Modified' } else { 'Added' }); Path = $relative; OriginalSHA256 = $oldHash; CurrentSHA256 = $currentHash }
        }
    }
    foreach ($relative in $originalHashes.Keys) {
        if (-not $currentPaths.Contains($relative)) {
            [PSCustomObject]@{ Status = 'Removed'; Path = $relative; OriginalSHA256 = $originalHashes[$relative]; CurrentSHA256 = '' }
        }
    }
)
$manifest | Sort-Object Status,Path | Export-Csv -LiteralPath (Join-Path $evidence 'source-change-manifest.csv') -NoTypeInformation -Encoding UTF8

$zipStream = [System.IO.File]::Open($sourceZip, [System.IO.FileMode]::Create)
$bundle = [System.IO.Compression.ZipArchive]::new($zipStream, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($source.Length + 1).Replace('\', '/')
        $null = [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($bundle, $file.FullName, "repland-upstream-design/$relative", [System.IO.Compression.CompressionLevel]::Optimal)
    }
} finally { $bundle.Dispose(); $zipStream.Dispose() }
Copy-Item -LiteralPath $apk -Destination $deliveredApk -Force
Copy-Item -LiteralPath (Join-Path $source 'app/build/reports/lint-results-debug.xml') -Destination $evidence -Force
Copy-Item -LiteralPath (Join-Path $source 'app/build/reports/lint-results-debug.html') -Destination $evidence -Force

$suites = @(Get-ChildItem -LiteralPath (Join-Path $source 'app/build/test-results/testDebugUnitTest') -Filter 'TEST-*.xml')
$tests = 0; $failures = 0; $errors = 0; $skipped = 0
foreach ($file in $suites) {
    [xml]$result = Get-Content -LiteralPath $file.FullName
    $tests += [int]$result.testsuite.tests
    $failures += [int]$result.testsuite.failures
    $errors += [int]$result.testsuite.errors
    $skipped += [int]$result.testsuite.skipped
}
[xml]$lint = Get-Content -LiteralPath (Join-Path $source 'app/build/reports/lint-results-debug.xml')
$summary = [ordered]@{
    Date = '2026-10-22'
    SourceSnapshot = '5a15288'
    IntegrationDonor = 'repland-0.1.35.zip'
    DonorSHA256 = 'A87CB968C082ABD3155DCCD6F3BF96EABBC168F87FAE5B5B357AFA21D254A2B8'
    FrontendBaselineApkSHA256 = 'F631BC96887542DBB8620E5D36B77565C6745E3BAE0273E6BE7C4F152D7E9EB5'
    Package = 'com.swan1127.repland.redesign'
    TestSuites = $suites.Count
    Tests = $tests
    Failures = $failures
    Errors = $errors
    Skipped = $skipped
    LintErrors = @($lint.issues.issue | Where-Object { $_.severity -eq 'Error' }).Count
    LintWarnings = @($lint.issues.issue | Where-Object { $_.severity -eq 'Warning' }).Count
    ModifiedFiles = @($manifest | Where-Object { $_.Status -eq 'Modified' }).Count
    AddedFiles = @($manifest | Where-Object { $_.Status -eq 'Added' }).Count
    RemovedFiles = @($manifest | Where-Object { $_.Status -eq 'Removed' }).Count
    SourceArchiveSHA256 = (Get-FileHash -LiteralPath $sourceZip -Algorithm SHA256).Hash
    ApkSHA256 = (Get-FileHash -LiteralPath $deliveredApk -Algorithm SHA256).Hash
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'release-summary.json') -Encoding UTF8
@(
    "$($summary.ApkSHA256)  Repland-新版-2026-10-22.apk"
    "$($summary.SourceArchiveSHA256)  Repland-前端改造源码-2026-10-22.zip"
) | Set-Content -LiteralPath (Join-Path $evidence 'ARTIFACTS.sha256') -Encoding UTF8
$summary | ConvertTo-Json
