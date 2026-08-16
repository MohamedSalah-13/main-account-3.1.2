<#
.SYNOPSIS
    يبني نسخة قابلة للتثبيت من AccountK (حساباتك) عبر jpackage.

.DESCRIPTION
    ينتج حزمة تتضمّن Java نفسها، فلا يحتاج جهاز العميل تثبيت Java ولا Maven.
    يبني أولاً الـ shaded jar عبر maven-shade-plugin (كما هو معمول به حالياً في account/pom.xml)
    ثم يغلفه jpackage في exe/installer. الاثنان متكاملان، ليسا بديلين لبعض: shade ينتج
    الـ jar القابل للتشغيل، وjpackage يضيف عليه الـ JRE ويغلفه في مثبِّت ويندوز.

    نوعان:
      app-image  مجلد فيه ملف تنفيذي - لا يحتاج أي أدوات إضافية (الافتراضي)
      msi        مثبِّت ويندوز - يتطلب WiX Toolset 3.x مثبَّتاً مسبقاً

.PARAMETER Type
    app-image أو msi

.EXAMPLE
    .\packaging\build-installer.ps1
    .\packaging\build-installer.ps1 -Type msi
#>
param(
    [ValidateSet('app-image', 'msi')]
    [string]$Type = 'app-image'
)

$ErrorActionPreference = 'Stop'

$AppName = 'AccountK'
$MainClass = 'com.hamza.account.Main'

$root = Split-Path -Parent $PSScriptRoot
$accountDir = Join-Path $root 'account'
Set-Location $root

# استخرج رقم الإصدار من الـ parent pom (main/pom.xml) بدل تكراره هنا
# (account لا يعرّف <version> خاصاً به، فهو يرث إصدار الأب)
[xml]$parentPom = Get-Content (Join-Path $root 'pom.xml') -Raw
$AppVersion = $parentPom.project.version
if (-not $AppVersion) { throw "تعذّر قراءة رقم الإصدار من pom.xml" }
Write-Host "الإصدار: $AppVersion" -ForegroundColor DarkGray

# jpackage جزء من JDK لكنه غالباً ليس في PATH، فنبحث عنه بجوار java
function Resolve-JPackage {
    $onPath = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\jpackage.exe') }

    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($java) { $candidates += (Join-Path (Split-Path -Parent $java.Source) 'jpackage.exe') }

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { return $candidate }
    }

    throw "لم يُعثر على jpackage. يتطلب JDK 14 أو أحدث، وهذا المشروع يبنى على JDK 21. اضبط JAVA_HOME على مجلد JDK."
}

$jpackage = Resolve-JPackage
Write-Host "jpackage: $jpackage" -ForegroundColor DarkGray

# account يعتمد على controlsfx من نفس الـ build، فلازم -am
# (راجع CLAUDE.md: "-pl account needs -am or it compiles against whatever stale controlsfx is in ~/.m2")
Write-Host "==> بناء الحزمة (shade)" -ForegroundColor Cyan
& mvn -q -pl account -am clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "فشل بناء Maven" }

# اسم الـ jar المُغلّف يحمل timestamp البناء (account-<version>-<timestamp>.jar)
$jar = Get-ChildItem -Path (Join-Path $accountDir 'target') -Filter "account-$AppVersion-*.jar" |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw "لم يُعثر على الـ shaded jar في account\target" }
Write-Host "الـ jar: $($jar.Name)" -ForegroundColor DarkGray

# jpackage ينسخ مجلد الإدخال كاملاً، فنعزل الـ jar وحده
# وإلا نُسخت مخرجات الترجمة والاختبارات داخل التطبيق
$stage = Join-Path $accountDir 'target\jpackage-input'
Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $stage | Out-Null
Copy-Item $jar.FullName $stage

$dest = Join-Path $accountDir 'target\installer'
Remove-Item $dest -Recurse -Force -ErrorAction SilentlyContinue

$jpackageArgs = @(
    '--type', $Type,
    '--name', $AppName,
    '--app-version', $AppVersion,
    '--input', $stage,
    '--main-jar', $jar.Name,
    '--main-class', $MainClass,
    '--dest', $dest,
    '--description', 'AccountK - نظام المحاسبة',
    # الواجهة والبيانات بالعربية: بدون هذا تظهر الحروف مشوّهة على بعض الأنظمة
    '--java-options', '-Dfile.encoding=UTF-8'
)

$icon = Join-Path $accountDir 'src\main\resources\tools.ico'
if (Test-Path $icon) {
    $jpackageArgs += @('--icon', $icon)
} else {
    Write-Host "لا توجد أيقونة في account\src\main\resources\tools.ico — ستُستخدم أيقونة Java الافتراضية." -ForegroundColor DarkGray
}

if ($Type -eq 'msi') {
    $jpackageArgs += @(
        '--win-dir-chooser',      # يسمح للعميل باختيار مسار التثبيت
        '--win-menu',
        '--win-shortcut',
        '--win-menu-group', 'AccountK'
    )
}

Write-Host "==> jpackage ($Type)" -ForegroundColor Cyan
& $jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) {
    if ($Type -eq 'msi') {
        Write-Host "فشل بناء MSI. النوع msi يتطلب WiX Toolset 3.x:" -ForegroundColor Yellow
        Write-Host "  https://github.com/wixtoolset/wix3/releases" -ForegroundColor Yellow
        Write-Host "أو استخدم النوع app-image الذي لا يحتاج أدوات إضافية." -ForegroundColor Yellow
    }
    throw "فشل jpackage"
}

Write-Host ""
Write-Host "تم. الناتج في: $dest" -ForegroundColor Green
Write-Host ""
Write-Host "config.xml و config.key يُحلّان بالنسبة لمجلد العمل الذي يُشغَّل منه التطبيق -" -ForegroundColor Yellow
Write-Host "تأكد من وضعهما بجوار الملف التنفيذي الناتج قبل أول تشغيل عند العميل." -ForegroundColor Yellow
