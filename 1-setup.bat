@echo off
chcp 65001 >nul
title DdakMatchum 1 - setup
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\setup-windows.ps1" %*
echo.
pause
