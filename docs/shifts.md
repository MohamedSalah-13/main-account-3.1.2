# 📘 توثيق نظام الورديات (User Shifts System)

> **آخر تحديث:** 2026-04-18
> **الحالة الحالية:** المرحلة 1 ✅ — المرحلة 2 ✅ — المرحلة 3 🟡 (~60%) — المرحلة 4/B ✅
> **المسار:** `docs/shifts.md`

---

## 📑 جدول المحتويات

1. [نظرة عامة](#نظرة-عامة)
2. [البنية المعمارية](#البنية-المعمارية)
3. [الملفات الرئيسية](#الملفات-الرئيسية)
4. [المرحلة 1 — الإصلاحات العاجلة ✅](#المرحلة-1--الإصلاحات-العاجلة-)
5. [المرحلة 2 — ربط الوردية بالحركات المالية ✅](#المرحلة-2--ربط-الوردية-بالحركات-المالية-)
6. [المرحلة 4/B — حماية العمليات (Shift Guard) ✅](#المرحلة-4b--حماية-العمليات-shift-guard-)
7. [المراحل المتبقية](#المراحل-المتبقية)
8. [قاعدة البيانات](#قاعدة-البيانات)
9. [دليل الاختبار](#دليل-الاختبار)
10. [ملاحظات ومخاطر معروفة](#ملاحظات-ومخاطر-معروفة)
11. [🗺️ خارطة طريق استئناف العمل](#-خارطة-طريق-استئناف-العمل)
12. [سجل التغييرات (Changelog)](#سجل-التغييرات-changelog)

---

## نظرة عامة

نظام إدارة ورديات المستخدمين (Cashier Shifts) يتيح:

- **فتح وردية** برصيد افتتاحي في بداية اليوم/الدوام.
- **تتبع الحركات المالية** خلال فترة الوردية (مبيعات، مرتجعات، مصروفات، إيداعات، سحوبات).
- **غلق الوردية** برصيد ختامي مع حساب الفرق آلياً (عجز/زيادة/مطابق).
- **تقارير** لحظية (X-Report) وعند الغلق (Z-Report).
- **حماية العمليات** — منع البيع/الشراء/المصروفات بدون وردية مفتوحة.

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
│              UserShiftService / ShiftReportService          │
│                   (Business Logic Layer)                    │
│  - openShift / closeShift                                   │
│  - getCurrentShiftSummary (X-Report)                        │
│  - buildXReport / buildZReport                              │
└──────────────────────────┬─────────────────────────────────┘
                           │ uses
                           ▼
┌────────────────────────────────────────────────────────────┐
│                     UserShiftDao                            │
│                  (Data Access Layer)                        │
│  - CRUD + calculateShiftSummary (Time-based)                │
└──────────────────────────┬─────────────────────────────────┘
                           │ SQL
                           ▼
┌────────────────────────────────────────────────────────────┐
│                  MySQL: user_shifts                         │
│  + total_sales, total_sales_re, expenses_details,           │
│    treasury_deposit_expenses                                │
└────────────────────────────────────────────────────────────┘

              ┌─────────────────────────┐
              │      ShiftContext        │  ◄── حارس عام يستدعى من
              │   (Session Singleton)    │      POS / Invoice / Expenses
              │  + requireOpenShift()    │      قبل أي عملية مالية
              └─────────────────────────┘
```

---

## الملفات الرئيسية

| الطبقة        | المسار |
|---------------|--------|
| Domain        | `account/src/main/java/com/hamza/account/model/domain/UserShift.java` |
| Domain        | `account/src/main/java/com/hamza/account/model/domain/ShiftSummary.java` |
| DAO           | `account/src/main/java/com/hamza/account/model/dao/UserShiftDao.java` |
| Service       | `account/src/main/java/com/hamza/account/service/UserShiftService.java` |
| Service       | `account/src/main/java/com/hamza/account/service/ShiftReportService.java` |
| Controller    | `account/src/main/java/com/hamza/account/controller/users/UserShiftController.java` |
| View (FXML)   | `account/src/main/resources/com/hamza/account/view/user-shift-view.fxml` |
| Session       | `account/src/main/java/com/hamza/account/session/ShiftContext.java` |
| Session       | `account/src/main/java/com/hamza/account/session/ShiftContextLoader.java` |
| SQL Migration | `docs/scripts/main/V018_user_shifts_summary.sql` |

---

## المرحلة 1 — الإصلاحات العاجلة ✅

**الهدف:** تنظيف الكود الحالي وإصلاح الثغرات الأمنية دون تغيير السلوك الوظيفي.

| # | المهمة | الحالة | الملف |
|---|--------|:------:|-------|
| 1 | توحيد `isOpen` كـ `BooleanProperty` متسقاً مع باقي الـ properties | ✅ | `UserShift.java` |
| 2 | إزالة تعارض `@AllArgsConstructor` مع منشئ `UserShift(int userId)` | ✅ | `UserShift.java` |
| 3 | جعل جميع الـ properties `final` (لا تُعاد تهيئتها إلا عبر `.set()`) | ✅ | `UserShift.java` |
| 4 | إصلاح ثغرة **SQL Injection** في `hasOpenShift` عبر `PreparedStatement` | ✅ | `UserShiftDao.java` |
| 5 | إضافة `LIMIT 1 ORDER BY open_time DESC` في `getOpenShiftByUserId` | ✅ | `UserShiftDao.java` |
| 6 | تحويل أسماء الأعمدة إلى `static final` | ✅ | `UserShiftDao.java` |
| 7 | معالجة `getOpenTime() == null` عند `insert` | ✅ | `UserShiftDao.java` |
| 8 | **Validation** على `userId` والرصيد (لا يقبل سالب) في الـ Service | ✅ | `UserShiftService.java` |
| 9 | **دمج** ملاحظات الفتح مع الغلق بدل استبدالها | ✅ | `UserShiftService.java` |
| 10 | استخدام `isBlank()` بدل `isEmpty()` | ✅ | `UserShiftService.java` |
| 11 | إزالة استيراد Full-Qualified-Name واستخدام `import` الصحيح | ✅ | `UserShiftController.java` |
| 12 | ربط أعمدة الجدول بـ properties الدومين مباشرة | ✅ | `UserShiftController.java` |
| 13 | `parseBalance` يدعم الفاصلة العربية `٫` والفاصلة العادية `,` والمسافات | ✅ | `UserShiftController.java` |
| 14 | إضافة `safeTrim` لتنظيف الملاحظات | ✅ | `UserShiftController.java` |
| 15 | Validation إضافي على الرصيد قبل إرسال الطلب للـ Service | ✅ | `UserShiftController.java` |

### ما لم يُنفّذ (مؤجّل)

- ❌ `Clock` قابل للحقن (Injectable Clock) — يحتاج إعادة هيكلة الـ Service كـ class بدل record.
- ❌ تغليف عملية الغلق في Transaction كامل — يحتاج تعديل `AbstractDao` في `controlsfx`.

---

## المرحلة 2 — ربط الوردية بالحركات المالية ✅

**الهدف:** جعل الوردية تعكس الحركة المالية الفعلية، لا أن تكون مجرد طابع زمني.

### القرار التصميمي

اختير **Time-Based Approach** بدل **Shift-Id Propagation**:

| النهج | الوصف | الميزة | العيب |
|-------|-------|--------|-------|
| **Time-Based ✅ المختار** | حساب الإجماليات حسب `user_id` + نطاق `date_insert BETWEEN openTime AND closeTime` | لا تغييرات على جداول أخرى، سريع التطبيق | يعتمد على دقة الأوقات |
| Shift-Id Propagation | إضافة عمود `shift_id` في كل جداول الحركات | ربط صريح ودقيق | migration واسع + تعديل كل DAOs |

> سننتقل إلى **Shift-Id Propagation** في المرحلة **4/E** عند الحاجة لدقة 100%.

### ما تم إنجازه

#### ✅ قاعدة البيانات
- ملف migration جديد: `V018_user_shifts_summary.sql`.
- إضافة 8 أعمدة: `total_sales`, `total_sales_returns`, `total_expenses`, `total_deposits`, `total_withdrawals`, `expected_balance`, `difference`, `invoices_count`.
- إضافة فهرسين: `idx_user_shifts_user_open`, `idx_user_shifts_open_time`.

#### ✅ Domain
- `ShiftSummary` (DTO) مع `getExpectedBalance()` و `calculateDifference()`.
- توسيع `UserShift` بـ 8 properties جديدة للملخص.

#### ✅ DAO
- `calculateShiftSummary(userId, from, to)` يحسب:
   - المبيعات من `total_sales.paid_up`
   - مرتجعات المبيعات من `total_sales_re.paid_from_treasury`
   - المصروفات من `expenses_details.amount`
   - الإيداعات/السحوبات من `treasury_deposit_expenses`
   - عدد الفواتير من `total_sales`
- تحديث `update()` لحفظ جميع حقول الملخص.
- `map()` مع `getDoubleSafe` / `getIntSafe` للتوافق مع السجلات القديمة.

#### ✅ Service
- `closeShift` يحسب الملخص تلقائياً ويحفظه.
- `getCurrentShiftSummary` — X-Report لحظي.

#### ✅ Session/Context
- `ShiftContext` (Singleton thread-safe).
- `ShiftContextLoader` لتحميل الوردية تلقائياً عند الـ login.

#### ✅ Presentation
- تحميل X-Report مع كل `refreshView`.
- رسالة تأكيد الغلق تعرض ملخصاً كاملاً.
- تلوين الفرق: 🟢 مطابق، 🟠 زيادة، 🔴 عجز.
- `TitledPane` جديد "ملخص الوردية اللحظي (X-Report)" في الـ FXML.

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

## المرحلة 4/B — حماية العمليات (Shift Guard) ✅

**الهدف:** منع تنفيذ أي عملية مالية (بيع/شراء/مرتجع/مصروف) بدون وجود وردية مفتوحة.

### القرار المعماري — Central Guard

بدل تكرار نفس التحقق في كل controller، نقطة واحدة مركزية:
- `ShiftContext.requireOpenShift()` — تعرض التنبيه وترجع `false` عند عدم وجود وردية.
- كل controller يستدعيها في **أول سطر** داخل دالة الحفظ.
- مفتاح طوارئ: `ShiftContext.setEnforceShiftRequired(false)` لتعطيل الإلزام مؤقتاً.

### ما تم إنجازه

| # | المكوّن | التغيير | الحالة |
|---|--------|---------|:------:|
| 1 | `ShiftContext` | `requireOpenShift()` + `isEnforceShiftRequired()` + `setEnforceShiftRequired()` | ✅ |
| 2 | `AddExpensesController.insertData()` | استدعاء `requireOpenShift()` في بداية الدالة | ✅ |
| 3 | `PosController.saveInvoice()` | استدعاء `requireOpenShift()` قبل أي معالجة | ✅ |
| 4 | `BuyController2.saveInvoice(boolean print)` | استدعاء `requireOpenShift()` قبل التحقق من الجدول | ✅ |
| 5 | `PosController.refreshShiftGuardUi()` | تعطيل زر الدفع بصرياً عند عدم وجود وردية + Tooltip | ✅ |

### الملفات المعدّلة

```
account/src/main/java/com/hamza/account/session/ShiftContext.java
account/src/main/java/com/hamza/account/controller/pos/PosController.java
account/src/main/java/com/hamza/account/controller/invoice/BuyController2.java
account/src/main/java/com/hamza/account/controller/others/AddExpensesController.java
```

### نموذج الاستخدام

```java
// في أي controller يحتوي على عملية مالية:
private void saveSomething() {
    try {
        if (!ShiftContext.requireOpenShift()) {
            return; // يعرض التنبيه تلقائياً ويوقف العملية
        }
        // ... تابع الحفظ العادي
    } catch (Exception e) {
        ...
    }
}
```

### قيود معروفة

- ⚠️ **`btnPay` في POS لا يتحدث تلقائياً** عند فتح/غلق الوردية من نافذة أخرى.
   - الحل: ربطه بـ `DataPublisher` (مؤجّل — انظر [خارطة الطريق](#-خارطة-طريق-استئناف-العمل)).
- ⚠️ **Controllers أخرى لم تُفحص بعد:** `convert_treasury/`, `convert_stock/`, عمليات الإيداع.
   - إن وُجدت بها عمليات مالية، يجب إضافة الحارس.

---

## المراحل المتبقية

### 🔹 المرحلة 3 — التقارير والطباعة 🟡 (~60%)

**الحالة الحقيقية:**

| البند | الحالة |
|-------|:------:|
| `ShiftReportService.buildXReport(userId)` | ✅ |
| `ShiftReportService.buildZReport(shiftId)` | ✅ |
| زر X-Report في `UserShiftController` | ✅ |
| طباعة Z-Report تلقائياً عند الغلق | ✅ |
| `buildAggregateReport(from, to, userId)` | ❌ يرجع `null` |
| `UserShiftDao.getShiftsBetween(...)` | ❌ غير موجود |
| شاشة تقرير تجميعي للفترات | ❌ |
| تصدير Excel (Apache POI) | ❌ |
| طباعة حرارية 80mm | ❌ |

**الخطوات المتبقية:**
1. إضافة `UserShiftDao.getShiftsBetween(from, to, userId)`.
2. إكمال `ShiftReportService.buildAggregateReport` (إزالة الـ `return null`).
3. شاشة `ShiftReportsController` + FXML بفلترة المستخدم والتاريخ.
4. تصدير Excel.

---

### 🔹 المرحلة 4 — الصلاحيات والإدارة ⏳ (جزئياً)

| المرحلة الفرعية | الحالة | الوصف |
|:---------------:|:------:|-------|
| 4/A | ⏳ لم يبدأ | دور `SHIFT_MANAGER` + Force Close |
| **4/B** | ✅ **مكتمل** | حماية العمليات بالحارس |
| 4/C | ⏳ لم يبدأ | Audit Log في `user_shifts_audit` |
| 4/D | ⏳ لم يبدأ | Transactions كاملة (JDBC autoCommit=false) |
| 4/E | ⏳ لم يبدأ | Shift-Id Propagation في جداول الحركات |
| 4/F | ⏳ لم يبدأ | قفل تلقائي لورديات اليوم السابق (Scheduled task) |

**مكونات مطلوبة للاستكمال:**
- `UserPermissionType.SHIFT_MANAGER`
- `ShiftAuditLog.java` (Domain)
- `V019_user_shifts_audit.sql`
- `V020_add_shift_id_to_transactions.sql`
- `AdminShiftsController` + FXML

---

### 🔹 المرحلة 5 — تحسينات UX ⏳

- [ ] فلترة جدول السجل (تاريخ من/إلى، الحالة، الرصيد).
- [ ] Pagination عند تجاوز حد معين.
- [ ] تلوين صفوف الجدول: أخضر (مفتوحة) / أحمر (عجز) / برتقالي (زيادة).
- [ ] Dashboard صغير (مبيعات اليوم، ورديات مفتوحة، آخر 5 مغلقة).
- [ ] تنبيه نسيان الغلق عند تسجيل الخروج.
- [ ] اختصارات لوحة المفاتيح: `Ctrl+Shift+O` فتح، `Ctrl+Shift+C` غلق، `F5` تحديث.
- [ ] تحديث الملخص لحظياً عند تغيير `txtCloseBalance` (listener).
- [ ] **تحديث تلقائي لـ `btnPay` في POS** عبر `DataPublisher` (انبعاث حدث عند فتح/غلق الوردية).

---

### 🔹 المرحلة 6 — ميزات متقدمة ⏳ (اختيارية)

- [ ] Cash In / Cash Out أثناء الوردية (سجل منفصل).
- [ ] عدة صناديق لنفس المستخدم (رئيسي + فرعي).
- [ ] Handover (تحويل وردية لمستخدم آخر دون غلق).
- [ ] مراقبة لحظية للمدير لجميع الورديات المفتوحة.
- [ ] مزامنة متعددة الفروع.
- [ ] إشعارات تلقائية عند تجاوز العجز حداً معيناً.
- [ ] تكامل مع Barcode Scanner لفتح الدرج النقدي.

---

## قاعدة البيانات

### مخطط `user_shifts` بعد المرحلة 2

```sql
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
|--------|-----------------|-------|
| `total_sales` | `paid_up` | المبيعات النقدية |
| `total_sales_re` | `paid_from_treasury` | مرتجعات المبيعات |
| `expenses_details` | `amount` | المصروفات |
| `treasury_deposit_expenses` | `amount` + `deposit_or_expenses` | إيداع (=1) / سحب (=2) |

---

## دليل الاختبار

### قبل التشغيل

```bash
cd docs/scripts/main
mysql -u root -p account_system_db < V018_user_shifts_summary.sql
```

### سيناريوهات الاختبار

#### المرحلة 1 + 2 (منطق الوردية)

| # | السيناريو | النتيجة المتوقعة |
|---|-----------|-------------------|
| 1 | فتح وردية برصيد 1000 | تُنشأ سجلة `is_open=TRUE` |
| 2 | محاولة فتح وردية ثانية لنفس المستخدم | خطأ "يوجد وردية مفتوحة بالفعل" |
| 3 | فتح وردية برصيد سالب | خطأ "لا يمكن أن يكون الرصيد بالسالب" |
| 4 | إنشاء فاتورة مبيعات برصيد `paid_up=500` أثناء الوردية | X-Report يعرض `totalSales = 500` |
| 5 | غلق الوردية برصيد 1500 (متوقع 1500) | `difference=0`, "مطابق ✅" |
| 6 | غلق الوردية برصيد 1400 | `difference=-100`, "عجز" 🔴 |
| 7 | غلق الوردية برصيد 1600 | `difference=+100`, "زيادة" 🟠 |
| 8 | تأكيد dialog الغلق | يعرض ملخصاً كاملاً قبل الحفظ |

#### المرحلة 4/B (Shift Guard)

| # | السيناريو | النتيجة المتوقعة |
|---|-----------|-------------------|
| 9 | دخول البرنامج بدون فتح وردية → فتح شاشة POS | `btnPay` معطل + Tooltip "افتح وردية أولاً..." |
| 10 | الضغط رغم ذلك (اختصار مثلاً) | رسالة "لا يمكن إتمام العملية!..." |
| 11 | محاولة حفظ فاتورة من `BuyController2` بدون وردية | نفس الرسالة — لا يُحفظ شيء |
| 12 | محاولة إضافة مصروف بدون وردية | نفس الرسالة |
| 13 | فتح وردية ثم إعادة السيناريوهات 9-12 | كل العمليات تعمل طبيعياً |
| 14 | `ShiftContext.setEnforceShiftRequired(false)` ثم حفظ بدون وردية | يُحفظ (وضع التعطيل) |

---

## ملاحظات ومخاطر معروفة

### ⚠️ افتراضات تحتاج تأكيداً

1. **`treasury_deposit_expenses.deposit_or_expenses`**: `1` = إيداع، `2` = سحب — **يجب تأكيد** من قاعدة البيانات.
2. **نطاق الخزينة**: الملخص يأخذ **كل** العمليات للمستخدم دون فلترة `treasury_id` (مشكلة إن كان المستخدم يعمل على صناديق متعددة).
3. **MySQL 8+**: الـ migration يستخدم `ADD COLUMN IF NOT EXISTS` — يحتاج إعادة صياغة للإصدارات الأقدم.

### 🐛 قيود حالية

- لا Transaction حقيقي عند الغلق — قد يحدث عدم اتساق لو فشل `update` بعد حساب الملخص.
- لا حماية من تعديل `date_insert` يدوياً — قد يُفسد حساب الملخص.
- لا refresh تلقائي لـ X-Report عند تغيير البيانات من نافذة أخرى.
- **الحارس لا يلتقط تغيير الوردية تلقائياً** في الشاشات المفتوحة (يعمل عند فتح الشاشة فقط).

### 🔐 مسائل أمنية

- `ShiftContext` عام لكل الجلسة — مناسب لبرنامج Desktop.
- لا توجد صلاحيات بعد لفتح/غلق ورديات الآخرين → **سيُعالج في المرحلة 4/A**.

---

## 🗺️ خارطة طريق استئناف العمل

> **هذا القسم خُصّص للعودة للعمل لاحقاً بدون فقد السياق.**

### ✅ ما هو جاهز ويعمل الآن

- فتح/غلق ورديات مع رصيد وملاحظات.
- حساب ملخص مالي تلقائي (مبيعات/مرتجعات/مصروفات/إيداعات/سحوبات).
- X-Report (لحظي) + Z-Report (تلقائي عند الغلق).
- `ShiftContext` + `ShiftContextLoader` (يحمّل وردية المستخدم عند الـ login).
- **حارس العمليات** في: `POS`, `Invoice (BuyController2)`, `AddExpenses`.
- تعطيل زر الدفع في POS بصرياً عند عدم وجود وردية.

### 🎯 الخطوات التالية بالترتيب

#### 🥇 أولوية 1 — استكمال المرحلة 4/B (~1 ساعة)

افحص بقية نقاط الحفظ المالي وأضف الحارس:

```bash
# أوامر بحث مفيدة:
grep -rn "confirmSave\|\.insert(" \
  account/src/main/java/com/hamza/account/controller/ \
  --include="*.java"
```

**المرشحون:**
- [ ] `controller/convert_treasury/*` — تحويلات الخزينة.
- [ ] `controller/convert_stock/*` — تحويلات المخزون.
- [ ] أي `*Deposit*Controller` أو `*Transfer*Controller`.

**النمط:** في بداية دالة الحفظ:
```java
if (!ShiftContext.requireOpenShift()) { return; }
```

#### 🥈 أولوية 2 — تحديث تلقائي لـ `btnPay` في POS (~30 دقيقة)

اربط `refreshShiftGuardUi` بحدث جديد في `DataPublisher`:

```java
// في DataPublisher:
@Getter private final Publisher<Boolean> publisherShiftChanged = new Publisher<>();

// في UserShiftService بعد openShift/closeShift:
dataPublisher.getPublisherShiftChanged().notifyObservers(true);

// في PosController.initialize():
dataPublisher.getPublisherShiftChanged()
    .addObserver(msg -> Platform.runLater(this::refreshShiftGuardUi));
```

#### 🥉 أولوية 3 — إكمال المرحلة 3 (~3-5 أيام)

1. `UserShiftDao.getShiftsBetween(from, to, userId)`.
2. إكمال `ShiftReportService.buildAggregateReport`.
3. `ShiftReportsController` + FXML للتقرير التجميعي.
4. تصدير Excel (Apache POI موجود في `pom.xml`).

#### 🏅 أولوية 4 — المرحلة 4/A (Force Close) (~1-2 يوم)

- إضافة `UserPermissionType.SHIFT_MANAGER` في `type/UserPermissionType.java`.
- شاشة `AdminShiftsController` لعرض كل الورديات + زر "غلق قسري".
- تسجيل العملية في `user_shifts_audit` (يحتاج المرحلة 4/C أولاً).

### 🔧 نقاط الدخول الحيوية للاستئناف السريع

```
# Session (الحارس)
account/src/main/java/com/hamza/account/session/ShiftContext.java
account/src/main/java/com/hamza/account/session/ShiftContextLoader.java

# منطق الأعمال
account/src/main/java/com/hamza/account/service/UserShiftService.java
account/src/main/java/com/hamza/account/service/ShiftReportService.java

# SQL
account/src/main/java/com/hamza/account/model/dao/UserShiftDao.java

# الشاشة
account/src/main/java/com/hamza/account/controller/users/UserShiftController.java
account/src/main/resources/com/hamza/account/view/user-shift-view.fxml

# Migrations
docs/scripts/main/V018_user_shifts_summary.sql
```

### 📋 Checklist للجلسة الجديدة

- [ ] تشغيل البرنامج والتحقق من أن الوردية تُحمّل عند الـ login.
- [ ] التأكد من أن زر الدفع في POS معطل بدون وردية.
- [ ] تشغيل سيناريوهات الاختبار 9-14 سريعاً.
- [ ] اختيار أولوية من القائمة أعلاه والبدء بها.

---

## سجل التغييرات (Changelog)

| التاريخ | المرحلة | الوصف |
|---------|:-------:|-------|
| 2026-04-17 | 1 | إصلاحات عاجلة + توحيد الدومين + معالجة SQL Injection |
| 2026-04-17 | 2 | ربط الحركات المالية + ShiftSummary + X/Z reports |
| 2026-04-18 | 4/B | حارس العمليات المركزي في POS + Invoice + Expenses |

---

## 📞 قواعد للمتابعة

1. بعد كل مرحلة جديدة: **حدّث قسم "سجل التغييرات" + الخلاصة أعلى الملف**.
2. لا تحذف الأقسام القديمة — فقط أضف ✅ ومهمة جديدة.
3. الترتيب الموصى به للعمل: **4/B المتبقي ⇒ UX (تحديث btnPay) ⇒ 3 ⇒ 4/A ⇒ 4/C ⇒ 5**.
4. للعمل المالي (HIGH RISK) التزم بقاعدة **Minimal Safe Change** — لا refactor غير مطلوب.
```