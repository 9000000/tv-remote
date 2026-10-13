@echo off
rem Windows: double-click this file. It starts the TV remote on this computer and opens it in the browser.
rem The first run installs what it needs (Python packages, adb). Close the window to stop.
chcp 65001 >nul
cd /d "%~dp0"
if not exist venv\Scripts\python.exe powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0app\web\install-windows.ps1"
start "" "http://127.0.0.1:8765"
venv\Scripts\python.exe app\web\server.py %*
