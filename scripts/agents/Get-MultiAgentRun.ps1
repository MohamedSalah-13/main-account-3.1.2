[CmdletBinding()]
param(
    [ValidatePattern("^[0-9]{8}-[0-9]{6}-[a-f0-9]{6}-[\p{L}\p{N}-]+$")]
    [string] $RunId,
    [switch] $AsJson
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = (& git -C $PSScriptRoot rev-parse --show-toplevel 2>$null | Select-Object -First 1).Trim()
if (-not $repositoryRoot) {
    throw "scripts/agents must be run from inside a Git repository."
}
$runRoot = Join-Path $repositoryRoot ".agent-runs"
if (-not (Test-Path -LiteralPath $runRoot -PathType Container)) {
    Write-Host "No Multi-Agent runs have been recorded."
    return
}
$runRootItem = Get-Item -LiteralPath $runRoot -Force
if (($runRootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw "Refusing to read a run log root that is a reparse point: $runRoot"
}

$resultFiles = if ($RunId) {
    $candidate = Join-Path (Join-Path $runRoot $RunId) "result.json"
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw "Run result not found: $RunId"
    }
    @(Get-Item -LiteralPath $candidate)
} else {
    @(Get-ChildItem -LiteralPath $runRoot -Filter result.json -File -Recurse | Sort-Object LastWriteTime -Descending)
}

$results = @($resultFiles | ForEach-Object {
    Get-Content -Raw -LiteralPath $_.FullName | ConvertFrom-Json
})

if ($AsJson) {
    $results | ConvertTo-Json -Depth 6
} else {
    $results | Select-Object runId, status, passed, branch, reviewVerdict, testExitCode, worktree | Format-Table -AutoSize
}
