$ErrorActionPreference = 'Stop'

$out = & reg query "HKLM\SOFTWARE\Microsoft\Cryptography" /v MachineGuid 2>$null
if (-not $out) {
    throw "MachineGuid not found."
}

foreach ($line in $out) {
    $trim = $line.Trim()
    if ($trim -match '^MachineGuid\s+REG_SZ\s+(.+)$') {
        $guid = $Matches[1].Trim()
        Write-Output $guid
        exit 0
    }
}

throw "MachineGuid not found."
