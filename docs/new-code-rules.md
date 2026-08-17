# عقد الكود الجديد — كيف تكتب اليوم كي لا تدفع غدًا

**الغرض:** كل موديل أو DAO أو خدمة تُكتب من اليوم يجب أن تكون **جاهزة للانتقال إلى Spring
Boot وإلى SaaS بلا إعادة كتابة**. هذا الملف هو العقد. ليس خطة — الخطة في
[`erp-roadmap.md`](erp-roadmap.md) — بل قائمة قواعد تُطبَّق على كل commit جديد.

**اقرأ هذا الملف قبل إنشاء أي ملف في `model/domain/` أو `model/dao/` أو `service/`.**

---

## 1. لماذا هذا العقد موجود

الانتقال إلى Spring في هذا المشروع **رخيص بشكل غير عادي**، لأن البنية التحتية مكتوبة
بنفس نموذج Spring بالضبط:

| الموجود اليوم | مقابله في Spring | حجم التحويل |
|---|---|---|
| `ConnectionManager` (ربط الاتصال بالـ thread) | `DataSourceUtils` + `TransactionSynchronizationManager` | ملف واحد |
| `AbstractDao` | `JdbcTemplate` | يبقى كما هو، أو يلتف حوله |
| `GenericMapper` | `RowMapper` | نفس التوقيع تقريبًا |
| `TransactionTemplate` | `org.springframework.transaction.support.TransactionTemplate` | نفس الاسم ونفس الفكرة |
| `DaoFactory` | `@Configuration` + `@Bean` | تعليق تعليقات |
| `ServiceRegistry` | `ApplicationContext` | `get()` يفوّض للسياق |
| `DataSourceProvider` (Hikari) | إعداد Spring Boot الافتراضي | إعداد |
| Flyway | auto-configuration | إعداد |

**الطبقة التي ستتحول ليست هي المشكلة.** المشكلة في ثلاثة أشياء تُكتَب اليوم بشكل يجعل
التحويل مستحيلًا لاحقًا، وكلها في الموديلات والخدمات لا في الـ DAOs:

1. **موديلات تحمل `javafx.beans.property`** — لا تُسلسَل إلى JSON، ولا تعيش على خادم.
   24 موديلًا من 50 اليوم.
2. **حالة عامة ثابتة (static)** — `CurrentUser`, `ShiftContext`, `Preferences`. على
   الخادم يصبح كل واحد منها **لكل طلب** لا لكل عملية.
3. **منطق داخل الـ controllers** — لا يمكن استدعاؤه من endpoint ولا اختباره.

كل قاعدة تحت تقابل واحدة من هذه الثلاثة.

---

## 2. القواعد

### ق-ج1 — الموديل الجديد POJO عادي

**الصحيح:** موديل بلا `javafx`، بلا وراثة من `DForColumnTable`، بحقول عادية.
القدوة الموجودة في المشروع: `features/stockcount/StockCount.java`.

```java
// ✅ features/<feature>/Something.java
public class Something {
    private int id;
    private String name;
    private BigDecimal amount;
    // getters/setters عادية، أو record لو كان للقراءة فقط
}
```

```java
// ❌ لا تفعل هذا في شيء جديد
public class Something extends DForColumnTable {
    private StringProperty name = new SimpleStringProperty();
}
```

**لماذا:** `DForColumnTable` يحمل `BooleanProperty` **و** `Users users =
CurrentUser.getOrNull()` في مُهيّئ حقل — أي أنه يستدعي حالة عامة ثابتة لحظة إنشاء كل صف.
على خادم متعدد المستخدمين هذا خطأ صامت: الصف يلتقط مستخدم أي طلب صادف أن يكون نشطًا.

**إذا احتاجت الشاشة `Property` للربط بجدول JavaFX:** اصنع صنف عرض منفصلًا في طبقة
الواجهة يلتف حول الموديل. لا تلوّث الموديل. الموديلات الـ24 القائمة تبقى كما هي حتى
تُنقل بخطة، لكن **لا يُضاف إليها رقم 25**.

---

### ق-ج2 — الـ DAO لا يقرأ حالة عامة

الـ DAO يستقبل ما يحتاجه كوسائط. لا `CurrentUser`، لا `Preferences`، لا `ServiceRegistry`،
لا `DefaultStock.ID` مقروءًا من داخله.

```java
// ✅
public int insert(Something s, int userId) throws DaoException { ... }

// ❌
public int insert(Something s) throws DaoException {
    int userId = CurrentUser.get().getId();   // الخادم لا يعرف "المستخدم الحالي"
}
```

**استثناء موثَّق:** الـ DAOs القائمة التي تقرأ `TrialManager` — **منطقة محظورة، لا تُلمس**
(`MAX_FAILS = 1`). ولا تُضف DAO جديدًا يمرّ به.

---

### ق-ج3 — الاعتماديات تدخل من الـ constructor

```java
// ✅ نفس شكل الـ 36 خدمة القائمة — وهو بالضبط ما يحقنه Spring
public record SomethingService(DaoFactory daoFactory) { ... }

// ✅ خدمة تحتاج أكثر من واحدة
public record SomethingService(DaoFactory daoFactory, ItemUnits units) { ... }

// ❌ لا تنادِ السجلّ من داخل الخدمة
public class SomethingService {
    void doIt() {
        var other = ServiceRegistry.get(OtherService.class);  // لن يُحقن أبدًا
    }
}
```

`ServiceRegistry.get` مسموح **في الـ controllers فقط**، لأنها ستُستبدل بـ
`setControllerFactory` في خطوة واحدة. داخل خدمة أو DAO فهي دَين دائم.

> **ملاحظة على الحالة:** نداءات `ServiceRegistry.get` كانت 95 عند كتابة الخطة وهي اليوم
> **121**. الاتجاه معاكس للخطة. كل نداء جديد في طبقة غير الـ controllers يجب أن يُرفض في
> المراجعة.

---

### ق-ج4 — المنطق في خدمة بلا JavaFX

القدوة في المشروع: حزمة `features/invoice/` — 28 صنفًا تحمل منطق الفاتورة كاملًا،
بلا استيراد واحد لـ JavaFX، ولكل منها اختبار. هذا هو الشكل المطلوب لأي ميزة جديدة.

```
features/<feature>/
├── <Feature>.java              الموديل (ق-ج1)
├── <Feature>Dao.java           أو <Feature>Repository + Jdbc<Feature>Repository
├── <Feature>Service.java       المنطق — قابل للاختبار بلا شاشة
└── <Feature>Status.java …      الأنواع المساعدة
controller/<area>/<Feature>Controller.java   الشاشة: تقرأ الحقول وتنادي الخدمة فقط
```

**معيار القبول:** هل تستطيع كتابة اختبار للمنطق بلا تشغيل JavaFX toolkit؟ إن لا، فالمنطق
في المكان الخطأ.

**الفصل بواجهة (`Repository`) مطلوب حين يكون المنطق جديرًا باختبار بلا قاعدة بيانات** —
كما في `InvoiceStockRepository` / `JdbcInvoiceStockRepository`. للـ CRUD البسيط، `Dao`
مباشر يكفي.

---

### ق-ج5 — حد المعاملة معلن في الخدمة

```java
// ✅ حد واضح، يصبح @Transactional بتعليق واحد لاحقًا
public int save(Something s) throws DaoException {
    return TransactionTemplate.execute(() -> {
        int id = dao.insertHeader(s);
        dao.insertLines(id, s.lines());
        return id;
    });
}
```

**لا تُضف موضع `insertMultiData` جديدًا.** الـ18 القائمة تُنقل بالخطة؛ الجديد يستخدم
`TransactionTemplate` مباشرة، وهو الشكل الذي يتحول إلى `@Transactional` بحذف السطر.

**وحدّ المعاملة في الخدمة لا في الـ DAO.** `TotalsSalesDao.insert` الذي يفتح معاملة
ويستدعي DAOs أخرى هو النموذج القديم؛ صحيح أنه يعمل، لكن على الخادم يصبح حد المعاملة هو
حد الـ endpoint، وهذا لا يكون في DAO.

---

### ق-ج6 — الفلترة والترقيم في SQL

أي شاشة جديدة تعرض قائمة: `WHERE` و`ORDER BY` و`LIMIT/OFFSET` في الاستعلام، لا
`stream().filter()` على قائمة محمّلة كاملة.

**لماذا الآن:** على سطح المكتب تحميل 50 ألف صنف بطيء فقط. عبر الشبكة هو مستحيل.
`InventoryDao` يفعل هذا بشكل صحيح — قلّده.

---

### ق-ج7 — لا تُخزّن بيانات عمل في `Preferences`

Java `Preferences` = هذا الجهاز، هذا المستخدم. مسار النسخ الاحتياطي وفاصل الإشعارات
مناسبان له. **أي شيء يخص بيانات العميل يذهب إلى قاعدة البيانات** — على SaaS يصبح
`Preferences` غير موجود أصلًا.

---

### ق-ج8 — لا تُضف عمود `tenant_id`

قرار معماري مُتخذ ومثبَّت: تعدد المستأجرين سيكون **قاعدة بيانات لكل عميل**
(`AbstractRoutingDataSource`)، لا عمودًا مشتركًا.

**السبب من هذا المشروع تحديدًا:** الاستعلامات مكتوبة يدويًا وموزّعة على 49 DAO. عمود
`tenant_id` يعني مراجعة كل استعلام يدويًا، ونسيان واحد = عرض بيانات عميل لعميل آخر.
قاعدة لكل عميل تجعل التسريب **مستحيلًا بنيويًا** لا محروسًا بانضباط المراجعة، وتُبقي
Flyway يعمل كما هو تمامًا.

**ما يترتب على ذلك في الكود الجديد:** لا شيء. اكتب كأن العميل واحد. هذا هو بيت القصيد.

---

## 3. قائمة الفحص قبل دمج أي commit فيه `model` أو `dao` جديد

- [ ] الموديل لا يستورد `javafx` ولا يرث `DForColumnTable`
- [ ] الموديل لا ينادي شيئًا ثابتًا (static) في مُهيّئ حقل
- [ ] الـ DAO لا يقرأ `CurrentUser` / `Preferences` / `ServiceRegistry`
- [ ] الخدمة تأخذ اعتمادياتها في الـ constructor
- [ ] لا `ServiceRegistry.get` خارج طبقة الـ controllers
- [ ] المنطق قابل للاختبار بلا JavaFX toolkit، **ويوجد اختبار فعلًا**
- [ ] حد المعاملة عبر `TransactionTemplate` في الخدمة، لا `insertMultiData` جديد
- [ ] الفلترة والترقيم في SQL
- [ ] جدول جديد ⇒ هجرة `V<n>__*.sql` جديدة، **بلا `tenant_id`**، وبلا تعديل هجرة شُحنت
- [ ] الحذف مُعلَن في `DeleteRegistry`، والمسح في `WipeCatalog` (يفشل `WipeCatalogTest` بدونه)
- [ ] عمود جديد على مستند ⇒ `DocumentTableSpec` محدَّث، و`DocumentDaoStatementsTest` يمر
- [ ] `mvn clean test` — **`clean` إلزامية**، والبناء التراكمي يتخطى التعديلات صامتًا

---

## 4. ما يُترك للخطة، لا يُصلَح الآن

هذه ديون معروفة ومقيسة. **لا تُصلحها ضمن عمل ميزة** — لها خطوات في
[`erp-roadmap.md`](erp-roadmap.md):

| الدَّين | القياس (2026-08-17) | الخطوة |
|---|---|---|
| موديلات تحمل `Property` | 24 من 50 | ملحق SaaS |
| `ServiceRegistry.get` | 121 نداءً | 2.6 |
| ملفات تمسك `DaoFactory` خارج طبقة الخدمات | 75 | 2.6 |
| مواضع `insertMultiData` | 18 | 3.3 |
| `stock_movements` جدول ميت لا يقرؤه أحد | 0 قارئ | 8 |
| لا دفتر أستاذ عام | — | 9 |

---

*آخر تحديث: 2026-08-17*
