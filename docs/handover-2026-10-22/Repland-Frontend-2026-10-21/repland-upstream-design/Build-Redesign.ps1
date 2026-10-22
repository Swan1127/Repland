param([string[]]$Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest'))
. "$PSScriptRoot/Activate-Redesign.ps1"
& $replandGradle -p $PSScriptRoot '--no-watch-fs' '--rerun-tasks' '-Pkotlin.compiler.execution.strategy=in-process' @Tasks
exit $LASTEXITCODE
