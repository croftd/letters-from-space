@echo off
rem Starts the web dashboard on http://localhost:8080 (pass another port as the first argument).
rem Further options go to the server, e.g.  run_dashboard.bat 8080 --video data\space_video_2001.dat
cd /d "%~dp0"
set PORT=%1
if "%PORT%"=="" set PORT=8080
call "%~dp0build.bat" || exit /b 1
java -cp build\classes lettersfromspace.web.DashboardServer --port %PORT% --data data --web web %2 %3 %4 %5 %6 %7 %8 %9
