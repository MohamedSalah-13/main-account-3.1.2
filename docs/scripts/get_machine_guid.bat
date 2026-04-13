@echo off
setlocal
set "OUT=machine_guid.txt"

for /f "tokens=1,2,*" %%A in ('reg query "HKLM\SOFTWARE\Microsoft\Cryptography" /v MachineGuid 2^>nul') do (
    if /i "%%A"=="MachineGuid" (
        set "GUID=%%C"
    )
)

if not defined GUID (
    echo MachineGuid not found.
    exit /b 1
)

echo %GUID%>"%OUT%"
echo MachineGuid saved to %OUT%
endlocal
