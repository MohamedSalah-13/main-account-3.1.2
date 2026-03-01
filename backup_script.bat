@echo off
setlocal

echo Current folder path: %CD%
for %%I in (%CD%) do set "CURRENT_FOLDER=%%~nxI"
echo Current folder name: %CURRENT_FOLDER%

:: Timestamped filename on Drive
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "TS=%%t"

set "SOURCE_DIR=."
set "BASE_DEST=I:\java\backup-projects\accounts-backup"
set "DESTINATION_DIR=%BASE_DEST%/%CURRENT_FOLDER%_%TS%"
set "SEVEN_ZIP_PATH=C:\Program Files\7-Zip\7z.exe"

robocopy "%SOURCE_DIR%" "%DESTINATION_DIR%" /E /NFL /NDL /NJH /NJS /NC /NS /NP ^
  /XD ".git" ".idea" "target" "log" "logs" "out" "backup_data" "Walls" ^
  /XF *.log


echo Copied to: "%DESTINATION_DIR%"
echo Copy completed successfully!

"%SEVEN_ZIP_PATH%" a "%DESTINATION_DIR%.zip" "%DESTINATION_DIR%"
echo Backup folder zipped successfully!

rmdir /s /q "%DESTINATION_DIR%"
echo Backup folder removed successfully!

endlocal