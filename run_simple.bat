@echo off
REM Simple runner for CryptoCarver

cd /d "%~dp0"

REM Check if JAR exists in current directory (portable mode)
if exist "cryptocarver-2.3.0.jar" (
    set JAR_FILE=cryptocarver-2.3.0.jar
) else (
    REM Check if JAR exists in target directory (maven project mode)
    if exist "target\cryptocarver-2.3.0.jar" (
        set JAR_FILE=target\cryptocarver-2.3.0.jar
    ) else (
        set JAR_FILE=target\cryptocarver-2.3.0.jar
    )
)

if not exist "%JAR_FILE%" (
    echo Error: %JAR_FILE% not found.
    echo Please run 'mvn clean package -DskipTests' first to build the project.
    echo OR copy 'cryptocarver-2.3.0.jar' to this directory.
    pause
    exit /b 1
)

echo Starting CryptoCarver...
java -jar "%JAR_FILE%"
pause
