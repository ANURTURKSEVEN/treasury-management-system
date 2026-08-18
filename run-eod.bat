@echo off
REM =====================================================================
REM  Gun Sonu (EOD) Gecikme Raporu Batch'i
REM  Geciken kredileri Excel'e yazar, personele bildirim + log birakir.
REM  Windows Gorev Zamanlayici her aksam calistirir.
REM =====================================================================
setlocal

set PROJ=C:\Users\gtstaj0079\treasury-management-system
set JAVA=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot\bin\java.exe

REM Calisma dizinini proje kokune al (reports klasoru ve db.properties icin)
cd /d "%PROJ%"

REM Classpath: derlenmis siniflar + kaynaklar + lib klasorundeki tum jar'lar
set CP=%PROJ%\target\classes;%PROJ%\src\main\resources;%PROJ%\lib\*

if not exist "%PROJ%\logs" mkdir "%PROJ%\logs"

echo. >> "%PROJ%\logs\eod.log"
echo ===== %date% %time% ===== >> "%PROJ%\logs\eod.log"

"%JAVA%" -Dfile.encoding=UTF-8 -cp "%CP%" com.gtech.treasury.batch.EodOverdueJob >> "%PROJ%\logs\eod.log" 2>&1

endlocal
