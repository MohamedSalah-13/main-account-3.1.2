:: Define the ZIP_PATH and upload it to Google Drive
set "ZIP_PATH=I:\java\backup-projects\accounts-backup\main-account_1.zip"
call "%~dp0upload_zip_to_g_drive2.bat" "%ZIP_PATH%"
