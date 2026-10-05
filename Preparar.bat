@echo off
cd /d "%~dp0"
py -3.11 -m venv .venv
if errorlevel 1 goto error
.venv\Scripts\python.exe -m pip install -r requirements.txt
if errorlevel 1 goto error
echo Listo. Ahora abre Iniciar.bat
pause
exit /b 0
:error
echo No se pudo preparar. Instala Python 3.11 de 64 bits y revisa tu conexion.
pause
exit /b 1
