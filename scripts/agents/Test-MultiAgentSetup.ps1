[CmdletBinding()]
param(
    [string] $CodexPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "CodexCommand.ps1")
$codex = Resolve-CodexCommand -Explicit $CodexPath

$repositoryRoot = (& git -C $PSScriptRoot rev-parse --show-toplevel 2>$null | Select-Object -First 1).Trim()
if (-not $repositoryRoot) {
    throw "scripts/agents must be run from inside a Git repository."
}

$requiredAgents = @(
    "project-architect.toml",
    "implementer.toml",
    "test-engineer.toml",
    "code-reviewer.toml",
    "database-reviewer.toml",
    "localization-reviewer.toml"
)
$agentRoot = Join-Path $repositoryRoot ".codex/agents"
foreach ($agent in $requiredAgents) {
    $path = Join-Path $agentRoot $agent
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing custom agent: $path"
    }
    $content = Get-Content -Raw -LiteralPath $path
    foreach ($requiredKey in @("name", "description", "developer_instructions")) {
        if ($content -notmatch "(?m)^$requiredKey\s*=") {
            throw "$agent is missing required key: $requiredKey"
        }
    }
    if ($content -notmatch '(?s)developer_instructions\s*=\s*"""\s*\S.*?"""\s*$') {
        throw "$agent has an invalid or empty developer_instructions multiline value."
    }
}

$scripts = Get-ChildItem -LiteralPath $PSScriptRoot -Filter *.ps1 -File
foreach ($script in $scripts) {
    $tokens = $null
    $errors = $null
    [Management.Automation.Language.Parser]::ParseFile($script.FullName, [ref] $tokens, [ref] $errors) | Out-Null
    if ($errors.Count -gt 0) {
        $messages = ($errors | ForEach-Object Message) -join "; "
        throw "PowerShell syntax error in $($script.Name): $messages"
    }
}

$schemaPath = Join-Path $PSScriptRoot "review-schema.json"
$reviewSchema = Get-Content -Raw -LiteralPath $schemaPath | ConvertFrom-Json
if ($reviewSchema.type -ne "object" -or $reviewSchema.additionalProperties -ne $false) {
    throw "Review schema must be a closed JSON object schema."
}
foreach ($requiredProperty in @("verdict", "summary", "findings")) {
    if ($requiredProperty -notin @($reviewSchema.required)) {
        throw "Review schema does not require: $requiredProperty"
    }
}

$projectConfig = Get-Content -Raw -LiteralPath (Join-Path $repositoryRoot ".codex/config.toml")
foreach ($requiredSetting in @("[agents]", "enabled = true", "max_concurrent_threads_per_session")) {
    if (-not $projectConfig.Contains($requiredSetting)) {
        throw ".codex/config.toml is missing: $requiredSetting"
    }
}

& $codex --version *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Codex CLI is not runnable from this shell: $codex"
}

# This renders local session context without calling a model, so it proves the CLI runs and can
# read this repository. It does NOT prove the custom agents were registered: the rendered prompt
# never names them, so only a real run can show that.
& $codex -C $repositoryRoot debug prompt-input "Multi-Agent configuration preflight" *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Codex could not load the repository context."
}

& (Join-Path $PSScriptRoot "Invoke-MultiAgent.ps1") -Task "setup smoke test" -DryRun -CodexPath $codex | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "The Multi-Agent runner dry-run failed."
}

Write-Host "Multi-Agent configuration preflight passed using $codex."
Write-Host "Maven, the custom agents and the review gate are exercised only by a full run."
