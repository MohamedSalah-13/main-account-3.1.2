# main-account

## توثيق تحديثات نظام النسخة التجريبية

### ملخص عام
تم تعزيز حماية النسخة التجريبية لتقليل التلاعب في الملف أو قاعدة البيانات وربطها بجهاز واحد. النظام الآن يعتمد على تشفير أقوى، توقيع سلامة للبيانات، وتحقق صارم من التطابق بين الملف وقاعدة البيانات.

### ما تم إضافته وتحديثه
1. **تشفير الملف**
   - استخدام `AES/GCM/NoPadding` مع `IV` عشوائي لكل عملية حفظ.
   - الملف الآن يحتوي إصدار صيغة `v1` لتسهيل التطوير لاحقاً.

2. **توقيع سلامة البيانات (HMAC)**
   - إنشاء `HMAC-SHA256` لربط بيانات التفعيل بالملف والـDB.
   - أي اختلاف في التوقيع يمنع التشغيل فوراً.

3. **ربط النسخة التجريبية بجهاز ويندوز**
   - استخدام `MachineGuid` من سجل ويندوز `HKLM\SOFTWARE\Microsoft\Cryptography`.
   - لا يمكن تشغيل النسخة التجريبية على جهاز مختلف.

4. **مزامنة ملف النسخة التجريبية مع قاعدة البيانات**
   - يجب أن يتطابق تاريخ التثبيت ومعرّف الجهاز بين الملف وDB.
   - إذا كان أحدهما مفقوداً بعد أول تشغيل، يتم منع التشغيل.

5. **منع التلاعب بالوقت**
   - حفظ `trial_last_check` في الملف وDB.
   - إذا كان وقت الجهاز الحالي أقدم من آخر تحقق، يتم منع التشغيل.

6. **تسجيل محاولات التلاعب**
   - إضافة أعمدة `trial_fail_count` و`trial_fail_last`.
   - عند أول محاولة تلاعب يتم تسجيلها، والمحاولة التالية تمنع التشغيل نهائياً.

7. **ترقية تلقائية للنسخ القديمة**
   - إذا كان الملف بصيغة قديمة (بدون الجهاز/التوقيع)، يتم ترقية البيانات بشرط تطابق التاريخ بين الملف وDB.

### الأعمدة الجديدة/المستخدمة في جدول الشركة
تمت إضافة أو استخدام الأعمدة التالية في جدول `company`:
- `installation_date` (تاريخ التثبيت)
- `trial_machine` (معرّف الجهاز)
- `trial_hash` (توقيع HMAC)
- `trial_last_check` (آخر تحقق وقتي)
- `trial_fail_count` (عدد محاولات التلاعب)
- `trial_fail_last` (تاريخ آخر محاولة)

### مسار ملف النسخة التجريبية في ويندوز
`%APPDATA%\HamzaAccount\trial.dat`

### ملف الترخيص المدفوع (بدون تاريخ)
- المسار الافتراضي: `license.dat` داخل مجلد البرنامج الحالي.
- عند وجود ملف ترخيص صالح، يتم تجاوز كل منطق النسخة التجريبية.

#### صيغة ملف الترخيص
الملف يتكون من جزئين مفصولين بنقطة:
```
BASE64(payload).BASE64(signature)
```

صيغة `payload`:
```
HAMZA_ACCOUNT|<MachineGuid>
```
- الترخيص الآن مرتبط دائمًا بجهاز محدد (لا يوجد ترخيص عام).

#### كيفية إنشاء الترخيص باستخدام OpenSSL
1) **إنشاء المفاتيح** (مرة واحدة فقط):
```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out private_key.pem
openssl rsa -pubout -in private_key.pem -out public_key.pem
```
2) **إنشاء payload**:
```bash
echo -n "HAMZA_ACCOUNT|<MachineGuid>" > payload.txt
```
3) **توقيع الـpayload**:
```bash
openssl dgst -sha256 -sign private_key.pem payload.txt | openssl base64 -A > signature.b64
```
4) **إنشاء ملف الترخيص**:
```bash
payload_b64=$(openssl base64 -A -in payload.txt)
sig_b64=$(cat signature.b64)
echo "$payload_b64.$sig_b64" > license.dat
```

#### كيفية جلب MachineGuid (ويندوز)
```powershell
reg query "HKLM\SOFTWARE\Microsoft\Cryptography" /v MachineGuid
```

#### سكربت جاهز لجلب MachineGuid تلقائيًا
```
powershell -ExecutionPolicy Bypass -File scripts\get_machine_guid.ps1
```

#### سكربت جاهز لتوليد الترخيص
استخدم:
```
powershell -ExecutionPolicy Bypass -File scripts\generate_license.ps1 -PrivateKeyPath .\private_key.pem -OutputPath .\license.dat
```
يمكن تمرير `-MachineGuid` يدويًا إن لزم.
يمكن تمرير مسار OpenSSL إن لم يكن في PATH:
```
powershell -ExecutionPolicy Bypass -File scripts\generate_license.ps1 -PrivateKeyPath .\private_key.pem -OutputPath .\license.dat -OpenSslPath "C:\Program Files\OpenSSL-Win64\bin\openssl.exe"
```
للتحقق التلقائي بعد التوليد:
```
powershell -ExecutionPolicy Bypass -File scripts\generate_license.ps1 -PrivateKeyPath .\private_key.pem -OutputPath .\license.dat -Verify
```
التحقق يستخدم المفتاح العام المستخرج من المفتاح الخاص ويتأكد أن التوقيع صالح قبل حفظ الملف.

### تحديثات الترخيص (2026-03-01)
- تم إصلاح توليد التوقيع لمنع تلف البايتات عند تمرير النتائج عبر الـ pipeline في PowerShell.
- التوقيع الآن يُكتب ثنائيًا ثم يُحوّل إلى Base64 داخل السكربت لضمان طول توقيع RSA الصحيح (384 بايت لمفتاح 3072).
- إضافة خيار `-Verify` للتحقق من صحة التوقيع أثناء التوليد.

#### ملاحظات مهمة
- العميل لا يحتاج OpenSSL أو مفاتيح خاصة. فقط ضع `license.dat` في مجلد البرنامج.
- `license.dat` مربوط بجهاز واحد عبر `MachineGuid` ولن يعمل على جهاز آخر.

### الملفات المتأثرة
- `account/src/main/java/com/hamza/account/trial/TrialManager.java`
- `scripts/generate_license.ps1`

### ملاحظات تشغيل
1. إذا كان ملف النسخة التجريبية غير موجود بعد أول تشغيل، يتم منع فتح البرنامج.
2. أي اختلاف بين بيانات الملف وDB يعتبر محاولة تلاعب.
3. لا يمكن تشغيل النسخة التجريبية على جهاز مختلف.
