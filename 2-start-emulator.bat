@echo off
chcp 65001 >nul
title DdakMatchum 2 - emulator
if not exist "%~dp0scripts\env.bat" goto nosetup
call "%~dp0scripts\env.bat"
if not exist "%ANDROID_HOME%\emulator\emulator.exe" goto nosetup
echo 에뮬레이터를 켭니다. 첫 부팅은 몇 분 걸립니다.
echo 이 창은 에뮬레이터를 쓰는 동안 닫지 마세요.
echo 기본은 소프트웨어 그래픽입니다. 그래픽 카드를 쓰려면 2-start-emulator.bat host 로 실행하세요.
echo.
rem 이 PC(내장 그래픽, 2021년 드라이버)는 host 모드에서 화면 서비스가 멈춰 시스템이 계속 재시작한다.
rem 그래서 기본값을 swangle(소프트웨어)로 둔다. 에뮬레이터 37에는 swiftshader_indirect 가 없다.
set "GPU=swangle"
if /i "%~1"=="host" set "GPU=host"
"%ANDROID_HOME%\emulator\emulator.exe" -avd %DDAK_AVD% -gpu %GPU% -no-snapshot -no-metrics
echo.
echo 에뮬레이터가 종료되었습니다.
pause
exit /b 0

:nosetup
echo 먼저 1-setup.bat 을 실행해 주세요.
pause
exit /b 1
