@echo off
rem PaqRap: inicia el sistema (API + visualizador) en segundo plano y abre el navegador.
setlocal
title PaqRap - Iniciar
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\windows\iniciar.ps1" %*
if errorlevel 1 (
  echo.
  echo Ocurrio un problema. Revise los mensajes anteriores.
  pause
  exit /b 1
)
timeout /t 6 /nobreak >nul 2>&1
exit /b 0
