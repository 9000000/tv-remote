@echo off
rem One-time setup on Windows (venv, packages, adb).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\install-windows.ps1"
