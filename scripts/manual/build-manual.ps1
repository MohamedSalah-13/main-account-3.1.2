<#
.SYNOPSIS
    Builds docs/manual/AccountK-User-Manual.pdf, re-taking the screenshots first.

.DESCRIPTION
    Four steps, each runnable on its own:

      1. ManualDemoDatabase  creates the throwaway schema account_manual_demo and writes a
                             config.xml/config.key pair for it into -ConfigDir.
      2. ManualDemoSetup     runs the application's own Flyway migration against it and seeds
                             docs/manual/demo-data.sql.
      3. ManualCapture       starts the application against that schema, opens every screen the
                             manual has a page for, and writes docs/manual/images/<id>.png.
      4. ManualBuilder       renders the PDF from docs/manual.

    The screenshots are the part that goes stale, so at release time this is the whole job:
    run it, read what it says it could not photograph, and commit the images with the release.

.NOTES
    The demo schema borrows this machine's MySQL server and credentials but never its database.
    ACCOUNT_CONFIG_DIR is set for steps 2 and 3 only, and points at -ConfigDir, so the application
    opens the demo schema rather than the one this workstation normally uses.

    One thing a demo database does not isolate: java.util.prefs. The backup folder, the retention
    rule and the notification settings still belong to this Windows account. The harness sets the
    theme to light and puts it back; it never signs in through the login screen, which is what
    would start the scheduled backup, so nothing writes to the real backup folder. Keep it that way.

.EXAMPLE
    pwsh scripts/manual/build-manual.ps1 -Version 4.7.0

.EXAMPLE
    pwsh scripts/manual/build-manual.ps1 -SkipCapture      # prose changed, pictures did not
#>
[CmdletBinding()]
param(
    [string] $Version = "dev",
    [string] $ConfigDir = "$env:TEMP\accountk-manual-demo",
    [string] $Output = "docs/manual/AccountK-User-Manual.pdf",
    [double] $SettleSeconds = 2.5,
    [switch] $SkipCapture,
    [switch] $DropDemo
)

$ErrorActionPreference = "Stop"
$repo = Resolve-Path (Join-Path $PSScriptRoot "../..")
Set-Location $repo

function Invoke-Tool {
    param([string] $Class, [string[]] $Arguments = @(), [hashtable] $Properties = @{})
    $options = @()
    $options += "-Dfile.encoding=UTF-8"
    foreach ($name in $Properties.Keys) { $options += "-D$name=$($Properties[$name])" }
    & java @options -cp $script:classpath $Class @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Class exited with $LASTEXITCODE" }
}

Write-Host "== compiling ==" -ForegroundColor Cyan
& mvn -o -q -pl account -am test-compile -DskipTests
if ($LASTEXITCODE -ne 0) { throw "the build failed" }

& mvn -o -q -pl account dependency:build-classpath "-Dmdep.outputFile=target/manual-cp.txt" -DincludeScope=test
if ($LASTEXITCODE -ne 0) { throw "could not resolve the classpath" }

$dependencies = Get-Content "account/target/manual-cp.txt" -Raw
$script:classpath = "account/target/classes;account/target/test-classes;controlsfx/target/classes;$dependencies"

if ($DropDemo) {
    Write-Host "== dropping the demo database ==" -ForegroundColor Cyan
    Invoke-Tool -Class "com.hamza.account.manual.ManualDemoDatabase" -Arguments @($ConfigDir, "--drop")
    return
}

if (-not $SkipCapture) {
    Write-Host "== demo database ==" -ForegroundColor Cyan
    Invoke-Tool -Class "com.hamza.account.manual.ManualDemoDatabase" -Arguments @($ConfigDir)

    $env:ACCOUNT_CONFIG_DIR = $ConfigDir
    try {
        Write-Host "== migrate and seed ==" -ForegroundColor Cyan
        Invoke-Tool -Class "com.hamza.account.manual.ManualDemoSetup" -Arguments @("docs/manual/demo-data.sql")

        Write-Host "== screenshots ==" -ForegroundColor Cyan
        # A screen the harness cannot open is reported and left as a placeholder in the PDF, which
        # is a result rather than a failure - so a non-zero exit here does not stop the build.
        Invoke-Tool -Class "com.hamza.account.manual.ManualCapture" -Properties @{
            "account.manual.images" = "docs/manual/images"
            "account.manual.root"   = "."
            "account.manual.settle" = $SettleSeconds
        } -ErrorAction Continue
    } catch {
        Write-Warning "the capture step did not finish cleanly: $_"
    } finally {
        Remove-Item Env:\ACCOUNT_CONFIG_DIR -ErrorAction SilentlyContinue
    }
}

Write-Host "== pdf ==" -ForegroundColor Cyan
Invoke-Tool -Class "com.hamza.account.manual.ManualBuilder" -Arguments @(".", $Version, $Output)

Write-Host "done: $Output" -ForegroundColor Green
