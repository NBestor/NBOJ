@echo off
setlocal
set "ROOT_DIR=%~dp0"
set "SCRIPT=%ROOT_DIR%start-website.ps1"

if not exist "%SCRIPT%" (
    echo Error: startup script not found: "%SCRIPT%"
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%"
if errorlevel 1 (
    echo.
    echo Startup failed. See the messages above.
    pause
    exit /b 1
)

exit /b 0
