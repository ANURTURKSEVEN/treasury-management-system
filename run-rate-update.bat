@echo off
REM =====================================================================
REM  Kur Guncelleme Batch'i - TCMB'den doviz + efektif kurlarini ceker.
REM  Windows Gorev Zamanlayici her is gunu 15:30'da calistirir.
REM =====================================================================
setlocal

set PROJ=C:\Users\gtstaj0079\treasury-management-system
set JAVA=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot\bin\java.exe
set M2=C:\Users\gtstaj0079\.m2\repository

set CP=%PROJ%\target\classes
set CP=%CP%;%M2%\com\mysql\mysql-connector-j\8.4.0\mysql-connector-j-8.4.0.jar

if not exist "%PROJ%\logs" mkdir "%PROJ%\logs"

echo. >> "%PROJ%\logs\rate-update.log"
echo ===== %date% %time% ===== >> "%PROJ%\logs\rate-update.log"

"%JAVA%" -Dfile.encoding=UTF-8 -cp "%CP%" com.gtech.treasury.batch.RateUpdateJob >> "%PROJ%\logs\rate-update.log" 2>&1

endlocal
