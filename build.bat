@echo off
REM ============================================================================
REM Heronix Guardian - Build Script
REM ============================================================================

title Building Heronix Guardian

echo.
echo  ============================================
echo   BUILDING HERONIX GUARDIAN
echo  ============================================
echo.

REM Check for Maven
where mvn >nul 2>nul
if %ERRORLEVEL% neq 0 (
    if exist mvnw.cmd (
        echo [INFO] Using Maven Wrapper...
        set MVN=mvnw.cmd
    ) else (
        echo [ERROR] Maven is not installed and no wrapper found
        pause
        exit /b 1
    )
) else (
    set MVN=mvn
)

echo [INFO] Cleaning and packaging...
call %MVN% clean package -DskipTests

if %ERRORLEVEL% equ 0 (
    echo.
    echo  ============================================
    echo   BUILD SUCCESSFUL
    echo  ============================================
    echo.
    echo  Output JAR: target\heronix-guardian-1.0.0.jar
    echo.
    echo  To run: java -jar target\heronix-guardian-1.0.0.jar
    echo  Or use: start-guardian.bat
    echo.
) else (
    echo.
    echo [ERROR] Build failed
)

pause
