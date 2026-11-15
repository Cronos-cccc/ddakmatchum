@echo off
chcp 65001 >nul
title DdakMatchum 3 - build and install
if not exist "%~dp0scripts\env.bat" goto nosetup
call "%~dp0scripts\env.bat"
set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"

echo [1/3] 앱 빌드 중입니다. 처음에는 5분에서 10분 걸립니다.
cd /d "%~dp0vehicle-app"
call "%~dp0vehicle-app\gradlew.bat" assembleDebug
if errorlevel 1 goto buildfail
rem 빌드 프로그램을 내려 메모리를 비운다 (에뮬레이터가 메모리와 CPU를 많이 쓴다)
call "%~dp0vehicle-app\gradlew.bat" --stop >nul 2>nul

echo.
echo [2/3] 에뮬레이터 확인
"%ADB%" devices
"%ADB%" get-state >nul 2>nul
if errorlevel 1 goto nodevice

echo [3/3] 앱 설치와 실행
"%ADB%" install -r "app\build\outputs\apk\debug\app-debug.apk"
if errorlevel 1 goto installfail
rem Pleos 에뮬레이터의 현재 사용자는 10번이다
"%ADB%" shell pm grant --user 10 com.ddakmatchum.car android.permission.RECORD_AUDIO >nul 2>nul
"%ADB%" shell pm grant --user 10 com.ddakmatchum.car android.car.permission.CAR_SPEED >nul 2>nul
"%ADB%" shell pm grant --user 10 com.ddakmatchum.car android.permission.POST_NOTIFICATIONS >nul 2>nul
"%ADB%" shell appops set --user 10 com.ddakmatchum.car SYSTEM_ALERT_WINDOW allow >nul 2>nul
"%ADB%" shell am start --user 10 -n com.ddakmatchum.car/.MainActivity
echo.
echo 완료. 에뮬레이터 화면에 딱맞춤 앱이 떴는지 확인해 주세요.
pause
exit /b 0

:nosetup
echo 먼저 1-setup.bat 을 실행해 주세요.
pause
exit /b 1

:buildfail
echo.
echo 빌드에 실패했습니다. 위 오류 내용을 복사해서 알려 주세요.
pause
exit /b 1

:nodevice
echo.
echo 켜져 있는 에뮬레이터가 없습니다. 2-start-emulator.bat 으로 에뮬레이터를 켜고
echo 홈 화면이 뜬 다음 이 파일을 다시 실행해 주세요. 빌드는 이미 끝났습니다.
pause
exit /b 1

:installfail
echo.
echo 설치에 실패했습니다. 위 오류 내용을 복사해서 알려 주세요.
pause
exit /b 1
