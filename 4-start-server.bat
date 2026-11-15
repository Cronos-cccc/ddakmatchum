@echo off
chcp 65001 >nul
title DdakMatchum 4 - store server
if exist "%~dp0scripts\env.bat" call "%~dp0scripts\env.bat"
if exist "%ProgramFiles%\nodejs\node.exe" set "PATH=%ProgramFiles%\nodejs;%PATH%"
where node >nul 2>nul
if errorlevel 1 goto nonode
cd /d "%~dp0server"
echo 매장 서버를 켭니다. 데모하는 동안 이 창은 닫지 마세요.
echo 방화벽 허용 창이 뜨면 허용을 눌러 주세요.
start "" powershell -NoProfile -WindowStyle Hidden -Command "Start-Sleep -Seconds 2; Start-Process 'http://localhost:8787/store'"
node server.js
pause
exit /b 0

:nonode
echo Node.js 를 찾지 못했습니다. 먼저 1-setup.bat 을 실행해 주세요.
pause
exit /b 1
