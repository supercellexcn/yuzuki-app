@echo off
rem One-click build / install / launch for the Debug APK (double-click friendly).
rem English-only messages on purpose: CMD uses the OEM codepage, Chinese text would garble.
setlocal

set "PROJ=%~dp0"
if "%PROJ:~-1%"=="\" set "PROJ=%PROJ:~0,-1%"
set "JAVA_HOME=%PROJ%\.toolchain\jdk17"
set "ADB=%PROJ%\.toolchain\sdk\platform-tools\adb.exe"
set "APK=%PROJ%\app\build\outputs\apk\debug\app-debug.apk"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [!] JDK 17 not found at %JAVA_HOME%
  echo     Set JAVA_HOME to a JDK 17 installation and retry.
  exit /b 1
)
if not exist "%ADB%" (
  echo [!] adb not found at %ADB%
  exit /b 1
)
if not exist "%PROJ%\gradlew.bat" (
  echo [!] gradlew.bat not found at %PROJ%
  exit /b 1
)

set "DEV="
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
  if "%%B"=="device" set "DEV=%%A"
)
if not defined DEV (
  echo [!] No authorized device. Enable USB debugging and accept the prompt.
  "%ADB%" devices
  exit /b 1
)
set "ANDROID_SERIAL=%DEV%"
echo [*] Device: %DEV%

echo [*] Building and installing ...
call "%PROJ%\gradlew.bat" -p "%PROJ%" :app:installDebug --console=plain
if errorlevel 1 (
  echo [!] Build or install failed.
  exit /b 1
)

echo [*] Launching ...
"%ADB%" shell am start -n com.yuzuki.app/.MainActivity
if errorlevel 1 (
  echo [!] Launch failed.
  exit /b 1
)

echo [OK] Done. APK: %APK%
echo      Tip: run-debug.sh supports --fast / --connect / --no-launch / --log
endlocal
