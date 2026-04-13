@echo off
setlocal enabledelayedexpansion

rem ========== USER SETTINGS ==========
set "DB_HOST=localhost"
set "DB_PORT=3306"
set "DB_NAME=account_system_db"
set "DB_USER=root"
set "DB_PASS=<YOUR_DB_PASSWORD>"  rem Consider using --defaults-extra-file instead of putting password here

rem If mysqldump.exe is not on PATH, point MYSQL_BIN to its folder (no trailing slash)
rem Example: C:\Program Files\MySQL\MySQL Server 8.0\bin
rem set "MYSQL_BIN=mysql"

rem set "BACKUP_DIR=%~dp0backup_data"
set "BACKUP_DIR=C:\Backups\mysql"
set "RETENTION_DAYS=7"

rem Gmail SMTP settings
set "GMAIL_USER=m13ido@gmail.com"
set "GMAIL_APP_PASS=xuow dygu ryvy ymsn"
set "MAIL_TO=m13ido@gmail.com"
set "MAIL_SUBJECT=MySQL backup: %DB_NAME%"

rem ========== END USER SETTINGS ==========

rem Make backup directory
if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

rem ISO timestamp
for /f %%I in ('powershell -NoProfile -Command "(Get-Date).ToString(\"yyyyMMdd_HHmmss\")"') do set "TS=%%I"

set "DUMP_FILE=%BACKUP_DIR%\%DB_NAME%_%TS%.sql"
set "ZIP_FILE=%BACKUP_DIR%\%DB_NAME%_%TS%.zip"
set "LOG_FILE=%BACKUP_DIR%\backup_%DB_NAME%_%TS%.log"

echo === MySQL backup started %date% %time% === > "%LOG_FILE%"

rem Resolve mysqldump path
set "MYSQLDUMP=mysqldump.exe"
if exist "mysqldump.exe" set "MYSQLDUMP=%MYSQL_BIN%\mysqldump.exe"

rem Optional: safer auth using a my.cnf file instead of exposing password:
rem Create a file like C:\secure\my_backup.cnf with:
rem [client]
rem user=<YOUR_DB_USER>
rem password=<YOUR_DB_PASSWORD>
rem host=<DB_HOST>
rem port=<DB_PORT>
rem Then replace the auth flags below with: --defaults-extra-file=C:\secure\my_backup.cnf

rem Path to your secure MySQL option file
set "DEFAULTS_FILE=C:\secure\my_backup.ini"
rem set "DEFAULTS_FILE=%~dp0secure\my_backup.ini"

rem Validate defaults file before running mysqldump
if not exist "%DEFAULTS_FILE%" (
  echo ERROR: Defaults file not found: "%DEFAULTS_FILE%"
  exit /b 1
)

rem Ensure --defaults-extra-file is the first option after the executable
echo Dumping database "%DB_NAME%"... >> "%LOG_FILE%"
"%MYSQLDUMP%" ^
  --defaults-extra-file="%DEFAULTS_FILE%" ^
  --routines --events --single-transaction ^
  --databases %DB_NAME% ^
  --default-character-set=utf8mb4 ^
  --result-file="%DUMP_FILE%" >> "%LOG_FILE%" 2>&1


if errorlevel 1 (
  echo ERROR: mysqldump failed. See log: "%LOG_FILE%"
  exit /b 1
)

rem Compress the dump
echo Compressing dump... >> "%LOG_FILE%"
powershell -NoProfile -Command "Compress-Archive -Path '%DUMP_FILE%' -DestinationPath '%ZIP_FILE%' -Force" >> "%LOG_FILE%" 2>&1
if errorlevel 1 (
  echo ERROR: compression failed. See log: "%LOG_FILE%"
  exit /b 1
)

rem Remove raw .sql after zipping (optional)
del /q "%DUMP_FILE%" >nul 2>&1

rem Send email with attachment using Gmail SMTP (requires App Password)
rem echo Sending email... >> "%LOG_FILE%"

rem powershell -NoProfile -Command ^
rem  "$user='%GMAIL_USER%';" ^
rem  "$pass=ConvertTo-SecureString '%GMAIL_APP_PASS%' -AsPlainText -Force;" ^
rem  "$cred=New-Object System.Management.Automation.PSCredential($user,$pass);" ^
rem  "Send-MailMessage -From $user -To '%MAIL_TO%' -Subject '%MAIL_SUBJECT% (%TS%)' -Body 'MySQL backup attached: %DB_NAME% (%TS%).' -SmtpServer 'smtp.gmail.com' -Port 587 -UseSsl -Credential $cred -Attachments '%ZIP_FILE%'" ^
rem  >> "%LOG_FILE%" 2>&1

rem if errorlevel 1 (
rem  echo ERROR: email send failed. See log: "%LOG_FILE%"
rem  exit /b 1
rem )

rem Purge old backups
if not "%RETENTION_DAYS%"=="" (
  echo Deleting backups older than %RETENTION_DAYS% days... >> "%LOG_FILE%"
  forfiles /p "%BACKUP_DIR%" /m *.zip /d -%RETENTION_DAYS% /c "cmd /c del /q @path" >> "%LOG_FILE%" 2>&1
)

echo Done. Backup file: %ZIP_FILE%
echo === Finished %date% %time% === >> "%LOG_FILE%"

endlocal
exit /b 0
