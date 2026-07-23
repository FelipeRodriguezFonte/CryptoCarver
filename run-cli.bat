@echo off
REM CryptoCarver CLI Launcher for Windows
setlocal
cd /d "%~dp0"

set "MAVEN_CMD=%MAVEN_BIN%"
if "%MAVEN_CMD%"=="" (
    for %%M in (mvn.cmd) do set "MAVEN_CMD=%%~$PATH:M"
)
if "%MAVEN_CMD%"=="" (
    echo Maven was not found. Set MAVEN_BIN or add mvn.cmd to PATH. 1>&2
    exit /b 127
)

call "%MAVEN_CMD%" -q -DskipTests compile exec:java -Dexec.mainClass=com.cryptocarver.CryptoCarverCli -Dexec.args="%*"
exit /b %errorlevel%
