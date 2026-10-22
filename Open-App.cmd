@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-App.ps1" %*
set "_replandExitCode=%errorlevel%"
if not "%_replandExitCode%"=="0" pause
exit /b %_replandExitCode%
