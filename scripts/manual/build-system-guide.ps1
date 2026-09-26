<# Builds a dedicated PDF guide from docs/manual-systems/<system>. #>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $System,
    [string] $Title,
    [string] $Version = "4.13.0",
    [string] $Output
)

$ErrorActionPreference = "Stop"
$repo = Resolve-Path (Join-Path $PSScriptRoot "../..")
Set-Location $repo
$source = Join-Path $repo "docs/manual-systems/$System"
if (-not (Test-Path -LiteralPath $source -PathType Container)) {
    throw "Guide source directory not found: $source"
}
if ([string]::IsNullOrWhiteSpace($Title)) { $Title = "AccountK - نظام $System" }
if ([string]::IsNullOrWhiteSpace($Output)) { $Output = "output/pdf/AccountK-$System-Guide.pdf" }

$depDir = Join-Path $repo "account/target/manual-deps"
New-Item -ItemType Directory -Force -Path $depDir | Out-Null
$repository = Join-Path $env:USERPROFILE ".m2/repository"
foreach ($relativeJar in @(
    "com/itextpdf/io/8.0.5/io-8.0.5.jar",
    "com/itextpdf/commons/8.0.5/commons-8.0.5.jar",
    "com/itextpdf/kernel/8.0.5/kernel-8.0.5.jar",
    "com/itextpdf/layout/8.0.5/layout-8.0.5.jar",
    "com/ibm/icu/icu4j/74.2/icu4j-74.2.jar",
    "org/slf4j/slf4j-api/2.0.5/slf4j-api-2.0.5.jar"
)) {
    Copy-Item -LiteralPath (Join-Path $repository $relativeJar) -Destination $depDir -Force
}
$classpath = (Get-ChildItem -LiteralPath $depDir -Filter "*.jar" | ForEach-Object FullName) -join ";"
$classes = Join-Path $repo "account/target/manual-classes"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources = @(
    "account/src/main/java/com/hamza/account/features/export/ArabicTextHelper.java",
    "account/src/test/java/com/hamza/account/manual/ManualBlock.java",
    "account/src/test/java/com/hamza/account/manual/ManualPage.java",
    "account/src/test/java/com/hamza/account/manual/ManualParser.java",
    "account/src/test/java/com/hamza/account/manual/ManualMeta.java",
    "account/src/test/java/com/hamza/account/manual/ManualText.java",
    "account/src/test/java/com/hamza/account/manual/ManualPdfWriter.java",
    "account/src/test/java/com/hamza/account/manual/SystemManualBuilder.java"
)
& javac -encoding UTF-8 -cp $classpath -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "guide builder compilation failed" }
$encodedTitle = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Title))
$target = Join-Path $repo $Output
$parent = Split-Path -Parent $target
New-Item -ItemType Directory -Force -Path $parent | Out-Null
$encodedOutput = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($target))
$runtimeClasspath = @($classes, "account/src/main/resources", $classpath) -join ";"
& java "-Dfile.encoding=UTF-8" -cp $runtimeClasspath com.hamza.account.manual.SystemManualBuilder `
    $source $encodedTitle $Version $encodedOutput
if ($LASTEXITCODE -ne 0) { throw "manual builder exited with $LASTEXITCODE" }
