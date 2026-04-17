@echo off
setlocal

set "ROOT_DIR=%~dp0"
set "APP_DIR=%ROOT_DIR%online-judge"
set "APP_URL=http://localhost:8081/"

if not exist "%APP_DIR%\mvnw.cmd" (
    echo Error: startup file not found: "%APP_DIR%\mvnw.cmd"
    pause
    exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
    echo Error: Java 17+ was not found on this machine.
    pause
    exit /b 1
)

powershell -NoProfile -Command "if (Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
if %errorlevel%==0 (
    start "" "%APP_URL%"
    exit /b 0
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%APP_DIR%\\mvnw.cmd' -ArgumentList 'spring-boot:run' -WorkingDirectory '%APP_DIR%'"
if errorlevel 1 (
    echo Error: failed to launch the project.
    pause
    exit /b 1
)

timeout /t 10 /nobreak >nul
start "" "%APP_URL%"

exit /b 0
