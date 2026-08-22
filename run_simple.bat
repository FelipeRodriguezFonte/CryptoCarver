@echo off
setlocal EnableDelayedExpansion
REM Simple runner for CryptoCarver

cd /d "%~dp0"

REM Resolve the Maven-generated JAR without duplicating the project version.
REM The substring test keeps the shaded artifact and skips the unshaded copy;
REM a piped findstr would not work here, because the child cmd.exe of a pipe
REM does not inherit EnableDelayedExpansion and would compare "!CANDIDATE!".
set "JAR_FILE="
for %%F in (cryptocarver-*.jar target\cryptocarver-*.jar) do (
    if not defined JAR_FILE (
        set "CANDIDATE=%%~nxF"
        if "!CANDIDATE:-original.jar=!"=="!CANDIDATE!" set "JAR_FILE=%%F"
    )
)

if not defined JAR_FILE (
    echo Error: CryptoCarver executable JAR not found.
    echo Please run run.bat, or 'mvn clean package -DskipTests', to build the project,
    echo OR copy 'cryptocarver-^<version^>.jar' to this directory.
    pause
    exit /b 1
)

echo Starting CryptoCarver...
java -jar "%JAR_FILE%"
pause
endlocal
