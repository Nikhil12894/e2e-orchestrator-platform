@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

echo ========================================================================
echo  Building MeterFlow Standalone Karate CLI Runner
echo  (Karate Core 2.1.4 + DbHelper + KafkaHelper + Universal JDBC Drivers)
echo ========================================================================

echo.
echo Packaging standalone-karate JAR...
cd backend
call mvn clean package -Pstandalone-karate -DskipTests
if %ERRORLEVEL% neq 0 (
    echo.
    echo BUILD FAILED!
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================================================
echo  BUILD SUCCESSFUL!
echo  Output JAR: backend\target\meterflow-karate-standalone.jar
echo  Run via:    run-standalone-karate.bat ^<feature-file^> [options]
echo  Example:    run-standalone-karate.bat path\to\workflow.feature
echo ========================================================================
