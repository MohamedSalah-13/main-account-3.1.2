# تشغيل البرنامج على نسخة من قاعدة البيانات

عند تجربة تعديل على الشاشة (حذف فواتير، ترحيل جرد، تسوية وردية) لا تشغّل البرنامج على
`account_system_db`. أي تجربة عليها تكتب فعلًا: حُذفت فواتير حقيقية بهذه الطريقة يوم 2026-09-15
أثناء تجربة الحذف المتعدد. هذا الدليل يشرح كيف تنسخ القاعدة، وتوجّه البرنامج إلى النسخة عبر
`ACCOUNT_CONFIG_DIR`، ثم تحذف النسخة بعد انتهاء التجربة.

الأوامر كُتبت لـ **Windows PowerShell 5.1**، وجُرّبت فعليًا على هذا الجهاز (MySQL 8.0.31)، ثم حُذفت
النسخة التجريبية. جاءت النسخة مطابقة للأصل: 91 جدولًا و36 view و75 trigger و3 procedures، ومجموع
CRC32 لأسماء العملاء والأصناف العربية مطابق. الملف المُصدَّر 5 ميجابايت، واستغرق الاستيراد 68 ثانية.

---

## ما الذي تعزله النسخة وما الذي لا تعزله

| الشيء | أين يُحفَظ | معزول في النسخة؟ |
|---|---|---|
| الفواتير والأصناف والعملاء والخزينة والورديات | القاعدة | ✅ نعم |
| الإعدادات المشتركة (`app_setting`: الميزان، مالك النسخ الاحتياطي…) | القاعدة | ✅ نعم |
| سجل الترحيلات (`flyway_schema_history`) | القاعدة | ✅ نعم، فلا يُعاد تطبيق شيء |
| **مسار النسخ الاحتياطي وسياسة الاحتفاظ** | Java `Preferences` في الريجستري | ❌ **لا** |
| إعدادات الإشعارات، اللغة، أعمدة الجداول، التواريخ المحفوظة | Java `Preferences` | ❌ لا |
| `license.dat` | مجلد التشغيل | — يلزم وجوده |

> ⚠️ **أهم نقطة:** `Preferences` مشتركة بين كل تشغيل على نفس حساب Windows. التشغيل على نسخة
> يستخدم **مجلد النسخ الاحتياطي الحقيقي نفسه**: الحذف يكتب فيه ملف `before-delete_`، وقد تحذف
> سياسة الاحتفاظ منه ملفات قديمة. في 2026-09-08 حذف تشغيلٌ على نسخة الملف
> `backup_20260905_121615.enc` من `E:\backup\test`. الخطوة 4 أدناه تحمي من ذلك.

---

## المتطلبات

- `mysql.exe` و`mysqldump.exe` في الـ PATH. للتحقق: `where.exe mysql mysqldump`.
- حساب MySQL يستطيع إنشاء قاعدة، غالبًا `root`. حساب البرنامج يملك صلاحيات على
  `account_system_db.*` فقط، فلن يستطيع فتح النسخة.
- المشروع مبني مرة على الأقل (`mvn -o clean install -DskipTests`) حتى توجد
  `controlsfx\target\classes`.
- **لا تضبط `$ErrorActionPreference = 'Stop'`** في الجلسة. `java` و`mysql` يكتبان رسائل عادية على
  stderr، وفي PowerShell 5.1 يتحول ذلك إلى خطأ يوقف الأوامر.

---

## 1. متغيرات الجلسة

افتح PowerShell جديدًا في جذر المشروع (أو الـ worktree) الذي ستشغّل منه البرنامج:

```powershell
$source = "account_system_db"
$copy   = "account_copy_" + (Get-Date -Format "yyyyMMdd_HHmm")
$dir    = Join-Path $env:TEMP "accountk-copy\$copy"
New-Item -ItemType Directory -Force $dir | Out-Null

$secure = Read-Host "MySQL root password" -AsSecureString
$env:MYSQL_PWD = [System.Net.NetworkCredential]::new("", $secure).Password
```

`MYSQL_PWD` يمرّر كلمة المرور إلى `mysql` و`mysqldump` دون أن تظهر في سطر الأوامر أو في قائمة
العمليات، وهي نفس الطريقة التي يستخدمها `BackupService`.

## 2. نسخ القاعدة

```powershell
$charset = mysql -u root -N -e "SELECT CONCAT(DEFAULT_CHARACTER_SET_NAME, ' COLLATE ', DEFAULT_COLLATION_NAME) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '$source'"
mysql -u root -e "CREATE DATABASE $copy CHARACTER SET $charset"

mysqldump -u root --single-transaction --no-tablespaces --routines --triggers --set-gtid-purged=OFF --default-character-set=utf8mb4 "--result-file=$dir\dump.sql" $source

$dumpPath = "$dir\dump.sql".Replace([char]92, [char]47)
mysql -u root --default-character-set=utf8mb4 $copy -e "source $dumpPath"
```

ثلاثة أشياء في هذه الأوامر مقصودة، ولا تغيّرها:

- **لا تستخدم `--databases`** مع `mysqldump`. هذا الخيار يضع `CREATE DATABASE` و`USE account_system_db`
  داخل الملف، فيُستورد في **القاعدة الأصلية** بدل النسخة.
- **لا تستخدم الأنبوب `|`** بين `mysqldump` و`mysql` في PowerShell 5.1. PowerShell يعيد ترميز النص
  المار بين برنامجين خارجيين، وقد يُتلف العربية. لذلك يُكتب الملف بـ `--result-file` ثم يُقرأ بـ `source`.
- الترميز يُقرأ من القاعدة الأصلية (على هذا الجهاز `utf8mb4 COLLATE utf8mb4_0900_ai_ci`)، بدل كتابة
  ترميز ثابت قد يختلف عنها.

## 3. التحقق من النسخة

```powershell
foreach ($db in $source, $copy) {
  $q = "SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$db' AND table_type='BASE TABLE'), (SELECT COUNT(*) FROM information_schema.views WHERE table_schema='$db'), (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema='$db'), (SELECT CONCAT(COUNT(*),'/',COALESCE(SUM(CRC32(name)),0)) FROM $db.custom), (SELECT COUNT(*) FROM $db.total_sales)"
  "$db -> " + (mysql -u root -N -e $q)
}
```

يجب أن يتطابق السطران. العمود الرابع مجموع CRC32 لأسماء العملاء، فإن اختلف فالنص العربي تلف
أثناء النسخ.

احذف ملف التصدير بعد التحقق. فيه كل بيانات القاعدة، ومنها هاشات كلمات المرور:

```powershell
Remove-Item -LiteralPath "$dir\dump.sql"
```

## 4. حماية مجلد النسخ الاحتياطي الحقيقي

خذ نسخة من إعدادات البرنامج في الريجستري قبل التشغيل:

```powershell
reg export "HKCU\Software\JavaSoft\Prefs\com\hamza" "$dir\prefs-before.reg" /y
```

بعد فتح البرنامج على النسخة، وقبل أي تجربة، افتح **الإعدادات ← النسخ الاحتياطي** وغيّر المسار إلى
مجلد داخل `$dir`، مثلًا `...\accountk-copy\account_copy_...\backups`. هكذا تذهب ملفات
`before-delete_` وقرارات الاحتفاظ إلى هذا المجلد، لا إلى مجلد نسخك الحقيقية.

عند الانتهاء استرجع الإعدادات كما كانت (الخطوة 7).

## 5. ملف الاتصال للنسخة

البرنامج يقرأ `config.xml` و`config.key` من المجلد الذي يحدده `ACCOUNT_CONFIG_DIR`. لإنشائهما
طريقتان:

### أ. أداة الإعداد (بدون كلمة مرور في سطر الأوامر)

إذا كان AccountK مثبّتًا، فالأداة `AccountK-Database-Setup.exe` تحترم المتغير نفسه:

```powershell
$env:ACCOUNT_CONFIG_DIR = $dir
& "<مجلد التثبيت>\AccountK-Database-Setup.exe"
```

من IntelliJ: أنشئ إعداد تشغيل Application لـ `com.hamza.account.DatabaseSetupMain`، وأضف في
**Environment variables** القيمة `ACCOUNT_CONFIG_DIR=<قيمة $dir>`.

داخل الأداة: اختر «الجهاز الرئيسي»، واكتب اسم القاعدة `$copy` وحساب `root`. اضغط «اختبار الاتصال»
ثم «حفظ الإعدادات». المسار الذي تكتب فيه الأداة يظهر أعلى نافذتها، فتأكد أنه `$dir`.

### ب. سطر الأوامر

هذه الطريقة جُرّبت. تعيبها أن كلمة المرور تظهر في قائمة العمليات أثناء تنفيذ الأمر:

```powershell
$classes = (Resolve-Path "controlsfx\target\classes").Path
$key = (java -cp $classes com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig genkey 2>$null | Select-Object -Last 1)
Set-Content -Path "$dir\config.key" -Value $key -Encoding ascii

Push-Location $dir
java -cp $classes com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig encrypt 3306 localhost $copy root $env:MYSQL_PWD config.xml
java -cp $classes com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig decrypt config.xml
Pop-Location
```

أمر `decrypt` يجب أن يطبع `dbname: account_copy_...`. المفتاح يُكتب بترميز ASCII عمدًا: التوجيه
`>` في PowerShell يكتب UTF-16. البرنامج يقرأ UTF-16، لكن لا داعي لذلك.

> تأكد أن `ACCOUNT_CONFIG_KEY` **غير مضبوط** في الجلسة (`$env:ACCOUNT_CONFIG_KEY` فارغ). إن كان
> مضبوطًا فله الأولوية على `config.key` الموجود بجوار الملف، ولن يُفتح `config.xml`.

## 6. التشغيل

### من سطر الأوامر

```powershell
$env:ACCOUNT_CONFIG_DIR = $dir
mvn -o clean install -DskipTests
mvn -o -pl account javafx:run
```

- `install` **من نفس المجلد** الذي ستشغّل منه. `-pl account` يقرأ `controlsfx` من `~/.m2`، وتثبيته من
  شجرة أخرى يعني تشغيل كود قديم. بهذه الطريقة ظهرت رسالة الحذف كاسم مفتاح بدل النص.
- **لا تكتب `-am`** مع `javafx:run`: يضيف المشروع الجذر فيفشل بخطأ `mainClass ... missing`.
- `license.dat` يُقرأ من **مجلد التشغيل**، وهو مع هذا الأمر المجلد `account\`. انسخه إليه، وإلا
  أُغلق البرنامج بعد تحذير الفترة التجريبية.

### من IntelliJ

في إعداد تشغيل `Main` أضف في **Environment variables** القيمة `ACCOUNT_CONFIG_DIR=<قيمة $dir>`.
وتأكد أن `license.dat` موجود في المجلد المكتوب في **Working directory**.

### قبل أي تجربة

افتح `account\logs\app.log` وابحث عن آخر سطر `Database:`:

```
FlywayExecutor - Database: jdbc:mysql://localhost:3306/account_copy_20260915_0930?...
```

إذا ظهر `account_system_db` فأغلق البرنامج فورًا. معنى ذلك أن المتغير لم يصل إلى البرنامج.

## 7. الانتهاء والتنظيف

```powershell
# استرجاع إعدادات البرنامج كما كانت قبل التجربة
reg import "$dir\prefs-before.reg"

# حذف النسخة، مع رفض أي اسم لا يبدأ بـ account_copy_
if ($copy -notlike 'account_copy_*') { throw "refusing to drop $copy" }
mysql -u root -e "DROP DATABASE $copy"
mysql -u root -N -e "SHOW DATABASES LIKE 'account_copy_%'"

Remove-Item -LiteralPath $dir -Recurse -Force
$env:MYSQL_PWD = $null
$env:ACCOUNT_CONFIG_DIR = $null
```

الأمر `reg import` يعيد القيم التي تغيّرت، ولا يحذف مفاتيح جديدة أُضيفت أثناء التجربة. هذا لا يضر:
تلك المفاتيح إعدادات أعمدة وتواريخ فقط.

---

## حل المشكلات

**`ERROR 1419 ... You do not have the SUPER privilege and binary logging is enabled`** أثناء `source`
الـ binary log مفعّل، واستيراد triggers وprocedures يحتاج صلاحية. على خادم التطوير هنا القيمة
`log_bin_trust_function_creators` مضبوطة مسبقًا، فلم يظهر الخطأ. على خادم نظيف يضبطها مسؤول MySQL
مرة واحدة (`SET PERSIST log_bin_trust_function_creators = 1`)، أو يُستورد بحساب `root`.

**البرنامج فتح `account_system_db` رغم ضبط المتغير**
المتغير ضُبط في جلسة PowerShell أخرى، أو ضُبط بعد فتح IntelliJ. IntelliJ لا يرى متغيرات البيئة التي
أُضيفت بعد تشغيله إلا عبر إعداد التشغيل نفسه.

**`Could not decrypt ... with the key from ...`**
`ACCOUNT_CONFIG_KEY` مضبوط في الجلسة، أو `config.key` في `$dir` ليس المفتاح الذي شُفّر به `config.xml`.
أعد الخطوة 5.

**أوامر `java` تتوقف بخطأ `NativeCommandError`**
`$ErrorActionPreference` مضبوط على `Stop`. أرجعه إلى `Continue`.

**أسماء عربية مشوّهة في النسخة**
الاستيراد مرّ عبر أنبوب `|` أو بترميز غير `utf8mb4`. احذف النسخة وأعد الخطوة 2 كما هي.

---

راجع أيضًا: [`CONNECTION_SETUP.md`](CONNECTION_SETUP.md) لملفات الاتصال عمومًا، و
[`agent-worktree-rules.md`](agent-worktree-rules.md) لتشغيل اختبارات القبول على قاعدة مؤقتة.
