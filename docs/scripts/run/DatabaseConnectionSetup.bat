@echo off
setlocal enabledelayedexpansion

REM ===========================
REM إعداد الاتصال (استخدم قيمك)
REM ===========================
set "DB_HOST=<DB_HOST>"
set "DB_PORT=3306"
set "DB_NAME=<DB_NAME>"
set "DB_USER=<DB_USER>"
set "DB_PASS=<DB_PASSWORD>"

REM ===========================
REM خيارات التشغيل
REM ===========================
set "DIR=main"       REM المجلد الجذري الذي يحتوي ملفات .sql
set "DRY_RUN=0"      REM 1 للتشغيل التجريبي بدون تنفيذ
set "TX_PER_FILE=0"  REM 1 لتنفيذ كل ملف داخل معاملة START TRANSACTION/COMMIT

REM ===========================
REM تحقق من mysql
REM ===========================
where mysql >nul 2>nul
if errorlevel 1 (
  echo لم يتم العثور على mysql في PATH
  exit /b 1
)

REM ===========================
REM تحقق من وجود المجلد
REM ===========================
if not exist "%DIR%" (
  echo المجلد "%DIR%" غير موجود
  exit /b 1
)

REM ===========================
REM تجهيز ملف السجل
REM ===========================
if not exist "logs" mkdir "logs"
for /f "usebackq delims=" %%t in (`powershell -NoProfile -Command "(Get-Date).ToString('yyyyMMdd_HHmmss')"`) do set "TS=%%t"
if not defined TS set "TS=%DATE: =0%_%TIME::=_%" & set "TS=%TS:/=-%" & set "TS=%TS::=-%" & set "TS=%TS:,=-%" & set "TS=%TS:.=-%"
set "LOG=logs\run_%TS%.log"

echo بدء التنفيذ. المجلد: %DIR% ^| DryRun=%DRY_RUN% ^| TxPerFile=%TX_PER_FILE%
>> "%LOG%" echo بدء التنفيذ. المجلد: %DIR% ^| DryRun=%DRY_RUN% ^| TxPerFile=%TX_PER_FILE%

REM ===========================
REM التحقق من وجود ملفات SQL递ي
REM ===========================
where /r "%DIR%" *.sql >nul 2>nul
if errorlevel 1 (
  echo لا توجد ملفات .sql تحت "%DIR%"
  >> "%LOG%" echo لا توجد ملفات .sql تحت "%DIR%"
  exit /b 0
)

REM ===========================
REM تنفيذ الملفات بترتيب أبجدي كامل المسار
REM ===========================
set "EC=0"
for /f "usebackq delims=" %%F in (`dir /b /s /a-d "%DIR%\*.sql" ^| sort`) do (
  echo ==> تنفيذ: "%%~fF"
  >> "%LOG%" echo ==> تنفيذ: "%%~fF"

  if "%DRY_RUN%"=="1" (
    echo     (تجريبي) لن يتم التنفيذ
    >> "%LOG%" echo     (تجريبي) لن يتم التنفيذ
  ) else (
    if "%TX_PER_FILE%"=="1" (
      set "TMP=%TEMP%\tx_%%~nF_!RANDOM!.sql"
      (echo START TRANSACTION;) > "!TMP!"
      type "%%~fF" >> "!TMP!"
      (echo COMMIT;) >> "!TMP!"

      REM ملاحظة: إن واجهت مشاكل مع محارف كلمة السر الخاصة، استخدم MYSQL_PWD (انظر الملاحظات أدناه)
      mysql --protocol=TCP -h "%DB_HOST%" -P %DB_PORT% -u "%DB_USER%" -p%DB_PASS% "%DB_NAME%" --default-character-set=utf8mb4 --comments < "!TMP!" >> "%LOG%" 2>&1
      set "EC=!ERRORLEVEL!"
      del /q "!TMP!" >nul 2>&1
    ) else (
      mysql --protocol=TCP -h "%DB_HOST%" -P %DB_PORT% -u "%DB_USER%" -p%DB_PASS% "%DB_NAME%" --default-character-set=utf8mb4 --comments < "%%~fF" >> "%LOG%" 2>&1
      set "EC=!ERRORLEVEL!"
    )
    if not "!EC!"=="0" (
      echo فشل تنفيذ الملف: "%%~fF" (code=!EC!). تم الإيقاف.
      >> "%LOG%" echo فشل تنفيذ الملف: "%%~fF" (code=!EC!). تم الإيقاف.
      exit /b !EC!
    )
  )
)

echo تم التنفيذ بنجاح. السجل: %LOG%
>> "%LOG%" echo تم التنفيذ بنجاح.
endlocal