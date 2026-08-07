# إعداد ملف الاتصال بقاعدة البيانات

يقرأ البرنامج بيانات الاتصال من ملف `config.xml` مشفَّر. هذا الدليل يشرح كيفية إنشائه، وترقية ملف قديم،
وتغيير بيانات الاعتماد.

## نظرة سريعة

| الملف | الغرض | متتبَّع في Git؟ |
|---|---|---|
| `config.xml` | بيانات الاتصال مشفَّرة | ❌ لا |
| `config.key` | مفتاح التشفير الخاص بهذا التثبيت | ❌ لا |
| `config.xml.example` | قالب للتوثيق فقط | ✅ نعم |

⚠️ **الملفان الأولان يُقرآن من مجلد تشغيل البرنامج (working directory)، لا من مجلد المستودع.** إن شغّلت
البرنامج من مجلد آخر فلن يجد الملفات. هذا أكثر سبب شائع لرسالة "config.xml not found".

## المتطلبات

- Java 21
- MySQL يعمل ويمكن الوصول إليه
- بناء المشروع مرة واحدة على الأقل حتى تتوفر أصناف الأداة:

```bash
mvn -o clean compile -DskipTests
```

---

## أولًا: تثبيت جديد

### 1. ولّد مفتاح تشفير خاصًا بك

```bash
java -cp controlsfx/target/classes com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig genkey
```

يطبع مفتاحًا مثل:

```
Xw4+b2QIwkqGJqZhUHgXxbZYxhsHLx5J0QbfrjERHPw=
```

### 2. ثبّت المفتاح

إمّا في ملف `config.key` داخل مجلد التشغيل:

```bash
echo "المفتاح_المولَّد" > config.key
```

أو في متغيّر بيئة (له الأولوية على الملف):

```bash
setx ACCOUNT_CONFIG_KEY "المفتاح_المولَّد"
```

> 🔑 **احتفظ بنسخة من المفتاح في مكان آمن.** بدونه لا يمكن قراءة `config.xml` إطلاقًا — لا يوجد استرجاع.
> ولا تضعه في Git.

### 3. أنشئ `config.xml`

```bash
java -cp controlsfx/target/classes com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig \
  encrypt <port> <host> <dbname> <username> <password> config.xml
```

مثال:

```bash
java -cp controlsfx/target/classes com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig \
  encrypt 3306 localhost account_system_db appuser 'كلمة_المرور' config.xml
```

يطبع الأمر المفتاح الذي استُخدم فعليًا — تأكّد أنه المصدر الذي تقصده.

### 4. تحقّق

```bash
java -cp controlsfx/target/classes com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig decrypt config.xml
```

يجب أن تظهر البيانات صحيحة. ثم شغّل البرنامج:

```bash
mvn -pl account javafx:run
```

---

## ثانيًا: ترقية ملف قديم

التثبيتات التي أُنشئت قبل هذا التحديث تستخدم **المفتاح المدمج في الكود** وصيغة تشفير قديمة بلا تحقق سلامة.
هذه الملفات ما زالت تُقرأ، لكن يُنصح بترقيتها.

عند بدء التشغيل، إن كان التثبيت ما زال على المفتاح المدمج، يُسجَّل تحذير في `logs/app.log`.

### خطوات الترقية

```bash
# 1. ولّد مفتاحًا جديدًا وثبّته (كما في القسم السابق)
java -cp controlsfx/target/classes com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig genkey > config.key

# 2. أعد تشفير الملف القائم بالمفتاح الجديد
java -cp controlsfx/target/classes com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig migrate config.xml
```

يقرأ `migrate` الملف بأي مفتاح كُتب به (الخاص أولًا ثم المدمج) ويعيد كتابته بالمفتاح الجديد وبصيغة `v2`.

خذ نسخة احتياطية من `config.xml` قبل الترقية.

> ⚠️ **الترقية وحدها لا تكفي.** أي بيانات اعتماد كانت محمية بالمفتاح المدمج يجب اعتبارها مكشوفة، لأن ذلك
> المفتاح منشور في الكود المصدري. راجع القسم التالي.

---

## ثالثًا: تغيير بيانات الاعتماد

إن كانت بيانات الاعتماد قد تسرّبت (أو كنت تشكّ في ذلك)، فإعادة التشفير **لا تُلغي التسريب** — من حصل على
البيانات سابقًا ما زال يملكها. الحل الوحيد تغييرها على الخادم:

```bash
# 1. غيّر كلمة المرور على MySQL
mysql -u root -p -e "ALTER USER 'appuser'@'%' IDENTIFIED BY 'كلمة_مرور_جديدة_قوية';"

# 2. أعد إنشاء config.xml بالبيانات الجديدة
java -cp controlsfx/target/classes com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig \
  encrypt 3306 localhost account_system_db appuser 'كلمة_مرور_جديدة_قوية' config.xml
```

---

## الأوامر المتاحة

| الأمر | الوظيفة |
|---|---|
| `genkey` | توليد مفتاح تشفير جديد |
| `encrypt <port> <host> <dbname> <user> <pass> <file>` | إنشاء ملف إعدادات جديد |
| `decrypt <file>` | عرض محتوى ملف إعدادات |
| `migrate <file>` | إعادة تشفير ملف قائم بالمفتاح الحالي |

---

## حلّ المشكلات

**`config.xml not found at ...`**
البرنامج يبحث في مجلد التشغيل. تأكّد أن الملف بجوار مكان تشغيلك — جذر المستودع عند التشغيل من الجذر، أو
`account/` عند تشغيل تلك الوحدة مباشرة. المسار الكامل يظهر في الرسالة نفسها.

**`Could not decrypt ... with the key from ...`**
المفتاح الحالي ليس المفتاح الذي شُفّر به الملف. تحقّق من `ACCOUNT_CONFIG_KEY` (له الأولوية) ومن محتوى
`config.key`. الرسالة تذكر المصدر الذي استُخدم فعليًا.

**`Tag mismatch`**
ملف بصيغة `v2` عُدّل بعد تشفيره، أو تلف. استعد نسخة احتياطية أو أعد إنشاءه بـ `encrypt`.

**`No encryption key of this install's own was found...`**
تحاول الكتابة دون تثبيت مفتاح خاص. هذا مقصود: المفتاح المدمج لا يُسمح بالكتابة به لأنه منشور في الكود.
ولّد مفتاحًا بـ `genkey` وثبّته أولًا.

**`config.xml has no <host> element` / `The <host> element in config.xml is empty`**
الملف ناقص أو أُنشئ يدويًا. أعد إنشاءه بأمر `encrypt`.

---

## ملاحظات أمنية

- القيم الجديدة تُكتب بصيغة `v2:` باستخدام **AES/GCM** مع IV عشوائي لكل قيمة وعلامة توثيق، فأي تعديل على
  الملف يُرفض بدل أن يمرّ بقيم مغيّرة.
- القيم بلا بادئة `v2:` هي الصيغة القديمة (AES/ECB بلا تحقق سلامة). تُقرأ للتوافق فقط ولا يُكتب بها شيء.
- الكتابة ترفض المفتاح المدمج. القراءة تقبله حتى لا تتعطّل التثبيتات القائمة.
- لا يُكتب المفتاح بجوار الملف المشفَّر. (كان إصدار سابق يكتبه في `secret_key.txt` في نفس المجلد، وهو ما
  يُبطل التشفير عمليًا؛ أُزيل ذلك. احذف أي `secret_key.txt` متبقٍّ لديك.)
- `config.xml` و`config.key` و`secret_key.txt` مُستبعدة في `.gitignore` — أبقِها كذلك.
