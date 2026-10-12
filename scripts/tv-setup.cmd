@echo off
rem tv-setup.cmd [apply^|undo] [host:port]
chcp 65001 >nul
cd /d "%~dp0.."
venv\Scripts\python.exe scripts\tv_setup.py %*
