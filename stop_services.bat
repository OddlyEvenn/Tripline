@echo off
setlocal
echo Stopping Tripline Services...

:: Kill Java processes (Backend)
echo Stopping Backend (Java)...
taskkill /F /IM java.exe /T 2>nul
if %ERRORLEVEL% EQU 0 (
    echo Backend stopped.
) else (
    echo Backend was not running or could not be stopped.
)

:: Kill Node processes (Frontend/Vite)
echo Stopping Frontend (Node)...
taskkill /F /IM node.exe /T 2>nul
if %ERRORLEVEL% EQU 0 (
    echo Frontend stopped.
) else (
    echo Frontend was not running or could not be stopped.
)

echo.
echo All Tripline services have been signaled to stop.
endlocal
pause
