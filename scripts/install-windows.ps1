# One-time setup on Windows: Python venv + packages, adb (Google platform-tools) into .\tools, ffmpeg check.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$py = (Get-Command py -ErrorAction SilentlyContinue), (Get-Command python -ErrorAction SilentlyContinue) | Where-Object { $_ } | Select-Object -First 1
if (-not $py) { Write-Host 'Python 3.10+ is required: winget install Python.Python.3.12'; exit 1 }
if (-not (Test-Path 'venv\Scripts\python.exe')) { & $py.Source -m venv venv }
& 'venv\Scripts\python.exe' -m pip install -q --disable-pip-version-check -r requirements.txt
if ($LASTEXITCODE) { Write-Host 'pip install failed'; exit 1 }

if (-not (Get-Command adb -ErrorAction SilentlyContinue) -and -not (Test-Path 'tools\platform-tools\adb.exe')) {
    Write-Host 'Downloading adb (Android platform-tools, from dl.google.com)...'
    New-Item -ItemType Directory -Force tools | Out-Null
    $zip = Join-Path $env:TEMP 'platform-tools.zip'
    & curl.exe -fsSL -o $zip 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip'
    if ($LASTEXITCODE) { Write-Host 'download failed'; exit 1 }
    Expand-Archive -Force $zip tools
    Remove-Item $zip
}
if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue) -and -not (Get-ChildItem tools -Filter ffmpeg.exe -Recurse -ErrorAction SilentlyContinue)) {
    Write-Host 'ffmpeg not found (needed for the screen view). Install it with:  winget install Gyan.FFmpeg   then open a new window.'
}
& 'venv\Scripts\python.exe' -c "import sys; sys.path.insert(0, 'app'); import tvtools, subprocess; print('adb   :', tvtools.ADB); print('ffmpeg:', tvtools.FFMPEG); print(subprocess.run([tvtools.ADB, 'version'], capture_output=True, text=True).stdout.splitlines()[0])"
Write-Host 'Done. Double-click start.cmd; add your TV on the web page under the TV tab.'
