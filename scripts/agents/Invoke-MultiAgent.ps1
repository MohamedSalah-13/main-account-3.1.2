[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string] $Task,

    [ValidatePattern("^(?!-)[A-Za-z0-9][A-Za-z0-9._/-]*$")]
    [string] $BaseBranch = "main",

    [ValidateRange(1, 12)]
    [int] $MaxAgents = 4,

    [ValidateRange(0, 3)]
    [int] $MaxFixPasses = 1,

    [switch] $DatabaseAcceptance,
    [switch] $ConfirmDisposableDatabase,

    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string] $DatabaseConfigPath,

    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string] $DatabaseConfigKeyPath,

    [switch] $AllowOnlineMaven,
    [switch] $SkipReview,
    [switch] $DryRun,

    [ValidateSet("codex", "claude")]
    [string] $Agent = "codex",

    [string] $AgentPath,

    [ValidateSet("plan", "acceptEdits", "bypassPermissions")]
    [string] $ClaudePermissionMode = "acceptEdits",

    [string] $Model,

    [ValidateSet("none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra")]
    [string] $ReasoningEffort
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "AgentCommand.ps1")

# The trap below is scope-wide, so it also runs for failures raised before the run log exists.
# Without these it died on its own strict-mode error and swallowed the real message.
$runState = $null
$resultPath = $null

function Invoke-Native {
    param(
        [Parameter(Mandatory)] [string] $Command,
        [Parameter(Mandatory)] [string[]] $Arguments,
        [string] $WorkingDirectory,
        [string] $LogPath
    )

    if ($WorkingDirectory) {
        Push-Location -LiteralPath $WorkingDirectory
    }
    # Windows PowerShell 5.1 wraps a native command's stderr in ErrorRecords, so under a Stop
    # preference the first Maven warning or Codex progress line aborts a run that exited zero.
    # The assignment is function-scoped; callers keep their Stop preference.
    $ErrorActionPreference = "Continue"
    try {
        if ($LogPath) {
            & $Command @Arguments 2>&1 | Tee-Object -FilePath $LogPath | Out-Host
        } else {
            & $Command @Arguments | Out-Host
        }
        return $LASTEXITCODE
    } finally {
        if ($WorkingDirectory) {
            Pop-Location
        }
    }
}

function Get-RepositoryRoot {
    $candidate = & git -C $PSScriptRoot rev-parse --show-toplevel 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $candidate) {
        throw "scripts/agents must be run from inside a Git repository."
    }
    return [IO.Path]::GetFullPath(($candidate | Select-Object -First 1).Trim())
}

function Assert-ChildPath {
    param(
        [Parameter(Mandatory)] [string] $Parent,
        [Parameter(Mandatory)] [string] $Child,
        [Parameter(Mandatory)] [string] $Label
    )

    $parentPath = [IO.Path]::GetFullPath($Parent).TrimEnd([IO.Path]::DirectorySeparatorChar) +
        [IO.Path]::DirectorySeparatorChar
    $childPath = [IO.Path]::GetFullPath($Child)
    if (-not $childPath.StartsWith($parentPath, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label resolved outside its allowed root: $childPath"
    }
}

function Assert-NoReparsePointInPath {
    param(
        [Parameter(Mandatory)] [string] $Root,
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $Label
    )

    $rootPath = [IO.Path]::GetFullPath($Root).TrimEnd([IO.Path]::DirectorySeparatorChar)
    $current = [IO.Path]::GetFullPath($Path)
    while (-not $current.Equals($rootPath, [StringComparison]::OrdinalIgnoreCase)) {
        Assert-ChildPath -Parent $rootPath -Child $current -Label $Label
        if (Test-Path -LiteralPath $current) {
            $item = Get-Item -LiteralPath $current -Force
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "$Label contains a reparse point: $current"
            }
        }
        $parent = Split-Path -Parent $current
        if (-not $parent -or $parent.Equals($current, [StringComparison]::OrdinalIgnoreCase)) {
            throw "$Label could not be traced back to its allowed root: $Path"
        }
        $current = $parent
    }
}

function Copy-WorktreeIncludes {
    param(
        [Parameter(Mandatory)] [string] $RepositoryRoot,
        [Parameter(Mandatory)] [string] $WorktreePath
    )

    $includeFile = Join-Path $RepositoryRoot ".worktreeinclude"
    if (-not (Test-Path -LiteralPath $includeFile -PathType Leaf)) {
        return
    }

    foreach ($line in Get-Content -LiteralPath $includeFile) {
        $entry = $line.Trim()
        if (-not $entry -or $entry.StartsWith("#")) {
            continue
        }
        if ($entry.IndexOfAny([char[]] "*?[") -ge 0) {
            throw "The local runner accepts exact .worktreeinclude paths only: $entry"
        }
        $leafName = [IO.Path]::GetFileName($entry).ToLowerInvariant()
        if ($leafName -match '^(?:\.env(?:\..*)?|config\.(?:xml|key)|license(?:\..*)?|private[-_]?key(?:\..*)?|secret(?:[-_]?key)?(?:\..*)?|credentials?(?:\..*)?)$') {
            throw ".worktreeinclude may not copy sensitive configuration or key files: $entry"
        }

        $source = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot $entry))
        $destination = [IO.Path]::GetFullPath((Join-Path $WorktreePath $entry))
        Assert-ChildPath -Parent $RepositoryRoot -Child $source -Label ".worktreeinclude source"
        Assert-ChildPath -Parent $WorktreePath -Child $destination -Label ".worktreeinclude destination"
        Assert-NoReparsePointInPath -Root $RepositoryRoot -Path $source -Label ".worktreeinclude source"
        Assert-NoReparsePointInPath -Root $WorktreePath -Path $destination -Label ".worktreeinclude destination"

        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            continue
        }
        $sourceItem = Get-Item -LiteralPath $source -Force
        if (($sourceItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Refusing to copy a reparse point into the worktree: $entry"
        }

        & git -C $RepositoryRoot check-ignore --quiet -- $entry
        if ($LASTEXITCODE -ne 0) {
            throw ".worktreeinclude may copy ignored files only: $entry"
        }

        $destinationDirectory = Split-Path -Parent $destination
        New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination -Force
        Write-Host "Copied ignored worktree dependency: $entry"
    }
}

function Get-CodexUserSetting {
    param([Parameter(Mandatory)] [string] $Key)

    $codexHome = if ($env:CODEX_HOME) { $env:CODEX_HOME } else { Join-Path $env:USERPROFILE ".codex" }
    $configPath = Join-Path $codexHome "config.toml"
    if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
        return $null
    }

    $pattern = "^\s*" + [regex]::Escape($Key) + "\s*=\s*`"([^`"]+)`"\s*$"
    foreach ($line in Get-Content -LiteralPath $configPath) {
        # Top-level keys only. Stop at the first table header so a [profiles.x] or [projects.y]
        # value is never mistaken for the session default.
        if ($line -match "^\s*\[") {
            break
        }
        if ($line -match $pattern) {
            return $Matches[1]
        }
    }
    return $null
}

function Get-TaskSlug {
    param([Parameter(Mandatory)] [string] $Value)

    # Letters and digits of any script, so an Arabic task keeps a readable branch name.
    # An ASCII-only class erased every Arabic description and named every branch "task".
    # Git ref names accept UTF-8; everything it forbids is punctuation this class drops.
    $slug = ($Value.ToLowerInvariant() -replace "[^\p{L}\p{N}]+", "-").Trim("-")
    if (-not $slug) {
        $slug = "task"
    }
    if ($slug.Length -gt 32) {
        $slug = $slug.Substring(0, 32).TrimEnd("-")
    }
    return $slug
}

function Invoke-CodexSession {
    param(
        [Parameter(Mandatory)] [string] $Prompt,
        [Parameter(Mandatory)] [string] $WorktreePath,
        [Parameter(Mandatory)] [string] $LogPath,
        [Parameter(Mandatory)] [string] $LastMessagePath,
        [Parameter(Mandatory)] [int] $AgentLimit,
        [string[]] $ConfigOverrides = @(),
        [string] $OutputSchemaPath,
        [ValidateSet("read-only", "workspace-write")]
        [string] $Sandbox = "read-only"
    )

    $arguments = @(
        "--ask-for-approval", "never",
        "exec",
        "--ignore-user-config",
        "--cd", $WorktreePath,
        "--sandbox", $Sandbox,
        "--config", "agents.max_concurrent_threads_per_session=$AgentLimit"
    ) + $ConfigOverrides
    if ($OutputSchemaPath) {
        $arguments += @("--output-schema", $OutputSchemaPath)
    }
    $arguments += @("--output-last-message", $LastMessagePath, $Prompt)

    return Invoke-Native -Command $script:AgentCommand -Arguments $arguments -WorkingDirectory $WorktreePath -LogPath $LogPath
}

function Invoke-ClaudeSession {
    param(
        [Parameter(Mandatory)] [string] $Prompt,
        [Parameter(Mandatory)] [string] $WorktreePath,
        [Parameter(Mandatory)] [string] $LogPath,
        [Parameter(Mandatory)] [string] $LastMessagePath,
        [ValidateSet("read-only", "workspace-write")]
        [string] $Sandbox = "read-only"
    )

    # Claude Code has no sandbox flag; the equivalent of read-only is plan mode, which cannot
    # edit files. Write sessions use the mode the operator chose - see -ClaudePermissionMode.
    $permissionMode = if ($Sandbox -eq "read-only") { "plan" } else { $script:ClaudeWriteMode }
    $arguments = @("-p", "--output-format", "text", "--permission-mode", $permissionMode)
    if ($script:AgentModel) {
        $arguments += @("--model", $script:AgentModel)
    }
    if ($script:ClaudeEffort) {
        $arguments += @("--effort", $script:ClaudeEffort)
    }
    $arguments += $Prompt

    Push-Location -LiteralPath $WorktreePath
    # See Invoke-Native: 5.1 turns a native command's stderr into terminating errors.
    $ErrorActionPreference = "Continue"
    try {
        $output = & $script:AgentCommand @arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    $text = (@($output) | ForEach-Object { [string] $_ }) -join [Environment]::NewLine
    $text | Set-Content -LiteralPath $LogPath -Encoding utf8
    # Claude prints its answer instead of writing it to a file, so the transcript is the answer.
    # Downstream code reads the same two files whichever agent produced them.
    $text | Set-Content -LiteralPath $LastMessagePath -Encoding utf8
    Write-Host $text
    return $exitCode
}

function Invoke-AgentSession {
    param(
        [Parameter(Mandatory)] [string] $Prompt,
        [Parameter(Mandatory)] [string] $WorktreePath,
        [Parameter(Mandatory)] [string] $LogDirectory,
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [int] $AgentLimit,
        [string[]] $ConfigOverrides = @(),
        [string] $OutputSchemaPath,
        [ValidateSet("read-only", "workspace-write")]
        [string] $Sandbox = "read-only"
    )

    $logPath = Join-Path $LogDirectory "$Name.log"
    $lastMessagePath = Join-Path $LogDirectory "$Name-final.md"

    if ($script:AgentKind -eq "claude") {
        return Invoke-ClaudeSession -Prompt $Prompt -WorktreePath $WorktreePath -LogPath $logPath `
            -LastMessagePath $lastMessagePath -Sandbox $Sandbox
    }
    return Invoke-CodexSession -Prompt $Prompt -WorktreePath $WorktreePath -LogPath $logPath `
        -LastMessagePath $lastMessagePath -AgentLimit $AgentLimit -ConfigOverrides $ConfigOverrides `
        -OutputSchemaPath $OutputSchemaPath -Sandbox $Sandbox
}

function Get-EmbeddedJson {
    param([Parameter(Mandatory)] [AllowEmptyString()] [string] $Text)

    # An agent that cannot be held to a schema answers in prose around its JSON, or fences it.
    $start = $Text.IndexOf("{")
    $end = $Text.LastIndexOf("}")
    if ($start -lt 0 -or $end -le $start) {
        return $null
    }
    return $Text.Substring($start, $end - $start + 1)
}

function Invoke-MavenGate {
    param(
        [Parameter(Mandatory)] [string] $WorktreePath,
        [Parameter(Mandatory)] [string] $LogDirectory,
        [Parameter(Mandatory)] [bool] $RunDatabaseAcceptance,
        [Parameter(Mandatory)] [bool] $AllowOnline,
        [string] $DatabaseConfigSource,
        [string] $DatabaseConfigKeySource,
        [Parameter(Mandatory)] [string] $Name
    )

    $arguments = if ($RunDatabaseAcceptance) {
        @("-pl", "account", "-am", "clean", "test", "-Daccount.db.acceptance=true")
    } else {
        @("clean", "test")
    }
    if (-not $AllowOnline) {
        $arguments = @("-o") + $arguments
    }
    if (-not $RunDatabaseAcceptance) {
        return Invoke-Native -Command "mvn" -Arguments $arguments -WorkingDirectory $WorktreePath `
            -LogPath (Join-Path $LogDirectory "$Name.log")
    }

    $configDestination = Join-Path $WorktreePath "account/config.xml"
    $keyDestination = Join-Path $WorktreePath "account/config.key"
    $configFingerprint = (Get-FileHash -Algorithm SHA256 -LiteralPath $DatabaseConfigSource).Hash.Substring(0, 32)
    $createdNew = $false
    $databaseMutex = [Threading.Mutex]::new($false, "AccountMultiAgentDb-$configFingerprint", [ref] $createdNew)
    $hasMutex = $false
    try {
        try {
            $hasMutex = $databaseMutex.WaitOne(0)
        } catch [Threading.AbandonedMutexException] {
            $hasMutex = $true
        }
        if (-not $hasMutex) {
            throw "Another acceptance gate is using the same database configuration. Use a unique disposable schema or retry later."
        }

        Copy-Item -LiteralPath $DatabaseConfigSource -Destination $configDestination -Force
        if ($DatabaseConfigKeySource) {
            Copy-Item -LiteralPath $DatabaseConfigKeySource -Destination $keyDestination -Force
        }
        return Invoke-Native -Command "mvn" -Arguments $arguments -WorkingDirectory $WorktreePath `
            -LogPath (Join-Path $LogDirectory "$Name.log")
    } finally {
        if (Test-Path -LiteralPath $configDestination -PathType Leaf) {
            Remove-Item -LiteralPath $configDestination -Force
        }
        if (Test-Path -LiteralPath $keyDestination -PathType Leaf) {
            Remove-Item -LiteralPath $keyDestination -Force
        }
        if ($hasMutex) {
            $databaseMutex.ReleaseMutex()
        }
        $databaseMutex.Dispose()
    }
}

function Assert-HeadUnchanged {
    param(
        [Parameter(Mandatory)] [string] $WorktreePath,
        [Parameter(Mandatory)] [string] $ExpectedCommit
    )

    # Read the exit code before narrowing the stream; see the base-branch note below.
    $currentCommitOutput = @(& git -C $WorktreePath rev-parse HEAD 2>$null)
    $currentCommit = if ($currentCommitOutput.Count -gt 0) { $currentCommitOutput[0].Trim() } else { $null }
    if ($LASTEXITCODE -ne 0 -or $currentCommit -ne $ExpectedCommit) {
        throw "An agent changed HEAD. Commits are forbidden during an automated run; inspect the worktree manually."
    }
}

function Invoke-ReviewGate {
    param(
        [Parameter(Mandatory)] [string] $WorktreePath,
        [Parameter(Mandatory)] [string] $LogDirectory,
        [Parameter(Mandatory)] [string] $SchemaPath,
        [string[]] $ConfigOverrides = @(),
        [Parameter(Mandatory)] [string] $Name
    )

    $reviewPath = Join-Path $LogDirectory "$Name.json"
    $logPath = Join-Path $LogDirectory "$Name.log"
    $prompt = @"
Review every uncommitted change in this worktree as an independent project owner. Read
docs/agent-worktree-rules.md, AGENTS.md, CLAUDE.md, and all applicable plans. Focus on correctness,
regressions, authorization, transactions, SQL/migrations, JavaFX behavior, Arabic/English
localization, and missing meaningful tests. Return verdict=fail when any actionable P0-P2 finding
remains; P3-only findings may pass. Use repository-relative paths. Do not edit files.

Answer with one JSON object and nothing else, matching this schema exactly:
$(Get-Content -Raw -LiteralPath $SchemaPath)
"@

    if ($script:AgentKind -eq "claude") {
        # Claude cannot be held to a schema by the CLI, so the schema goes in the prompt and the
        # JSON is extracted from the answer. The verdict contract below is the same either way.
        $exitCode = Invoke-ClaudeSession -Prompt $prompt -WorktreePath $WorktreePath -LogPath $logPath `
            -LastMessagePath (Join-Path $LogDirectory "$Name-final.md") -Sandbox "read-only"
        $answer = if (Test-Path -LiteralPath $logPath -PathType Leaf) { Get-Content -Raw -LiteralPath $logPath } else { "" }
        $json = Get-EmbeddedJson -Text $answer
        if ($json) {
            $json | Set-Content -LiteralPath $reviewPath -Encoding utf8
        }
    } else {
        # Codex enforces the schema itself, which is stronger than asking for it, so it keeps
        # `exec review --uncommitted --output-schema`.
        $arguments = @(
            "--ask-for-approval", "never",
            "exec",
            "--ignore-user-config",
            "--cd", $WorktreePath,
            "--sandbox", "read-only",
            "review",
            "--uncommitted"
        ) + $ConfigOverrides + @(
            "--output-schema", $SchemaPath,
            "--output-last-message", $reviewPath,
            $prompt
        )
        $exitCode = Invoke-Native -Command $script:AgentCommand -Arguments $arguments -WorkingDirectory $WorktreePath -LogPath $logPath
    }

    if ($exitCode -ne 0 -or -not (Test-Path -LiteralPath $reviewPath -PathType Leaf)) {
        return [pscustomobject]@{ ExitCode = $exitCode; Verdict = "error"; Path = $reviewPath; Summary = "Review did not produce a result." }
    }

    try {
        $review = Get-Content -Raw -LiteralPath $reviewPath | ConvertFrom-Json
        $blockingFindings = @($review.findings | Where-Object { $_.priority -in @("P0", "P1", "P2") })
        $verdict = if ($blockingFindings.Count -gt 0) { "fail" } else { $review.verdict }
        $summary = if ($blockingFindings.Count -gt 0 -and $review.verdict -eq "pass") {
            "Reviewer returned pass but reported $($blockingFindings.Count) blocking finding(s); the gate forced failure. $($review.summary)"
        } else {
            $review.summary
        }
        return [pscustomobject]@{ ExitCode = $exitCode; Verdict = $verdict; Path = $reviewPath; Summary = $summary }
    } catch {
        return [pscustomobject]@{ ExitCode = 1; Verdict = "error"; Path = $reviewPath; Summary = "Review output was not valid JSON." }
    }
}

if ($DatabaseAcceptance -and (-not $ConfirmDisposableDatabase -or -not $DatabaseConfigPath)) {
    throw "Database acceptance requires -ConfirmDisposableDatabase and -DatabaseConfigPath for an isolated disposable MySQL schema."
}
if (-not $DatabaseAcceptance -and ($DatabaseConfigPath -or $DatabaseConfigKeyPath)) {
    throw "Database config paths are accepted only with -DatabaseAcceptance."
}

$repositoryRoot = Get-RepositoryRoot
$script:AgentKind = $Agent
$script:AgentCommand = Resolve-AgentCommand -Agent $Agent -Explicit $AgentPath
$script:ClaudeWriteMode = $ClaudePermissionMode

$configOverrides = @()
if ($Agent -eq "codex") {
    # `--ignore-user-config` keeps a run reproducible, but it also discards the model and the
    # reasoning effort chosen in ~/.codex/config.toml - the first real run silently fell back to a
    # default with reasoning effort "none". So read that choice here and pass it back explicitly:
    # the run stays independent of the rest of the user config, and what it used is recorded.
    $effectiveModel = if ($Model) { $Model } else { Get-CodexUserSetting -Key "model" }
    $effectiveEffort = if ($ReasoningEffort) { $ReasoningEffort } else { Get-CodexUserSetting -Key "model_reasoning_effort" }
    if ($effectiveModel) {
        $configOverrides += @("--config", "model=$effectiveModel")
    }
    if ($effectiveEffort) {
        $configOverrides += @("--config", "model_reasoning_effort=$effectiveEffort")
    }
    if (-not $effectiveModel) {
        Write-Warning "No model resolved from -Model or ~/.codex/config.toml; Codex will pick its own default."
    }
} else {
    # A model id is agent-specific, so nothing is inherited across agents: unless the operator
    # names one, Claude runs on whatever it is configured to use.
    $effectiveModel = $Model
    $effectiveEffort = $ReasoningEffort
    if ($effectiveEffort -and $effectiveEffort -notin @("low", "medium", "high", "xhigh", "max")) {
        Write-Warning "Claude does not accept effort '$effectiveEffort'; running without an effort override."
        $effectiveEffort = $null
    }
}
$script:AgentModel = $effectiveModel
$script:ClaudeEffort = if ($Agent -eq "claude") { $effectiveEffort } else { $null }

$mavenCommand = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mavenCommand) {
    throw "Maven is not available on PATH."
}

# Capture the whole stream before reading $LASTEXITCODE. `Select-Object -First` stops the
# pipeline, which kills git and leaves the exit code at -1 on a perfectly successful command -
# this rejected every existing branch, so no run could start at all.
$baseCommitOutput = @(& git -C $repositoryRoot rev-parse --verify "$BaseBranch^{commit}" 2>$null)
$baseCommit = if ($baseCommitOutput.Count -gt 0) { $baseCommitOutput[0].Trim() } else { $null }
if ($LASTEXITCODE -ne 0 -or -not $baseCommit) {
    throw "Base branch or revision does not exist: $BaseBranch"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$nonce = [guid]::NewGuid().ToString("N").Substring(0, 6)
$slug = Get-TaskSlug -Value $Task
$runId = "$timestamp-$nonce-$slug"
# Neither the branch nor the worktree names a tool: which agent ran is recorded in result.json.
$branchName = "agents/$slug-$timestamp-$nonce"
$repositoryParent = Split-Path -Parent $repositoryRoot
$repositoryName = Split-Path -Leaf $repositoryRoot
$worktreeHostRoot = Join-Path $repositoryParent ".agent-worktrees"
$worktreeRoot = Join-Path $worktreeHostRoot $repositoryName
$worktreePath = Join-Path $worktreeRoot $runId
$runRoot = Join-Path $repositoryRoot ".agent-runs"
$logDirectory = Join-Path $runRoot $runId
$reviewSchema = Join-Path $repositoryRoot "scripts/agents/review-schema.json"
$requiredAgentFiles = @(
    "project-architect.toml",
    "implementer.toml",
    "test-engineer.toml",
    "code-reviewer.toml",
    "database-reviewer.toml",
    "localization-reviewer.toml"
)

Assert-ChildPath -Parent $worktreeRoot -Child $worktreePath -Label "Worktree"
Assert-ChildPath -Parent $repositoryRoot -Child $logDirectory -Label "Run log"

if ($DryRun) {
    [pscustomobject]@{
        RunId = $runId
        BaseBranch = $BaseBranch
        Branch = $branchName
        Worktree = $worktreePath
        Logs = $logDirectory
        MaxAgents = $MaxAgents
        Agent = $Agent
        AgentCommand = $script:AgentCommand
        Model = $effectiveModel
        ReasoningEffort = $effectiveEffort
        DatabaseAcceptance = [bool] $DatabaseAcceptance
        AllowOnlineMaven = [bool] $AllowOnlineMaven
    }
    return
}

foreach ($agentFile in $requiredAgentFiles) {
    & git -C $repositoryRoot cat-file -e "${baseCommit}:.codex/agents/$agentFile" 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Base revision $BaseBranch does not contain the required agent definition: $agentFile. Commit the Multi-Agent system before using this runner."
    }
}

New-Item -ItemType Directory -Path $worktreeRoot -Force | Out-Null
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
Assert-NoReparsePointInPath -Root $repositoryParent -Path $worktreeRoot -Label "Worktree root"
Assert-NoReparsePointInPath -Root $repositoryRoot -Path $logDirectory -Label "Run log"

$resultPath = Join-Path $logDirectory "result.json"
$runState = [ordered]@{
    runId = $runId
    status = "starting"
    task = $Task
    baseBranch = $BaseBranch
    branch = $branchName
    worktree = $worktreePath
    logs = $logDirectory
    passed = $false
    discoveryExitCode = $null
    deliveryExitCode = $null
    testExitCode = $null
    reviewVerdict = "not-run"
    reviewSummary = "The run has not reached the independent review gate."
    completedPass = $null
    agent = $Agent
    agentCommand = $script:AgentCommand
    model = $effectiveModel
    reasoningEffort = $effectiveEffort
    databaseAcceptance = [bool] $DatabaseAcceptance
    allowOnlineMaven = [bool] $AllowOnlineMaven
    changes = @()
    error = $null
    startedAt = (Get-Date).ToString("o")
    finishedAt = $null
}
$runState | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $resultPath -Encoding utf8

trap {
    if ($runState -and $resultPath) {
        $runState["status"] = "failed"
        $runState["passed"] = $false
        $runState["error"] = $_.Exception.Message
        $runState["finishedAt"] = (Get-Date).ToString("o")
        $runState | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $resultPath -Encoding utf8
    }
    [Console]::Error.WriteLine("Multi-Agent run failed: $($_.Exception.Message)")
    exit 1
}

$worktreeExit = Invoke-Native -Command "git" -Arguments @(
    "-C", $repositoryRoot, "worktree", "add", "-b", $branchName, $worktreePath, $BaseBranch
) -LogPath (Join-Path $logDirectory "worktree.log")
if ($worktreeExit -ne 0) {
    throw "Git could not create the worktree. See $logDirectory/worktree.log"
}
$runState["status"] = "running"
$runState | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $resultPath -Encoding utf8

Copy-WorktreeIncludes -RepositoryRoot $repositoryRoot -WorktreePath $worktreePath

foreach ($agentFile in $requiredAgentFiles) {
    $agentPath = Join-Path $worktreePath ".codex/agents/$agentFile"
    if (-not (Test-Path -LiteralPath $agentPath -PathType Leaf)) {
        throw "Base revision $BaseBranch does not contain the required agent definition: $agentFile. Commit the Multi-Agent system before using this runner."
    }
}

$databaseInstruction = if ($DatabaseAcceptance) {
    "A disposable isolated MySQL schema has been explicitly confirmed. Do not run database acceptance inside an agent; the independent outer gate will install its configuration temporarily and run it serially."
} else {
    "Do not run database acceptance tests; the independent gate will run the default non-database suite."
}
$mavenInstruction = if ($AllowOnlineMaven) {
    "Maven may use the network when resolving dependencies for this run."
} else {
    "Run Maven offline. If the local dependency cache is incomplete, report that limitation instead of changing project dependencies."
}

# The six roles in .codex/agents are a Codex feature. Every other agent gets the same division of
# labour described in prose, so the workflow does not depend on one tool's delegation mechanism.
$discoveryDelegation = if ($Agent -eq "codex") {
    @"
Delegate repository mapping to project_architect and test selection to test_engineer so they can
work in parallel. If database artifacts may be affected, also delegate risk analysis to
database_reviewer. If JavaFX user-visible text or layout may change, delegate localization analysis
to localization_reviewer. Wait for every delegated role.
"@
} else {
    @"
Cover four angles before you answer: how the affected code is structured and what its invariants
are, which existing tests are the relevant ones, what risk the change carries for the database
(migrations, views, transactions), and what it obliges in Arabic/English localization. Use
subagents for these if you have them.
"@
}
$deliveryDelegation = if ($Agent -eq "codex") {
    @"
2. Delegate the bounded source and test edits to implementer and wait until it exits completely.
3. Only after implementer finishes, delegate verification to test_engineer. Never overlap
   implementation and test execution.
"@
} else {
    @"
2. Make the bounded source and test edits first, and finish them.
3. Only then run the targeted verification. Never interleave editing and test execution.
"@
}

$discoveryPrompt = @"
You are the read-only discovery coordinator for this task:

$Task

Read docs/agent-worktree-rules.md first and obey it; it outranks this prompt. Then read AGENTS.md
and CLAUDE.md completely.

$discoveryDelegation

Do not edit files, run Maven, or execute commands that write generated output. Produce one concrete
implementation brief containing affected symbols, invariants, ordered implementation steps,
targeted clean tests, database acceptance needs, and localization obligations.
"@
$discoveryPrompt | Set-Content -LiteralPath (Join-Path $logDirectory "discovery-prompt.md") -Encoding utf8
$discoveryExit = Invoke-AgentSession -Prompt $discoveryPrompt -WorktreePath $worktreePath `
    -LogDirectory $logDirectory -Name "discovery" -AgentLimit $MaxAgents -Sandbox "read-only" `
    -ConfigOverrides $configOverrides
$runState["discoveryExitCode"] = $discoveryExit
if ($discoveryExit -ne 0) {
    throw "The read-only discovery phase failed. See $logDirectory/discovery.log"
}
$discoveryBriefPath = Join-Path $logDirectory "discovery-final.md"
if (-not (Test-Path -LiteralPath $discoveryBriefPath -PathType Leaf)) {
    throw "The read-only discovery phase produced no implementation brief."
}
$discoveryBrief = Get-Content -Raw -LiteralPath $discoveryBriefPath

$deliveryPrompt = @"
You are the implementation coordinator inside a dedicated Git worktree and task branch.

Task:
$Task

Read-only discovery brief:
$discoveryBrief

Required sequential workflow:
1. Read docs/agent-worktree-rules.md and obey it; it outranks this prompt. Then read AGENTS.md and
   CLAUDE.md completely and follow every applicable linked plan.
$deliveryDelegation
4. If JavaFX user-visible text or layout changes, use the repository skill at
   .agents/skills/javafx-localization-review/ in the same change.
5. Fix failures caused by the change and rerun the targeted clean tests.
6. Finish with changed files, commands and results, and real limitations. The outer runner owns the full
   Maven gate and final independent code review.

Stay inside this worktree. Do not commit, merge, rebase, push, tag, publish, or modify another worktree.
$databaseInstruction
$mavenInstruction
"@
$deliveryPrompt | Set-Content -LiteralPath (Join-Path $logDirectory "delivery-prompt.md") -Encoding utf8

$agentExit = Invoke-AgentSession -Prompt $deliveryPrompt -WorktreePath $worktreePath `
    -LogDirectory $logDirectory -Name "delivery-0" -AgentLimit $MaxAgents -Sandbox "workspace-write" `
    -ConfigOverrides $configOverrides

$testExit = 1
$reviewResult = [pscustomobject]@{ ExitCode = 0; Verdict = "skipped"; Path = $null; Summary = "Review was skipped." }
$completedPass = 0

for ($pass = 0; $pass -le $MaxFixPasses; $pass++) {
    Assert-HeadUnchanged -WorktreePath $worktreePath -ExpectedCommit $baseCommit
    $testExit = Invoke-MavenGate -WorktreePath $worktreePath -LogDirectory $logDirectory `
        -RunDatabaseAcceptance ([bool] $DatabaseAcceptance) -AllowOnline ([bool] $AllowOnlineMaven) `
        -DatabaseConfigSource $DatabaseConfigPath -DatabaseConfigKeySource $DatabaseConfigKeyPath `
        -Name "tests-$pass"

    $changes = & git -C $worktreePath status --porcelain
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect worktree changes."
    }

    if (-not $SkipReview -and $changes) {
        $reviewResult = Invoke-ReviewGate -WorktreePath $worktreePath -LogDirectory $logDirectory `
            -SchemaPath $reviewSchema -ConfigOverrides $configOverrides -Name "review-$pass"
    } elseif (-not $changes) {
        $reviewResult = [pscustomobject]@{ ExitCode = 1; Verdict = "error"; Path = $null; Summary = "The delivery produced no changes." }
    }

    $reviewPassed = $SkipReview -or $reviewResult.Verdict -eq "pass"
    if ($changes -and $agentExit -eq 0 -and $testExit -eq 0 -and $reviewPassed) {
        $completedPass = $pass
        break
    }

    if ($pass -ge $MaxFixPasses) {
        $completedPass = $pass
        break
    }

    $testTailPath = Join-Path $logDirectory "tests-$pass.log"
    $testTail = if (Test-Path -LiteralPath $testTailPath) {
        (Get-Content -LiteralPath $testTailPath -Tail 120) -join [Environment]::NewLine
    } else {
        "No Maven log was produced."
    }
    $reviewFeedback = if ($reviewResult.Path -and (Test-Path -LiteralPath $reviewResult.Path)) {
        Get-Content -Raw -LiteralPath $reviewResult.Path
    } else {
        $reviewResult.Summary
    }

    $repairPrompt = @"
Continue the assigned task in this same worktree. The independent gates did not pass.
Make the smallest correct fixes, finish them, and only then run verification. Never overlap writing
and test execution. Do not commit, merge, rebase, push, or touch another worktree;
docs/agent-worktree-rules.md still outranks this prompt.

Maven exit code: $testExit
Maven tail:
$testTail

Independent review:
$reviewFeedback

Resolve failures caused by this branch and all actionable P0-P2 findings, then run targeted clean tests.
"@
    $repairPrompt | Set-Content -LiteralPath (Join-Path $logDirectory "repair-prompt-$($pass + 1).md") -Encoding utf8
    $agentExit = Invoke-AgentSession -Prompt $repairPrompt -WorktreePath $worktreePath `
        -LogDirectory $logDirectory -Name "delivery-$($pass + 1)" -AgentLimit $MaxAgents `
        -Sandbox "workspace-write" -ConfigOverrides $configOverrides
}

Assert-HeadUnchanged -WorktreePath $worktreePath -ExpectedCommit $baseCommit
$finalChanges = & git -C $worktreePath status --short
$passed = [bool] $finalChanges -and $agentExit -eq 0 -and $testExit -eq 0 -and `
    ($SkipReview -or $reviewResult.Verdict -eq "pass")
$runState["status"] = if ($passed) { "passed" } else { "failed" }
$runState["passed"] = $passed
$runState["discoveryExitCode"] = $discoveryExit
$runState["deliveryExitCode"] = $agentExit
$runState["testExitCode"] = $testExit
$runState["reviewVerdict"] = $reviewResult.Verdict
$runState["reviewSummary"] = $reviewResult.Summary
$runState["completedPass"] = $completedPass
$runState["changes"] = @($finalChanges)
$runState["finishedAt"] = (Get-Date).ToString("o")
$runState | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $resultPath -Encoding utf8

Write-Host ""
Write-Host "Run:      $runId"
Write-Host "Branch:   $branchName"
Write-Host "Worktree: $worktreePath"
Write-Host "Result:   $resultPath"
Write-Host "Passed:   $passed"

if (-not $passed) {
    exit 1
}
