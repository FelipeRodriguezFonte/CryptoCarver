@echo off
setlocal EnableDelayedExpansion
REM Simple runner for CryptoCarver

cd /d "%~dp0"

REM Resolve the Maven-generated JAR without duplicating the project version.
set "JAR_FILE="
for %%F in (cryptocarver-*.jar) do (
    set "CANDIDATE=%%~nxF"
    echo !CANDIDATE! | findstr /I /C:"-original.jar" >nul
    if errorlevel 1 set "JAR_FILE=%%F"
)
if not defined JAR_FILE (
    for %%F in (target\cryptocarver-*.jar) do (
        set "CANDIDATE=%%~nxF"
        echo !CANDIDATE! | findstr /I /C:"-original.jar" >nul
        if errorlevel 1 set "JAR_FILE=%%F"
    )
)

if not exist "%JAR_FILE%" (
    echo Error: %JAR_FILE% not found.
    echo Please run 'mvn clean package -DskipTests' first to build the project.
    echo OR copy 'cryptocarver-^<version^>.jar' to this directory.
    pause
    exit /b 1
)

echo Starting CryptoCarver...
java -jar "%JAR_FILE%"
pause
endlocal
