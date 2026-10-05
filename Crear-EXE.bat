@echo off
cd /d "%~dp0"
.venv\Scripts\python.exe -m PyInstaller --noconfirm --clean --onedir --windowed --name LuzCam --collect-all pyvirtualcam app.py
if errorlevel 1 goto error
echo Resultado: dist\LuzCam\LuzCam.exe. Copia TODA la carpeta LuzCam.
pause
exit /b 0
:error
echo La compilacion fallo. Ejecuta Preparar.bat primero.
pause
exit /b 1
