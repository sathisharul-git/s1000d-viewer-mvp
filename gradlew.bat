@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "LOCAL_JAVA_HOME=%SCRIPT_DIR%.tools\jdk17\jdk-17.0.18+8"
set "LOCAL_GRADLE=%SCRIPT_DIR%.tools\gradle\gradle-8.10.2\bin\gradle.bat"

if exist "%LOCAL_JAVA_HOME%\bin\java.exe" (
  set "JAVA_HOME=%LOCAL_JAVA_HOME%"
  set "PATH=%JAVA_HOME%\bin;%PATH%"
)

if exist "%LOCAL_GRADLE%" (
  call "%LOCAL_GRADLE%" %*
  exit /b %ERRORLEVEL%
)

where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  gradle %*
  exit /b %ERRORLEVEL%
)

echo gradle executable not found. Install Gradle or keep .tools\gradle present.
exit /b 1