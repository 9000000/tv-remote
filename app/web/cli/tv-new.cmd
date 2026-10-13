@echo off
rem tv-new.cmd ^<tv-ip^>   Command-line way to add a TV (the web page does the same under "TV")
chcp 65001 >nul
cd /d "%~dp0..\..\.."
venv\Scripts\python.exe app\web\cli\tv_new.py %*
pause
