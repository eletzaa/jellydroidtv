@echo off
rem Jellydroid Installer starten (Windows)
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -STA -File "%~dp0installer-windows.ps1"
if errorlevel 1 pause
