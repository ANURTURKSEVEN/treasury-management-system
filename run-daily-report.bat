@echo off
REM =====================================================================
REM  Gunluk Rapor Batch'i - o gunun tum islemlerini e-posta ile gonderir.
REM  Windows Gorev Zamanlayici bu dosyayi her gun 17:00'de calistirir.
REM =====================================================================
setlocal

set PROJ=C:\Users\gtstaj0079\treasury-management-system
set JAVA=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot\bin\java.exe
set M2=C:\Users\gtstaj0079\.m2\repository

set CP=%PROJ%\target\classes
set CP=%CP%;%M2%\com\mysql\mysql-connector-j\8.4.0\mysql-connector-j-8.4.0.jar
set CP=%CP%;%M2%\com\sun\mail\javax.mail\1.6.2\javax.mail-1.6.2.jar
set CP=%CP%;%M2%\com\sun\activation\javax.activation\1.2.0\javax.activation-1.2.0.jar

if not exist "%PROJ%\logs" mkdir "%PROJ%\logs"

echo. >> "%PROJ%\logs\daily-report.log"
echo ===== %date% %time% ===== >> "%PROJ%\logs\daily-report.log"

"%JAVA%" -Dfile.encoding=UTF-8 -cp "%CP%" com.gtech.treasury.batch.DailyReportJob >> "%PROJ%\logs\daily-report.log" 2>&1

endlocal
