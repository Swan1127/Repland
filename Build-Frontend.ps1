param([switch]$SkipTests, [switch]$DeviceTests)
$ErrorActionPreference = 'Stop'
$frontendEnvironment = Join-Path $PSScriptRoot '../Environment/Activate-Repland.ps1'
if (Test-Path -LiteralPath $frontendEnvironment) { . $frontendEnvironment }
$frontendProject = Join-Path $PSScriptRoot 'android'
$frontendTasks = @(':app:assembleFrontendPreview', ':app:verifyOfflineMvpBoundary')
if (!$SkipTests) { $frontendTasks += ':app:testFrontendPreviewUnitTest' }
if ($DeviceTests) { $frontendTasks += ':app:connectedDebugAndroidTest' }
$frontendTestBuildType = if ($DeviceTests) { 'debug' } else { 'frontendPreview' }
& "$frontendProject/gradlew.bat" -p $frontendProject --no-daemon --max-workers=2 '-Pkotlin.compiler.execution.strategy=in-process' "-PreplandTestBuildType=$frontendTestBuildType" @frontendTasks
if ($LASTEXITCODE -ne 0) { throw "Frontend build failed with exit code $LASTEXITCODE" }
