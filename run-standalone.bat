@echo off
setlocal

set SCRIPT_DIR=%~dp0
set JAR_PATH=%SCRIPT_DIR%standalone\target\meterflow-standalone.jar

if not exist "%JAR_PATH%" (
    echo Executable JAR not found at %JAR_PATH%
    echo Running build first...
    call "%SCRIPT_DIR%build-standalone.bat"
)

echo ========================================================================
echo  Starting MeterFlow Standalone...
echo  Web UI:     http://localhost:8080
echo  Database:   .\data\meterflow.mv.db
echo  Memory cap: 256MB RAM
echo  Press Ctrl+C to stop
echo ========================================================================

if not exist "%SCRIPT_DIR%data" mkdir "%SCRIPT_DIR%data"
java -Xmx256m --enable-native-access=ALL-UNNAMED -jar "%JAR_PATH%" %*
