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
    echo Please install Java 21 or higher from https://adoptium.net/
    pause
    exit /b 1
)

echo Starting CryptoCarver...
echo.

cd /d "%~dp0"
mvn javafx:run

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Failed to start application
    echo Make sure Maven is installed and try: mvn clean install
    pause
)
