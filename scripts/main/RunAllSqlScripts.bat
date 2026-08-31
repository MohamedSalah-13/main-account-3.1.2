@echo off
REM Run all .sql files in scripts\main against a MySQL database
REM Usage: set MYSQL_PWD=secret ^&^& RunAllSqlScripts.bat localhost 3306 root "" mydb
REM        RunAllSqlScripts.bat localhost 3306 root secret mydb
REM Prefer MYSQL_PWD: a password given as the 4th argument is visible to anything
REM that can read this machine's command lines or your shell history.

setlocal enabledelayedexpansion

set "HOST=%~1"
if "%HOST%"=="" set "HOST=localhost"

set "PORT=%~2"
if "%PORT%"=="" set "PORT=3306"

set "USER=%~3"
if "%USER%"=="" set "USER=root"

REM No default password. Take the 4th argument if given, otherwise MYSQL_PWD.
set "PASS=%~4"
if "%PASS%"=="" set "PASS=%MYSQL_PWD%"
if "%PASS%"=="" (
  echo Error: no MySQL password supplied.
  echo Set MYSQL_PWD in the environment, or pass it as the 4th argument.
  exit /b 1
)

REM Hand the password to the client through the environment rather than
REM --password= on the command line, which would expose it in the process list.
set "MYSQL_PWD=%PASS%"
set "PASS="

set "DB=%~5"
if "%DB%"=="" set "DB=account_system_db"

REM Path to mysql client (assumed on PATH). Override by setting MYSQL_BIN env var.
if not defined MYSQL_BIN set "MYSQL_BIN=mysql"

REM Compute scripts\main relative to this .bat location
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%") do set "SQL_DIR=%%~fI"

if not exist "%SQL_DIR%" (
  echo Folder not found: "%SQL_DIR%"
  exit /b 1
)

REM ==============================================================
REM --- بداية الجزء الخاص بتنظيف قاعدة البيانات (Triggers & Views) ---
REM ==============================================================

REM 1. حذف جميع الـ Triggers
echo Dropping all existing triggers in %DB% ...
"%MYSQL_BIN%" --host=%HOST% --port=%PORT% --user=%USER% -sN -e "SELECT CONCAT('DROP TRIGGER IF EXISTS `', trigger_name, '`;') FROM information_schema.triggers WHERE trigger_schema = '%DB%';" > temp_drop_triggers.sql
"%MYSQL_BIN%" --host=%HOST% --port=%PORT% --user=%USER% %DB% < temp_drop_triggers.sql
del temp_drop_triggers.sql
echo Triggers dropped successfully.

REM 2. حذف جميع الـ Views
echo Dropping all existing views in %DB% ...
"%MYSQL_BIN%" --host=%HOST% --port=%PORT% --user=%USER% -sN -e "SELECT CONCAT('DROP VIEW IF EXISTS `', table_name, '`;') FROM information_schema.VIEWS WHERE table_schema = '%DB%';" > temp_drop_views.sql
"%MYSQL_BIN%" --host=%HOST% --port=%PORT% --user=%USER% %DB% < temp_drop_views.sql
del temp_drop_views.sql
echo Views dropped successfully.

REM ==============================================================
REM --- نهاية الجزء الخاص بتنظيف قاعدة البيانات ---
REM ==============================================================

pushd "%SQL_DIR%"
for /f "delims=" %%F in ('dir /b /on *.sql') do (
  echo Running %%F ...
  call :run_sql "%%F"
  if errorlevel 1 (
    echo Failed on %%F
    popd
    exit /b 1
  )
)
popd

echo Done.
exit /b 0

:run_sql
set "FILE=%~1"
"%MYSQL_BIN%" --host=%HOST% --port=%PORT% --user=%USER% --default-character-set=utf8mb4 -vvv %DB% < "%FILE%" >> migration_log.txt 2>&1
exit /b %errorlevel%