param(
    [Parameter(Mandatory = $true)]
    [string]$PrivateKeyPath,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [Parameter(Mandatory = $false)]
    [string]$MachineGuid,
    [Parameter(Mandatory = $false)]
    [string]$OpenSslPath,
    [Parameter(Mandatory = $false)]
    [switch]$Verify
)

$ErrorActionPreference = 'Stop'

function Get-MachineGuid {
    $out = & reg query "HKLM\SOFTWARE\Microsoft\Cryptography" /v MachineGuid 2>$null
    if (-not $out) { return $null }
    foreach ($line in $out) {
        $trim = $line.Trim()
        if ($trim -match '^MachineGuid\s+REG_SZ\s+(.+)$') {
            return $Matches[1].Trim()
        }
    }
    return $null
}

if (-not $MachineGuid) {
    $MachineGuid = Get-MachineGuid
}

if (-not $MachineGuid) {
    throw "MachineGuid not found. Pass -MachineGuid manually."
}

if (-not (Test-Path -Path $PrivateKeyPath)) {
    throw "Private key not found: $PrivateKeyPath"
}

$payload = "HAMZA_ACCOUNT|$MachineGuid"
$payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
$payloadB64 = [Convert]::ToBase64String($payloadBytes)

$tmpPayload = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllBytes($tmpPayload, $payloadBytes)
$tmpSig = [System.IO.Path]::GetTempFileName()
$tmpPub = [System.IO.Path]::GetTempFileName()

try {
$openssl = if ($OpenSslPath) { $OpenSslPath } else { "openssl" }
& $openssl dgst -sha256 -sign $PrivateKeyPath -out $tmpSig $tmpPayload
$sigBytes = [System.IO.File]::ReadAllBytes($tmpSig)
$sigB64 = [Convert]::ToBase64String($sigBytes)

if ($Verify) {
    & $openssl pkey -in $PrivateKeyPath -pubout -out $tmpPub
    & $openssl dgst -sha256 -verify $tmpPub -signature $tmpSig $tmpPayload | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Signature verification failed. Check key pair or payload."
    }
}
} finally {
    Remove-Item -Force $tmpPayload -ErrorAction SilentlyContinue
    Remove-Item -Force $tmpSig -ErrorAction SilentlyContinue
    Remove-Item -Force $tmpPub -ErrorAction SilentlyContinue
}

if (-not $sigB64) {
    throw "Signing failed. Ensure openssl is installed and working."
}

$license = "$payloadB64.$sigB64"
[System.IO.File]::WriteAllText($OutputPath, $license, [System.Text.Encoding]::ASCII)

Write-Output "License created at: $OutputPath"
