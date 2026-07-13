@echo off
setlocal
set VERSION=8.13
set BASE=%~dp0
set DIST=%BASE%.gradle-dist\gradle-%VERSION%
set ZIP=%BASE%.gradle-dist\gradle-%VERSION%-bin.zip
if exist "%DIST%\bin\gradle.bat" goto run
if not exist "%BASE%.gradle-dist" mkdir "%BASE%.gradle-dist"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%ZIP%'"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP%' '%BASE%.gradle-dist'"
:run
call "%DIST%\bin\gradle.bat" %*
endlocal
