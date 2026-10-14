# One-time setup on Windows: Python venv + packages, adb and ffmpeg into .\tools (gettools.py).
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..\..'))                 # repo root: venv and tools live there
$py = (Get-Command py -ErrorAction SilentlyContinue), (Get-Command python -ErrorAction SilentlyContinue) | Where-Object { $_ } | Select-Object -First 1
if (-not $py) { Write-Host 'Python 3.10+ is required: winget install Python.Python.3.12'; exit 1 }
if (-not (Test-Path 'venv\Scripts\python.exe')) { & $py.Source -m venv venv }
& 'venv\Scripts\python.exe' -m pip install -q --disable-pip-version-check -r app\web\requirements.txt
if ($LASTEXITCODE) { Write-Host 'pip install failed'; exit 1 }

& 'venv\Scripts\python.exe' app\web\gettools.py                     # adb and ffmpeg into .\tools
& 'venv\Scripts\python.exe' -c "import sys; sys.path.insert(0, 'app/web'); import tvtools, subprocess; print('adb   :', tvtools.ADB); print('ffmpeg:', tvtools.FFMPEG); print(subprocess.run([tvtools.ADB, 'version'], capture_output=True, text=True).stdout.splitlines()[0])"
Write-Host 'Done. Double-click start-windows.cmd; add your TV on the web page under the TV tab.'
