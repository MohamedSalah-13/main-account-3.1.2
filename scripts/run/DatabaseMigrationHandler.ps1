param(
  [string]$Dir = "main",
  [string]$Host = "localhost",
  [int]$Port = 3306,
  [string]$Db = "account_system_db",
  [string]$User = "root",
  [string]$Pass = "m13ido",
  [switch]$DryRun,
  [switch]$TxPerFile,
  [string]$LogDir = "logs"
)

$mysql = Get-Command mysql -ErrorAction SilentlyContinue
if (-not $mysql) {
  Write-Error "لم يتم العثور على mysql في PATH"
  exit 1
}

# تجهيز السجل
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
$ts = Get-Date -Format "yyyyMMdd_HHmmss"
$log = Join-Path $LogDir "run_$ts.log"

$files = Get-ChildItem -Path $Dir -Filter *.sql -File -Recurse | Sort-Object FullName
if ($files.Count -eq 0) {
  "لا توجد ملفات .sql تحت $Dir" | Tee-Object -FilePath $log
  exit 0
}

"بدء التنفيذ. المجلد: $Dir | DryRun=$($DryRun.IsPresent) | TxPerFile=$($TxPerFile.IsPresent)" | Tee-Object -FilePath $log -Append

$mysqlArgs = @(
  "--protocol=TCP", "-h", $Host, "-P", "$Port",
  "-u", $User, "-p$Pass", $Db,
  "--default-character-set=utf8mb4", "--comments"
)

foreach ($f in $files) {
  "==> ملف: $($f.FullName)" | Tee-Object -FilePath $log -Append

  if ($DryRun) {
    "    (تجريبي) لن يتم التنفيذ" | Tee-Object -FilePath $log -Append
    continue
  }

  if ($TxPerFile) {
    $payload = @"
START TRANSACTION;
$(Get-Content -Raw -Path $f.FullName)
COMMIT;
"@
    $payload | & $mysql.Source $mysqlArgs 2>&1 | Tee-Object -FilePath $log -Append
  } else {
    Get-Content -Raw -Path $f.FullName | & $mysql.Source $mysqlArgs 2>&1 | Tee-Object -FilePath $log -Append
  }

  if ($LASTEXITCODE -ne 0) {
    "فشل تنفيذ الملف: $($f.FullName) (code=$LASTEXITCODE). تم الإيقاف." | Tee-Object -FilePath $log -Append
    exit $LASTEXITCODE
  }
}

"تم التنفيذ بنجاح. السجل: $log" | Tee-Object -FilePath $log -Append
