@echo off
setlocal
set "ROOT=%~dp0"
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
set "LOCAL=%ROOT%.gradle-dist\gradle-8.13\bin\gradle.bat"
if not exist "%LOCAL%" (
  echo Getting official Gradle 8.13 for the first build...
  if not exist "%ROOT%.gradle-dist" mkdir "%ROOT%.gradle-dist"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $p='%ROOT%.gradle-dist'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.13-bin.zip' -OutFile ($p+'\gradle.zip'); Expand-Archive -LiteralPath ($p+'\gradle.zip') -DestinationPath $p -Force; Remove-Item -LiteralPath ($p+'\gradle.zip')"
  if errorlevel 1 exit /b 1
)
call "%LOCAL%" %*
exit /b %ERRORLEVEL%
