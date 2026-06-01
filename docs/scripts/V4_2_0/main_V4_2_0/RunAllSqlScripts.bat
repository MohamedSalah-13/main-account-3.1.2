@echo off
setlocal enabledelayedexpansion

REM ==================================================
REM Run all main SQL migration files in correct order
REM Usage:
REM   RunAllSqlScripts.bat localhost 3306 root password account_system_db
REM ==================================================

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

if not defined MYSQL_BIN set "MYSQL_BIN=mysql"

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%") do set "SQL_DIR=%%~fI"

if not exist "%SQL_DIR%" (
  echo Folder not found: "%SQL_DIR%"
  exit /b 1
)

set "LOG_FILE=%SQL_DIR%\migration_log.txt"

echo ================================================== > "%LOG_FILE%"
echo Migration started at %DATE% %TIME% >> "%LOG_FILE%"
echo Database: %DB% >> "%LOG_FILE%"
echo Folder: %SQL_DIR% >> "%LOG_FILE%"
echo ================================================== >> "%LOG_FILE%"

pushd "%SQL_DIR%"

for /f "delims=" %%F in ('dir /b /on *.sql') do (
  echo Running %%F ...
  echo. >> "%LOG_FILE%"
  echo ================================================== >> "%LOG_FILE%"
  echo Running %%F at %DATE% %TIME% >> "%LOG_FILE%"
  echo ================================================== >> "%LOG_FILE%"

  "%MYSQL_BIN%" ^
    --host=%HOST% ^
    --port=%PORT% ^
    --user=%USER% ^
    --password=%PASS% ^
    --default-character-set=utf8mb4 ^
    --force ^
    -vvv ^
    < "%%F" >> "%LOG_FILE%" 2>&1

  if errorlevel 1 (
    echo Failed on %%F
    echo Failed on %%F >> "%LOG_FILE%"
    popd
    exit /b 1
  )
)

popd

echo Done.
echo Migration finished at %DATE% %TIME% >> "%LOG_FILE%"
exit /b 0