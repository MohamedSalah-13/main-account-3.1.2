[CmdletBinding(SupportsShouldProcess, ConfirmImpact = "High")]
param(
    [Parameter(Mandatory)]
    [ValidatePattern("^[0-9]{8}-[0-9]{6}-[a-f0-9]{6}-[\p{L}\p{N}-]+$")]
    [string] $RunId
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = [IO.Path]::GetFullPath((
    & git -C $PSScriptRoot rev-parse --show-toplevel 2>$null | Select-Object -First 1
).Trim())
$resultPath = Join-Path (Join-Path (Join-Path $repositoryRoot ".agent-runs") $RunId) "result.json"
$result = $null
if (Test-Path -LiteralPath $resultPath -PathType Leaf) {
    $result = Get-Content -Raw -LiteralPath $resultPath | ConvertFrom-Json
}

$repositoryParent = Split-Path -Parent $repositoryRoot
$repositoryName = Split-Path -Leaf $repositoryRoot
$worktreeRoot = [IO.Path]::GetFullPath((Join-Path (Join-Path $repositoryParent ".agent-worktrees") $repositoryName)).TrimEnd([IO.Path]::DirectorySeparatorChar) +
    [IO.Path]::DirectorySeparatorChar
if (-not (Test-Path -LiteralPath $worktreeRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) -PathType Container)) {
    throw "Worktree root does not exist: $worktreeRoot"
}
$worktreeRootItem = Get-Item -LiteralPath $worktreeRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) -Force
if (($worktreeRootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw "Refusing to use a worktree root that is a reparse point: $worktreeRoot"
}
$expectedWorktreePath = [IO.Path]::GetFullPath((Join-Path $worktreeRoot $RunId))
if ($result) {
    if ([string] $result.runId -ne $RunId) {
        throw "Run result identity does not match the requested RunId."
    }
    $recordedWorktreePath = [IO.Path]::GetFullPath([string] $result.worktree)
    if (-not $recordedWorktreePath.Equals($expectedWorktreePath, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Run result points to a different worktree than .agent-worktrees/$repositoryName/$RunId."
    }
}
$worktreePath = $expectedWorktreePath
if (-not $worktreePath.StartsWith($worktreeRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to remove a worktree outside $worktreeRoot"
}
if (-not (Test-Path -LiteralPath $worktreePath -PathType Container)) {
    throw "Worktree directory does not exist: $worktreePath"
}
$worktreeItem = Get-Item -LiteralPath $worktreePath -Force
if (($worktreeItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw "Refusing to remove a worktree path that is a reparse point: $worktreePath"
}

$registeredWorktrees = @(& git -C $repositoryRoot worktree list --porcelain | Where-Object {
    $_.StartsWith("worktree ")
} | ForEach-Object {
    [IO.Path]::GetFullPath($_.Substring(9))
})
if ($LASTEXITCODE -ne 0 -or -not ($registeredWorktrees | Where-Object {
    $_.Equals($worktreePath, [StringComparison]::OrdinalIgnoreCase)
})) {
    throw "The expected path is not a registered Git worktree: $worktreePath"
}

$changes = & git -C $worktreePath status --porcelain --ignored
if ($LASTEXITCODE -ne 0) {
    throw "Could not inspect worktree status."
}
if ($changes) {
    $unsafeChanges = @($changes | Where-Object {
        $_ -notmatch '^!! (?:[^/]+/)*target/$'
    })
    if ($unsafeChanges.Count -gt 0) {
        throw "Worktree has tracked, untracked, or local ignored files. Commit or preserve them before cleanup."
    }
}

if ($PSCmdlet.ShouldProcess($worktreePath, "Remove the clean Git worktree (the branch is preserved)")) {
    & git -C $repositoryRoot worktree remove $worktreePath
    if ($LASTEXITCODE -ne 0) {
        throw "Git could not remove the worktree."
    }
    if ($result) {
        $result | Add-Member -NotePropertyName worktreeRemovedAt -NotePropertyValue (Get-Date).ToString("o") -Force
        $result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $resultPath -Encoding utf8
        Write-Host "Removed worktree. Branch preserved: $($result.branch)"
    } else {
        $runMatch = [regex]::Match($RunId, '^(?<timestamp>[0-9]{8}-[0-9]{6})-(?<nonce>[a-f0-9]{6})-(?<slug>[\p{L}\p{N}-]+)$')
        $branch = "agents/$($runMatch.Groups['slug'].Value)-$($runMatch.Groups['timestamp'].Value)-$($runMatch.Groups['nonce'].Value)"
        Write-Host "Removed unrecorded worktree. Expected branch preserved: $branch"
    }
}
