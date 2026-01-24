@echo off
REM ============================================================================
REM Heronix Guardian - Startup Script
REM Zero-Trust Third-Party Integration Layer
REM ============================================================================

title Heronix Guardian Service

echo.
echo  ============================================
echo   HERONIX GUARDIAN
echo   Zero-Trust Third-Party Integration Layer
echo  ============================================
echo.

REM Check for Java
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java is not installed or not in PATH
    echo Please install Java 21 or later
    pause
    exit /b 1
)

REM Check Java version
for /f tokens^=2-5^ delims^=.-_+^" %%j in ('java -fullversion 2^>^&1') do set "jver=%%j"
if %jver% LSS 21 (
    echo [WARNING] Java 21 or later is recommended. Found version %jver%
)

REM Set environment variables (override these as needed)
if not defined GUARDIAN_MASTER_KEY set GUARDIAN_MASTER_KEY=defaultDevKeyMustBeAtLeast32Chars!
if not defined SIS_API_URL set SIS_API_URL=http://localhost:9580

echo [INFO] Starting Heronix Guardian on port 9680...
echo [INFO] SIS API URL: %SIS_API_URL%
echo.

REM Check if JAR exists
if exist target\heronix-guardian-1.0.0.jar (
    echo [INFO] Running from compiled JAR...
    java -jar target\heronix-guardian-1.0.0.jar
) else (
    echo [INFO] Running with Maven...
    call mvnw.cmd spring-boot:run
)

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Guardian failed to start
    pause
)
