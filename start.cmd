@echo off
rem Double-click to start the TV remote and open it in the browser. Close the window to stop.
chcp 65001 >nul
cd /d "%~dp0"
if not exist venv\Scripts\python.exe call install-windows.cmd
start "" "http://127.0.0.1:8765"
venv\Scripts\python.exe app\server.py %*
