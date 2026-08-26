@echo off
setlocal
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Please install Gradle 8.9 or run this project from Android Studio.
exit /b 1
