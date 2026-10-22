$ErrorActionPreference = 'Stop'
$replandSourceRoot = Join-Path $PSScriptRoot 'repland'
$replandSourceLink = Join-Path $PSScriptRoot 'app/src/main/java/com/swan1127/repland'
if (-not (Test-Path -LiteralPath $replandSourceRoot -PathType Container)) { throw 'The repland source directory is missing.' }
if (-not (Test-Path -LiteralPath $replandSourceLink)) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $replandSourceLink) -Force | Out-Null
    New-Item -ItemType Junction -Path $replandSourceLink -Target $replandSourceRoot | Out-Null
}
$replandEnvironmentRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../Environment'))
$replandJavaInstall = Get-ChildItem -LiteralPath (Join-Path $replandEnvironmentRoot 'JDK17') -Directory | Select-Object -First 1
if (-not $replandJavaInstall) { throw 'The local JDK is missing.' }
$env:JAVA_HOME = $replandJavaInstall.FullName
$env:ANDROID_HOME = Join-Path $replandEnvironmentRoot 'Android/sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $replandEnvironmentRoot 'android-user-home'
$env:ANDROID_AVD_HOME = Join-Path $replandEnvironmentRoot 'avd'
$env:GRADLE_USER_HOME = Join-Path $replandEnvironmentRoot 'gradle-home'
$env:JAVA_TOOL_OPTIONS = '-Duser.home=' + (Join-Path $replandEnvironmentRoot 'java-home').Replace('\', '/')
$env:PATH = "$env:JAVA_HOME/bin;$env:ANDROID_HOME/platform-tools;$env:ANDROID_HOME/emulator;$env:PATH"
$replandGradle = Join-Path $replandEnvironmentRoot 'Gradle/gradle-9.1.0/bin/gradle.bat'
if (-not (Test-Path -LiteralPath $replandGradle)) { throw 'Gradle 9.1.0 is missing.' }
