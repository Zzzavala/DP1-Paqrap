@echo off
rem PaqRap: recompila visualizador y backend y deja dist\paqrap-app.jar (solo si se cambio el codigo).
setlocal
title PaqRap - Compilar
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\windows\compilar.ps1" %*
if errorlevel 1 (
  echo.
  echo Ocurrio un problema. Revise los mensajes anteriores.
  pause
  exit /b 1
)
echo.
pause
exit /b 0
