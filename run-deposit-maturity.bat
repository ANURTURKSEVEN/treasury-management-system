@echo off
REM =====================================================================
REM  Vadeli Mevduat Vade Sonu Batch'i - vadesi dolan mevduatlari kapatir.
REM  Windows Gorev Zamanlayici her gun calistirir.
REM =====================================================================
setlocal

set PROJ=C:\Users\gtstaj0079\treasury-management-system
set JAVA=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot\bin\java.exe
set M2=C:\Users\gtstaj0079\.m2\repository

set CP=%PROJ%\target\classes
set CP=%CP%;%M2%\com\mysql\mysql-connector-j\8.4.0\mysql-connector-j-8.4.0.jar

if not exist "%PROJ%\logs" mkdir "%PROJ%\logs"
echo. >> "%PROJ%\logs\deposit-maturity.log"
echo ===== %date% %time% ===== >> "%PROJ%\logs\deposit-maturity.log"

"%JAVA%" -Dfile.encoding=UTF-8 -cp "%CP%" com.gtech.treasury.batch.DepositMaturityJob >> "%PROJ%\logs\deposit-maturity.log" 2>&1

endlocal
