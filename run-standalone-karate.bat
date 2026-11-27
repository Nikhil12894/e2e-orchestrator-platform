@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

set "JAR_PATH=%SCRIPT_DIR%backend\target\meterflow-karate-standalone.jar"

if not exist "%JAR_PATH%" (
    echo Standalone Karate JAR not found at: %JAR_PATH%
    echo Running build first...
    call "%SCRIPT_DIR%build-standalone-karate.bat"
    if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
)

java --enable-native-access=ALL-UNNAMED -jar "%JAR_PATH%" %*
