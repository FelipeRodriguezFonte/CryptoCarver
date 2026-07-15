@echo off
setlocal

set APP_NAME=CryptoCarver
set APP_VERSION=2.3.0
set MAIN_JAR=cryptocarver-2.3.0.jar
set MAIN_CLASS=com.cryptoforge.CryptoCalculatorModern
set ICON_PATH=src\main\resources\icons\app-icon.png
set INPUT_DIR=target
set OUTPUT_DIR=dist

echo ==========================================
echo   Building CryptoCarver (Windows)
echo ==========================================

REM Check if jpackage is in PATH
where jpackage >nul 2>nul
if %errorlevel% neq 0 (
    echo Error: jpackage not found in PATH.
    echo Please ensure you have JDK 14+ installed and added to your PATH.
    goto :error
)

REM 1. Build with Maven
echo [1/3] Building project with Maven...
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo Error: Maven build failed.
    goto :error
)

if not exist "%INPUT_DIR%\%MAIN_JAR%" (
    echo Error: %MAIN_JAR% not found in %INPUT_DIR%
    goto :error
)

REM 2. Run jpackage
echo [2/3] Creating Windows application image...
echo Note: Using PNG icon. For best results on Windows, revert to an .ico file.

REM Note: --type app-image creates a directory containing the exe.
REM Use --type exe or --type msi for installers (requires WiX Toolset).
jpackage ^
  --name "%APP_NAME%" ^
  --app-version "%APP_VERSION%" ^
  --input "%INPUT_DIR%" ^
  --main-jar "%MAIN_JAR%" ^
  --main-class "%MAIN_CLASS%" ^
  --type app-image ^
  --icon "%ICON_PATH%" ^
  --dest "%OUTPUT_DIR%" ^
  --java-options "--enable-preview" ^
  --java-options "-Xmx512m" ^
  --win-console ^
  --verbose

if %errorlevel% neq 0 (
    echo Error: jpackage execution failed.
    goto :error
)

echo.
echo SUCCESS! Application built in %OUTPUT_DIR%\%APP_NAME%
echo You can run it from: %OUTPUT_DIR%\%APP_NAME%\%APP_NAME%.exe
goto :end

:error
echo.
echo FAILED. Please check the logs above.
exit /b 1

:end
endlocal
