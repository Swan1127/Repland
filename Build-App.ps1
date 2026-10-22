[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [switch]$SkipTests,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Get-ReplandJavaHome {
    $candidates = @($env:JAVA_HOME, $env:JDK_HOME)
    foreach ($programRoot in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA)) {
        if ([string]::IsNullOrWhiteSpace($programRoot)) { continue }
        $candidates += Join-Path $programRoot 'Android/Android Studio/jbr'
        $candidates += Join-Path $programRoot 'Programs/Android Studio/jbr'
        foreach ($vendor in @('Java', 'Eclipse Adoptium', 'Microsoft', 'Eclipse Foundation')) {
            $vendorRoot = Join-Path $programRoot $vendor
            if (Test-Path -LiteralPath $vendorRoot -PathType Container) {
                $candidates += @(Get-ChildItem -LiteralPath $vendorRoot -Directory -ErrorAction SilentlyContinue |
                    Where-Object { $_.Name -match '(?i)jdk|jbr' } | Select-Object -ExpandProperty FullName)
            }
        }
    }
    $pathJava = Get-Command java.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($pathJava) { $candidates += Split-Path -Parent (Split-Path -Parent $pathJava.Source) }
    foreach ($candidate in @($candidates | Where-Object { ![string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)) {
        $release = Join-Path $candidate 'release'
        if (!(Test-Path -LiteralPath (Join-Path $candidate 'bin/java.exe') -PathType Leaf) -or
            !(Test-Path -LiteralPath (Join-Path $candidate 'bin/javac.exe') -PathType Leaf) -or
            !(Test-Path -LiteralPath $release -PathType Leaf)) { continue }
        $versionLine = Get-Content -LiteralPath $release | Where-Object { $_ -match '^JAVA_VERSION="([^"]+)"' } | Select-Object -First 1
        if ($versionLine -match '^JAVA_VERSION="(?:1\.)?(\d+)') {
            if ([int]$Matches[1] -ge 17) { return (Resolve-Path -LiteralPath $candidate).Path }
        }
    }
    throw 'JDK 17 or newer was not found. Set JAVA_HOME to a full JDK, or install Android Studio with its bundled jbr.'
}

$appRoot = $PSScriptRoot
$androidProject = Join-Path $appRoot 'android'
if (!(Test-Path -LiteralPath (Join-Path $androidProject 'gradlew.bat') -PathType Leaf)) {
    throw "Android project was not found at $androidProject. Keep this script in the Repland project root."
}
$javaHome = Get-ReplandJavaHome
$gradleCommand = Join-Path $androidProject 'gradlew.bat'
$runtimeRoot = Join-Path $appRoot '.gradle-runtime'
if (Test-Path -LiteralPath $runtimeRoot -PathType Container) {
    $runtimes = @(Get-ChildItem -LiteralPath $runtimeRoot -Directory -Filter 'gradle-9*' |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/gradle.bat') -PathType Leaf } |
        Sort-Object { [version]($_.Name -replace '^gradle-', '') } -Descending)
    if ($runtimes.Count -gt 0) { $gradleCommand = Join-Path $runtimes[0].FullName 'bin/gradle.bat' }
}
$gradleArguments = @('-p', $androidProject, '--no-daemon', '--max-workers=2')
if ($Offline) { $gradleArguments += '--offline' }
$gradleArguments += @(':app:assembleDebug', ':app:assembleInternal')
if (!$SkipTests) { $gradleArguments += ':app:testDebugUnitTest' }
$gradleArguments += ':app:verifyOfflineMvpBoundary'

Write-Host "JDK: $javaHome"
Write-Host "Gradle: $gradleCommand"
Write-Host ('Tasks: ' + (($gradleArguments | Where-Object { $_ -like ':app:*' }) -join ', '))
Write-Host 'Android SDK: Gradle uses android/local.properties or ANDROID_HOME / ANDROID_SDK_ROOT.'
if ($PSCmdlet.ShouldProcess($androidProject, 'Build debug and internal APKs and run the selected verification tasks')) {
    $previousJavaHome = $env:JAVA_HOME
    $previousPath = $env:PATH
    $previousGradleUserHome = $env:GRADLE_USER_HOME
    try {
        $env:JAVA_HOME = $javaHome
        $env:PATH = (Join-Path $javaHome 'bin') + [IO.Path]::PathSeparator + $previousPath
        $localCache = Join-Path $appRoot '.gradle-cache'
        if (Test-Path -LiteralPath $localCache -PathType Container) {
            $env:GRADLE_USER_HOME = $localCache
            Write-Host "Gradle cache: $localCache"
        }
        & $gradleCommand @gradleArguments
        if ($LASTEXITCODE -ne 0) { throw "App build failed with exit code $LASTEXITCODE. See the Gradle error above." }
        Write-Host ('Debug APK: ' + (Join-Path $androidProject 'app/build/outputs/apk/debug/app-debug.apk'))
        Write-Host ('Internal APK: ' + (Join-Path $androidProject 'app/build/outputs/apk/internal/app-internal.apk'))
    } finally {
        $env:JAVA_HOME = $previousJavaHome
        $env:PATH = $previousPath
        $env:GRADLE_USER_HOME = $previousGradleUserHome
    }
}
