@echo off
rem tv-setup.cmd [apply^|undo] [host:port]
chcp 65001 >nul
cd /d "%~dp0..\..\.."
venv\Scripts\python.exe app\web\cli\tv_setup.py %*
