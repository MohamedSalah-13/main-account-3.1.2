@echo off
REM Run all .sql files in scripts\main against a MySQL database
REM Usage: run_all_sql.bat localhost 3306 root secret mydb

setlocal enabledelayedexpansion

set "HOST=%~1"
if "%HOST%"=="" set "HOST=localhost"

set "PORT=%~2"
if "%PORT%"=="" set "PORT=3306"

set "USER=%~3"
if "%USER%"=="" set "USER=root"

set "PASS=%~4"
if "%PASS%"=="" set "PASS=m13ido"

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
REM Note: passing password on CLI; consider MYSQL_PWD env var if preferred.
"%MYSQL_BIN%" --host=%HOST% --port=%PORT% --user=%USER% --default-character-set=utf8mb4 --password=%PASS% -vvv %DB% < "%FILE%"
exit /b %errorlevel%
