@echo off
rem PaqRap: detiene el sistema iniciado con INICIAR.bat.
setlocal
title PaqRap - Detener
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\windows\detener.ps1" %*
if errorlevel 1 (
  echo.
  echo Ocurrio un problema. Revise los mensajes anteriores.
  pause
  exit /b 1
)
timeout /t 4 /nobreak >nul 2>&1
exit /b 0
