# Shared Codex CLI resolution for the Multi-Agent scripts.
#
# The Desktop app ships the CLI inside a build-hashed directory that changes on every update
# and is not added to PATH, so a hard-coded path goes stale and `codex` alone is often missing.
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

    throw "Codex CLI is not on PATH and no bundled build was found. Install it or pass -CodexPath."
}
