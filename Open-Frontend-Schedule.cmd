@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-Frontend.ps1" -ShowSchedulePreview
if errorlevel 1 pause
