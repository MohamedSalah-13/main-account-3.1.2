:: upload-zip-to-gdrive.bat
@echo off
setlocal

:: Configure if needed:
set "RCLONE_EXE=rclone"  :: or set to "C:\Program Files\rclone\rclone.exe"
set "REMOTE_NAME=gdrive"
set "REMOTE_BASE=/Backups"

if "%~1"=="" (
  echo Usage: %~nx0 "C:\path\to\your.zip" [remoteSubfolder]
  exit /b 1
)

set "ZIP_PATH=%~1"
set "REMOTE_SUB=%~2"

if not exist "%ZIP_PATH%" (
  echo File not found: "%ZIP_PATH%"
  exit /b 2
)

:: If no subfolder provided, use current folder name
for %%I in ("%CD%") do set "CURRENT_FOLDER=%%~nxI"
if "%REMOTE_SUB%"=="" set "REMOTE_SUB=%CURRENT_FOLDER%"

:: Timestamped filename on Drive
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "TS=%%t"

set "REMOTE_DIR=%REMOTE_NAME%:%REMOTE_BASE%/%REMOTE_SUB%"
set "REMOTE_FILE=%REMOTE_DIR%/%REMOTE_SUB%_%TS%.zip"

echo Uploading "%ZIP_PATH%" to "%REMOTE_FILE%" ...

"%RCLONE_EXE%" --version >nul 2>nul
if errorlevel 1 (
  echo rclone not found or failed to run. Please install or set RCLONE_EXE.
  exit /b 3
)

"%RCLONE_EXE%" mkdir "%REMOTE_DIR%" 1>nul 2>nul
"%RCLONE_EXE%" copyto "%ZIP_PATH%" "%REMOTE_FILE%" --progress --retries 5 --low-level-retries 10
if errorlevel 1 (
  echo Upload failed.
  exit /b 4
)

echo Upload complete: %REMOTE_FILE%
endlocal
exit /b 0
