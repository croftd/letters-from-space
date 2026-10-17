@echo off
rem Compiles everything under src\ into build\classes. Needs a JDK (17 or newer) on the PATH.
rem The file list uses forward slashes in quotes: javac argument files split unquoted names at
rem spaces and treat backslashes inside quotes as escapes.
setlocal EnableDelayedExpansion
cd /d "%~dp0"
if exist build\classes rmdir /s /q build\classes
mkdir build\classes
if exist build\sources.txt del build\sources.txt
for /r src %%f in (*.java) do (
  set "file=%%f"
  echo "!file:\=/!">> build\sources.txt
)
javac -encoding UTF-8 --release 17 -d build\classes @build\sources.txt
if errorlevel 1 (echo Compilation failed & exit /b 1)
echo Compiled into build\classes
