@echo off
setlocal
echo Starting Tripline Services...

:: Navigate to root directory to ensure relative paths work
cd /d "%~dp0"

:: Start Backend
echo Starting Backend (Spring Boot)...
start "backend" cmd /c "cd backend && mvn spring-boot:run"

:: Start Frontend
echo Starting Frontend (Vite)...
start "frontend" cmd /c "cd frontend && npm run dev"

echo Successfully initiated startup.
echo Check the new terminal windows for progress.
endlocal
pause
