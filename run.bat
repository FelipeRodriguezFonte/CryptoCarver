@echo off
REM CryptoCarver Launcher for Windows

echo ================================================
echo   CryptoCarver - Advanced Crypto Test Tool
echo ================================================
echo.

REM Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install JDK 17 or newer from https://adoptium.net/
    pause
    exit /b 1
)

cd /d "%~dp0"
where mvn.cmd >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Maven is not installed or not in PATH
    echo Install Maven 3.8+ or use run_simple.bat with an already-built JAR.
    pause
    exit /b 1
)

REM Package instead of javafx:run, so the executable JAR is left in target\ and
REM run_simple.bat can start the app afterwards without a Maven build. No clean
REM here: the build is incremental, only what changed is recompiled and shaded.
echo Building CryptoCarver...
echo.
call mvn package -DskipTests
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Maven build failed
    echo Try a clean build with: mvn clean package -DskipTests
    pause
    exit /b 1
)

REM run_simple.bat resolves the JAR name from the Maven artifact and launches it.
call "%~dp0run_simple.bat"
