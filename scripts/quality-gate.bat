@echo off
setlocal

echo [quality-gate] headless unit + FXML smoke suite
call mvn -q clean test
if errorlevel 1 exit /b %errorlevel%

echo [quality-gate] XML syntax for every production FXML
for %%F in (src\main\resources\fxml\*.fxml) do (
  powershell -NoProfile -Command "$null = [xml](Get-Content -Raw '%%F')"
  if errorlevel 1 exit /b %errorlevel%
)

echo [quality-gate] release package compilation
call mvn -q -DskipTests -Prelease-artifacts package
if errorlevel 1 exit /b %errorlevel%

echo [quality-gate] working-tree diff check
git diff --check
if errorlevel 1 exit /b %errorlevel%

echo [quality-gate] PASS
