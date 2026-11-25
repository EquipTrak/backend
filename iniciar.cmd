@echo off
rem Duplo clique para subir o EquipTrack no Docker. Faz o mesmo que o iniciar.ps1.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar.ps1" %*
pause
