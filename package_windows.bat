@echo off
setlocal

set APP_NAME=CryptoCarver
set MAIN_CLASS=com.cryptocarver.Launcher
set ICON_PATH=src\main\resources\icons\app-icon.png
set INPUT_DIR=target
if "%PACKAGE_OUTPUT_DIR%"=="" (set OUTPUT_DIR=dist) else (set OUTPUT_DIR=%PACKAGE_OUTPUT_DIR%)
if "%PACKAGE_TYPE%"=="" set PACKAGE_TYPE=app-image

echo ==========================================
echo   Building CryptoCarver (Windows)
echo ==========================================

REM Check if jpackage is in PATH
where jpackage >nul 2>nul
if %errorlevel% neq 0 (
    echo Error: jpackage not found in PATH.
    echo Please ensure you have JDK 17+ installed and added to your PATH.
    goto :error
)

for /f "tokens=1" %%i in ('jpackage --version') do set JAVA_VERSION=%%i
for /f "tokens=1 delims=." %%i in ("%JAVA_VERSION%") do set JAVA_MAJOR=%%i
if %JAVA_MAJOR% LSS 17 (
    echo Error: Detected JDK version %JAVA_VERSION%, but JDK 17 or higher is required.
    echo Please install JDK 17+ and add it to your PATH.
    goto :error
)

REM 1. Build with Maven
echo [1/3] Building project with Maven...
echo Warning: This script performs a clean build. Do not run it concurrently with an active development instance.
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo Error: Maven build failed.
    goto :error
)

for /f "usebackq delims=" %%V in (`mvn -q -DforceStdout help:evaluate -Dexpression=project.version`) do set APP_VERSION=%%V
if "%APP_VERSION%"=="" (
    echo Error: Unable to resolve the Maven project version.
    goto :error
)
set MAIN_JAR=cryptocarver-%APP_VERSION%.jar

if not exist "%INPUT_DIR%\%MAIN_JAR%" (
    echo Error: %MAIN_JAR% not found in %INPUT_DIR%
    goto :error
)

REM 2. Run jpackage
echo [2/3] Creating Windows %PACKAGE_TYPE% package...
echo Note: Using PNG icon. For best results on Windows, revert to an .ico file.

REM --type app-image creates a directory containing the exe.
REM Set PACKAGE_TYPE=exe or PACKAGE_TYPE=msi for installers (requires WiX Toolset).
jpackage ^
  --name "%APP_NAME%" ^
  --app-version "%APP_VERSION%" ^
  --input "%INPUT_DIR%" ^
  --main-jar "%MAIN_JAR%" ^
  --main-class "%MAIN_CLASS%" ^
  --type "%PACKAGE_TYPE%" ^
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
