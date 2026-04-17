سأقوم بإنشاء ملف توثيق شامل يلخّص ما تم إنجازه في المرحلتين 1 و 2، وما تبقى من مراحل، مع معلومات تفصيلية تفيد في المتابعة المستقبلية.

```markdown
# 📘 توثيق نظام الورديات (User Shifts System)

> آخر تحديث: 2026-04-17
> المسؤول: فريق التطوير
> الحالة: **المرحلة 2 مكتملة** — قيد الاختبار

---

## 📑 جدول المحتويات

1. [نظرة عامة](#نظرة-عامة)
2. [البنية المعمارية](#البنية-المعمارية)
3. [المرحلة 1 — الإصلاحات العاجلة ✅](#المرحلة-1--الإصلاحات-العاجلة-)
4. [المرحلة 2 — ربط الوردية بالحركات المالية ✅](#المرحلة-2--ربط-الوردية-بالحركات-المالية-)
5. [المراحل المتبقية](#المراحل-المتبقية)
6. [قاعدة البيانات](#قاعدة-البيانات)
7. [دليل الاختبار](#دليل-الاختبار)
8. [ملاحظات ومخاطر معروفة](#ملاحظات-ومخاطر-معروفة)

---

## نظرة عامة

نظام إدارة ورديات المستخدمين (Cashier Shifts) يتيح:

- **فتح وردية** بـ رصيد افتتاحي في بداية اليوم/الدوام.
- **تتبع الحركات المالية** خلال فترة الوردية (مبيعات، مرتجعات، مصروفات).
- **غلق الوردية** بـ رصيد ختامي مع حساب الفرق آلياً (عجز/زيادة/مطابق).
- **تقارير** لحظية (X-Report) وعند الغلق (Z-Report).

---

## البنية المعمارية
```

┌────────────────────────────────────────────────────────────┐
│                   UserShiftController                       │
│              (JavaFX + FXML - Presentation)                 │
└──────────────────────────┬─────────────────────────────────┘
│ uses
▼
┌────────────────────────────────────────────────────────────┐
│                    UserShiftService                         │
│                  (Business Logic Layer)                     │
│  - openShift / closeShift                                   │
│  - getCurrentShiftSummary (X-Report)                        │
└──────────────────────────┬─────────────────────────────────┘
│ uses
▼
┌────────────────────────────────────────────────────────────┐
│                    UserShiftDao                             │
│                 (Data Access Layer)                         │
│  - CRUD + calculateShiftSummary (Time-based)                │
└──────────────────────────┬─────────────────────────────────┘
│ SQL
▼
┌────────────────────────────────────────────────────────────┐
│                  MySQL: user_shifts                         │
│  + total_sales, total_sales_re, expenses_details, ...       │
└────────────────────────────────────────────────────────────┘
```
### الملفات الرئيسية

| الطبقة | المسار |
|-------|--------|
| Domain | `account/src/main/java/com/hamza/account/model/domain/UserShift.java` |
| Domain | `account/src/main/java/com/hamza/account/model/domain/ShiftSummary.java` *(جديد في المرحلة 2)* |
| DAO | `account/src/main/java/com/hamza/account/model/dao/UserShiftDao.java` |
| Service | `account/src/main/java/com/hamza/account/service/UserShiftService.java` |
| Controller | `account/src/main/java/com/hamza/account/controller/users/UserShiftController.java` |
| View (FXML) | `account/src/main/resources/com/hamza/account/view/user-shift-view.fxml` |
| Session | `account/src/main/java/com/hamza/account/session/ShiftContext.java` *(جديد)* |
| Session | `account/src/main/java/com/hamza/account/session/ShiftContextLoader.java` *(جديد)* |
| SQL Migration | `docs/scripts/main/V018_user_shifts_summary.sql` *(جديد)* |

---

## المرحلة 1 — الإصلاحات العاجلة ✅

**الهدف:** تنظيف الكود الحالي وإصلاح الثغرات الأمنية دون تغيير السلوك الوظيفي.

### ما تم إنجازه

| # | المهمة | الحالة | الملف |
|---|--------|:------:|-------|
| 1 | توحيد `isOpen` كـ `BooleanProperty` متسقاً مع باقي الـ properties | ✅ | `UserShift.java` |
| 2 | إزالة تعارض `@AllArgsConstructor` مع منشئ `UserShift(int userId)` | ✅ | `UserShift.java` |
| 3 | جعل جميع الـ properties `final` (لا تُعاد تهيئتها إلا عبر `.set()`) | ✅ | `UserShift.java` |
| 4 | إصلاح ثغرة **SQL Injection** في `hasOpenShift` عبر `PreparedStatement` | ✅ | `UserShiftDao.java` |
| 5 | إضافة `LIMIT 1 ORDER BY open_time DESC` في `getOpenShiftByUserId` احترازياً | ✅ | `UserShiftDao.java` |
| 6 | تحويل أسماء الأعمدة إلى `static final` | ✅ | `UserShiftDao.java` |
| 7 | معالجة `getOpenTime() == null` عند `insert` | ✅ | `UserShiftDao.java` |
| 8 | **Validation** على `userId` والرصيد (لا يقبل سالب) في الـ Service | ✅ | `UserShiftService.java` |
| 9 | **دمج** ملاحظات الفتح مع الغلق بدل استبدالها | ✅ | `UserShiftService.java` |
| 10 | استخدام `isBlank()` بدل `isEmpty()` | ✅ | `UserShiftService.java` |
| 11 | إزالة استيراد Full-Qualified-Name واستخدام `import` الصحيح | ✅ | `UserShiftController.java` |
| 12 | ربط أعمدة الجدول بـ properties الدومين مباشرة (بدلاً من إنشاء نسخ جديدة) | ✅ | `UserShiftController.java` |
| 13 | `parseBalance` يدعم الفاصلة العربية `٫` والفاصلة العادية `,` والمسافات | ✅ | `UserShiftController.java` |
| 14 | إضافة `safeTrim` لتنظيف الملاحظات | ✅ | `UserShiftController.java` |
| 15 | Validation إضافي على الرصيد قبل إرسال الطلب للـ Service | ✅ | `UserShiftController.java` |

### ما لم يُنفّذ ضمن المرحلة 1 (مؤجَّل)

- ❌ استخدام `Clock` قابل للحقن (Injectable Clock) لتسهيل الاختبارات.
  → **سبب التأجيل:** يحتاج إعادة هيكلة الـ Service كـ class بدل record.
- ❌ تغليف عملية الغلق في Transaction كامل.
  → **سبب التأجيل:** طبقة `AbstractDao` الحالية لا توفر API للـ transactions — يحتاج تعديل في `controlsfx`.

---

## المرحلة 2 — ربط الوردية بالحركات المالية ✅

**الهدف:** جعل الوردية تعكس الحركة المالية الفعلية، لا أن تكون مجرد طابع زمني.

### قرار تصميمي مهم

اتبعنا **Time-Based Approach** بدلاً من **Shift-Id Propagation Approach**:

| النهج | الوصف | الميزة | العيب |
|-------|-------|--------|-------|
| **Time-Based ✅ المختار** | حساب الإجماليات حسب `user_id` + نطاق `date_insert BETWEEN openTime AND closeTime` | لا تغييرات على جداول أخرى، سريع التطبيق | يعتمد على دقة الأوقات، لا يُسجّل حركات قبل/بعد نطاق الوردية |
| Shift-Id Propagation | إضافة عمود `shift_id` في جميع جداول الحركات | ربط صريح ودقيق | يحتاج migration واسع لكل الجداول + تعديل كل DAOs |

> سننتقل إلى **Shift-Id Propagation** لاحقاً في **المرحلة 4** عند الحاجة لضمان دقة 100%.

### ما تم إنجازه

#### ✅ قاعدة البيانات
- إنشاء ملف migration جديد `V018_user_shifts_summary.sql`.
- إضافة 8 أعمدة جديدة لجدول `user_shifts`:
  - `total_sales`
  - `total_sales_returns`
  - `total_expenses`
  - `total_deposits`
  - `total_withdrawals`
  - `expected_balance`
  - `difference`
  - `invoices_count`
- إضافة فهرسين: `idx_user_shifts_user_open`, `idx_user_shifts_open_time`.

#### ✅ Domain Layer
- إنشاء **`ShiftSummary`** (DTO) يحتوي:
  - جميع الإجماليات + `getExpectedBalance()` + `calculateDifference()`.
- توسيع **`UserShift`** بـ 8 properties جديدة للملخص.

#### ✅ Data Access Layer (DAO)
- إضافة **`calculateShiftSummary(userId, from, to)`** الذي يحسب:
  - إجمالي المبيعات من `total_sales.paid_up`
  - إجمالي مرتجعات المبيعات من `total_sales_re.paid_from_treasury`
  - إجمالي المصروفات من `expenses_details.amount`
  - إجمالي الإيداعات/السحوبات من `treasury_deposit_expenses`
  - عدد الفواتير من `total_sales`
- تحديث `update()` لحفظ جميع حقول الملخص.
- تحديث `map()` مع دوال آمنة `getDoubleSafe` / `getIntSafe` للتوافق مع السجلات القديمة قبل الـ migration.

#### ✅ Service Layer
- تحديث **`closeShift`** ليحسب الملخص تلقائياً ويحفظه.
- إضافة **`getCurrentShiftSummary`** (X-Report) لعرض ملخص لحظي للوردية المفتوحة.

#### ✅ Session/Context
- **`ShiftContext`** (Singleton thread-safe) لتتبع الوردية الحالية خلال جلسة التطبيق.
- **`ShiftContextLoader`** لتحميل الوردية تلقائياً عند الـ login.

#### ✅ Presentation Layer
- تحديث `UserShiftController`:
  - تحميل الملخص اللحظي (X-Report) مع كل `refreshView`.
  - تحديث `ShiftContext` عند الفتح/الغلق.
  - **رسالة تأكيد الغلق** تعرض ملخصاً كاملاً قبل التأكيد.
  - تلوين الفرق: 🟢 مطابق، 🟠 زيادة، 🔴 عجز.
- تحديث `user-shift-view.fxml`:
  - قسم جديد `TitledPane` بعنوان "ملخص الوردية اللحظي (X-Report)" يحوي 6 labels.

### معادلات الحساب
```

الرصيد المتوقع = الرصيد الافتتاحي
+ إجمالي المبيعات النقدية
− إجمالي مرتجعات المبيعات
− إجمالي المصروفات
+ إجمالي الإيداعات
− إجمالي السحوبات

الفرق = الرصيد الفعلي (المُدخل) − الرصيد المتوقع

الفرق > 0  ⇒ زيادة  (orange)
الفرق < 0  ⇒ عجز    (red)
الفرق ≈ 0  ⇒ مطابق  (green)
```
---

## المراحل المتبقية

### 🔹 المرحلة 3 — التقارير والطباعة ⏳

**الأولوية:** 🟡 متوسطة | **الحالة:** لم تبدأ

- [ ] **Z-Report (تقرير غلق الوردية)**: يُطبع تلقائياً عند الغلق
  - بيانات المستخدم والوقت (فتح/غلق)
  - جدول إجماليات (مبيعات/مرتجعات/مصروفات)
  - عدد الفواتير
  - تفصيل طرق الدفع (نقدي/شبكة/آجل)
  - الرصيد المتوقع مقابل الفعلي + الفرق
- [ ] **X-Report (تقرير لحظي)**: زر مستقل لعرض الملخص بدون غلق
- [ ] **تصدير PDF** باستخدام مكتبة الطباعة الحالية في المشروع
- [ ] **تصدير Excel** للورديات
- [ ] **تقرير تجميعي** للورديات خلال فترة زمنية (بفلترة المستخدم/التاريخ)
- [ ] دعم **طباعة حرارية** 80mm للكاشير

**المكونات المطلوبة:**
- `ShiftReportService`
- `ZReportDialog.java` + `z-report-dialog.fxml`
- `XReportDialog.java` + `x-report-dialog.fxml`
- `ShiftReportsController` (للتقارير التجميعية)

---

### 🔹 المرحلة 4 — الصلاحيات والإدارة ⏳

**الأولوية:** 🟡 متوسطة | **الحالة:** لم تبدأ

- [ ] **دور مدير الورديات**:
  - عرض جميع الورديات لكل المستخدمين
  - **Force Close** لوردية مستخدم عالق
  - تعديل/حذف مع تسجيل في Audit Log
- [ ] **منع العمليات** (بيع، شراء، مصروف) إذا لم تكن هناك وردية مفتوحة
  - استخدام `ShiftContext.isOpen()` في `SalesController`, `PurchaseController`, `AddExpensesController`
- [ ] **قفل تلقائي** لورديات اليوم السابق (Scheduled task)
- [ ] **Audit Log** لكل عمليات الوردية في جدول منفصل `user_shifts_audit`
- [ ] **ربط shift_id** صراحةً في جداول الحركات (Shift-Id Propagation)
- [ ] Transactions كاملة (JDBC autoCommit = false)

**المكونات المطلوبة:**
- إضافة `SHIFT_MANAGER` في جدول `permission`
- `ShiftAuditLog.java`
- `V019_user_shifts_audit.sql`
- `V020_add_shift_id_to_transactions.sql`

---

### 🔹 المرحلة 5 — تحسينات UX ⏳

**الأولوية:** 🟢 منخفضة | **الحالة:** لم تبدأ

- [ ] **فلترة جدول السجل** بـ (التاريخ من/إلى، الحالة، الرصيد)
- [ ] **Pagination** إذا تجاوز عدد الورديات حداً
- [ ] **تلوين صفوف الجدول**:
  - أخضر: مفتوحة
  - أحمر: بها عجز
  - برتقالي: بها زيادة
- [ ] **Dashboard صغير**:
  - إجمالي مبيعات اليوم
  - عدد الورديات المفتوحة حالياً
  - آخر 5 ورديات مغلقة
- [ ] **تنبيه نسيان الغلق** عند تسجيل الخروج
- [ ] **اختصارات لوحة المفاتيح**:
  - `Ctrl+Shift+O` ⇒ فتح وردية
  - `Ctrl+Shift+C` ⇒ غلق وردية
  - `F5` ⇒ تحديث
- [ ] تحديث الملخص **لحظياً** عند تغيير `txtCloseBalance` (listener)

---

### 🔹 المرحلة 6 — ميزات متقدمة (اختيارية) ⏳

**الأولوية:** 🟢 منخفضة | **الحالة:** لم تبدأ

- [ ] **Cash In / Cash Out** أثناء الوردية (إيداع/سحب يدوي بسجل منفصل)
- [ ] **عدة صناديق** لنفس المستخدم (رئيسي + فرعي)
- [ ] **Handover** (تحويل وردية لمستخدم آخر دون غلق)
- [ ] **مراقبة لحظية للمدير** — جدول يعرض الورديات المفتوحة الآن مع إجمالياتها
- [ ] **مزامنة متعددة الفروع**
- [ ] **إشعارات تلقائية** عند عجز يتجاوز حداً معيناً
- [ ] **تكامل مع Barcode Scanner** لفتح الدرج النقدي

---

## قاعدة البيانات

### مخطط `user_shifts` بعد المرحلة 2
```
sql
CREATE TABLE user_shifts (
id                  INT AUTO_INCREMENT PRIMARY KEY,
user_id             INT NOT NULL,
open_time           DATETIME NOT NULL,
close_time          DATETIME NULL,
open_balance        DOUBLE DEFAULT 0.0,
close_balance       DOUBLE DEFAULT 0.0,

    -- أعمدة المرحلة 2
    total_sales         DOUBLE DEFAULT 0.0,
    total_sales_returns DOUBLE DEFAULT 0.0,
    total_expenses      DOUBLE DEFAULT 0.0,
    total_deposits      DOUBLE DEFAULT 0.0,
    total_withdrawals   DOUBLE DEFAULT 0.0,
    expected_balance    DOUBLE DEFAULT 0.0,
    difference          DOUBLE DEFAULT 0.0,
    invoices_count      INT    DEFAULT 0,

    is_open             BOOLEAN DEFAULT TRUE,
    notes               TEXT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_shifts_user_open (user_id, is_open),
    INDEX idx_user_shifts_open_time (open_time)
);
```
### الجداول المستخدمة في حساب الملخص

| الجدول | العمود المستخدم | الغرض |
|-------|-----------------|-------|
| `total_sales` | `paid_up` | المبيعات النقدية |
| `total_sales_re` | `paid_from_treasury` | مرتجعات المبيعات |
| `expenses_details` | `amount` | المصروفات |
| `treasury_deposit_expenses` | `amount` + `deposit_or_expenses` | إيداع (=1) / سحب (=2) |

---

## دليل الاختبار

### قبل التشغيل
1. تشغيل migration:
   ```bash
   cd docs/scripts/main
   mysql -u root -p account_system_db < V018_user_shifts_summary.sql
   ```

### سيناريوهات الاختبار

| # | السيناريو | النتيجة المتوقعة |
|---|-----------|------------------|
| 1 | فتح وردية برصيد 1000 | تُنشأ سجلة `is_open=TRUE` |
| 2 | محاولة فتح وردية ثانية لنفس المستخدم | خطأ "يوجد وردية مفتوحة بالفعل" |
| 3 | فتح وردية برصيد سالب | خطأ "لا يمكن أن يكون الرصيد بالسالب" |
| 4 | إنشاء فاتورة مبيعات أثناء الوردية بـ `paid_up=500` | X-Report يعرض `totalSales = 500` |
| 5 | غلق الوردية برصيد 1500 | `expected_balance=1500`, `difference=0`, "مطابق" |
| 6 | غلق الوردية برصيد 1400 | `difference=-100`, يظهر "عجز" أحمر |
| 7 | غلق الوردية برصيد 1600 | `difference=+100`, يظهر "زيادة" برتقالي |
| 8 | تأكيد dialog الغلق | يعرض ملخصاً كاملاً قبل الحفظ |

---

## ملاحظات ومخاطر معروفة

### ⚠️ افتراضات تحتاج تأكيداً
1. **`treasury_deposit_expenses.deposit_or_expenses`**:
    - `1` = إيداع، `2` = سحب (حسب الكود الحالي) — **يجب تأكيد** من قاعدة البيانات الفعلية.
2. **نطاق الخزينة**: الملخص الحالي يأخذ **كل** العمليات للمستخدم دون فلترة `treasury_id`. إذا كان المستخدم يعمل على صناديق متعددة، يحتاج تعديلاً.
3. **MySQL 8+**: الـ migration يستخدم `ADD COLUMN IF NOT EXISTS` و `CREATE INDEX IF NOT EXISTS`. إذا كان الإصدار أقدم، يحتاج إعادة صياغة.

### 🐛 قيود حالية
- لا Transaction حقيقي عند الغلق ⇒ قد يحدث عدم اتساق لو فشل `update` بعد حساب الملخص.
- لا حماية من **تعديل `date_insert`** يدوياً في الحركات — قد يُفسد حساب الملخص.
- لا يوجد refresh تلقائي لـ X-Report عند تغيير البيانات من نافذة أخرى.

### 🔐 مسائل أمنية
- `ShiftContext` عام لكل الجلسة — مناسب لبرنامج Desktop، لكن يجب الانتباه في حال تشغيل عدة مستخدمين على نفس الجهاز.
- لا توجد صلاحيات بعد لفتح/غلق ورديات الآخرين — **سيُعالج في المرحلة 4**.

---

## الخلاصة

| المرحلة | الحالة | التقدير الزمني المتبقي |
|:-------:|:------:|:---------------------:|
| 1 — إصلاحات عاجلة | ✅ مكتمل | — |
| 2 — ربط الحركات المالية | ✅ مكتمل | — |
| 3 — التقارير والطباعة | ⏳ لم يبدأ | ~3-5 أيام |
| 4 — الصلاحيات والإدارة | ⏳ لم يبدأ | ~4-6 أيام |
| 5 — تحسينات UX | ⏳ لم يبدأ | ~2-3 أيام |
| 6 — ميزات متقدمة | ⏳ اختيارية | — |

---

### 📞 للمتابعة

- راجع قائمة TODO في كل مرحلة.
- التزم بالترتيب: **3 ⇒ 4 ⇒ 5 ⇒ 6** لتحقيق أقصى استفادة.
- بعد كل مرحلة: حدّث هذا الملف بـ ✅ والتاريخ.
```


### 📌 ملاحظات حول هذا التوثيق

1. **حُفظ الملف في:** `docs/shifts.md` (نفس المسار الحالي — استبدل المحتوى القديم بالجديد).
2. **هيكله يتبع أفضل الممارسات**:
   - نظرة عامة → تفاصيل → حالة حالية → ما تبقى → مخاطر.
3. **قابل للتحديث التدريجي**: كل مرحلة جديدة تُضاف بنفس النمط + علامة ✅.

### 💡 اقتراحات للخطوة التالية

هل تريد:
1. **البدء في المرحلة 3** (التقارير والطباعة)؟ — **الأكثر فائدة فورياً**.
2. أو إعداد **ملف `CHANGELOG.md`** منفصل لتتبع الإصدارات؟
3. أو إضافة **diagrams (PlantUML)** لتوضيح تدفق الوردية بصرياً في التوثيق؟