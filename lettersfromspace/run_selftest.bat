@echo off
cd /d "%~dp0"
call "%~dp0build.bat" || exit /b 1
java -cp build\classes lettersfromspace.SelfTest --data data
