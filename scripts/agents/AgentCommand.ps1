# Shared CLI resolution for the Multi-Agent scripts.
#
# The runner is agent-neutral: it owns the worktree, the build gate and the review gate, and it
# treats the agent itself as a replaceable part. Each supported CLI needs one resolver, because
# neither of them can be assumed to be on PATH.
function Resolve-CodexCommand {
    param([string] $Explicit)

    if ($Explicit) {
        if (-not (Test-Path -LiteralPath $Explicit -PathType Leaf)) {
            throw "The Codex executable does not exist: $Explicit"
        }
        return (Get-Item -LiteralPath $Explicit).FullName
    }

    $onPath = Get-Command codex -ErrorAction SilentlyContinue
    if ($onPath) {
        return $onPath.Source
    }

    # The Desktop app ships the CLI inside a build-hashed directory that changes on every update
    # and is never added to PATH, so resolve the newest rather than pinning a path.
    if ($env:LOCALAPPDATA) {
        $bundledRoot = Join-Path $env:LOCALAPPDATA "OpenAI\Codex\bin"
        if (Test-Path -LiteralPath $bundledRoot -PathType Container) {
            $candidate = Get-ChildItem -LiteralPath $bundledRoot -Filter codex.exe -File -Recurse -Depth 1 -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending | Select-Object -First 1
            if ($candidate) {
                return $candidate.FullName
            }
        }
    }

    throw "Codex CLI is not on PATH and no bundled build was found. Install it or pass -AgentPath."
}

function Resolve-ClaudeCommand {
    param([string] $Explicit)

    if ($Explicit) {
        if (-not (Test-Path -LiteralPath $Explicit -PathType Leaf)) {
            throw "The Claude executable does not exist: $Explicit"
        }
        return (Get-Item -LiteralPath $Explicit).FullName
    }

    $onPath = Get-Command claude -ErrorAction SilentlyContinue
    if ($onPath) {
        return $onPath.Source
    }

    # npm's global shim directory is not on PATH on every machine either.
    if ($env:APPDATA) {
        $shim = Join-Path $env:APPDATA "npm\claude.cmd"
        if (Test-Path -LiteralPath $shim -PathType Leaf) {
            return (Get-Item -LiteralPath $shim).FullName
        }
    }

    throw "Claude CLI is not on PATH and no npm shim was found. Install it or pass -AgentPath."
}

function Resolve-AgentCommand {
    param(
        [Parameter(Mandatory)] [ValidateSet("codex", "claude")] [string] $Agent,
        [string] $Explicit
    )

    if ($Agent -eq "claude") {
        return Resolve-ClaudeCommand -Explicit $Explicit
    }
    return Resolve-CodexCommand -Explicit $Explicit
}
