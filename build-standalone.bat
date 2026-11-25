@echo off
setlocal

echo ========================================================================
echo  Building MeterFlow Standalone (Zero-Docker / Bundled UI ^& H2 DB)
echo ========================================================================

echo.
echo [1/2] Compiling React Visual Canvas UI (frontend)...
cd frontend
call npm run build
if %errorlevel% neq 0 exit /b %errorlevel%

echo.
echo [2/2] Packaging Standalone Executable JAR (standalone)...
cd ..\standalone
call mvn clean package -DskipTests
if %errorlevel% neq 0 exit /b %errorlevel%

cd ..
echo.
echo ========================================================================
echo  BUILD SUCCESSFUL!
echo  Output JAR: standalone\target\meterflow-standalone.jar
echo  Run via:    run-standalone.bat
echo ========================================================================
