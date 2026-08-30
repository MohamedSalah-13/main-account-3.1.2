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

وزيادةً على ذلك، لكل **ملف فتحته** في هذا الـ commit (§5):

- [ ] لا `@ColumnData` جديد؛ وما لمسته من أعمدة صار يُبنى بالكود
- [ ] الموديل الذي لمسته لا يستورد `javafx`
- [ ] الـ FXML الذي لمسته يعلن `fx:controller`، وتحميله يمرّر `ResourceBundle`
- [ ] كل `%key` في ذلك الـ FXML موجود في الحزم الثلاث (وإلا `LoadException` وقت الفتح)
- [ ] كل أيقونة واجهة في الشاشة التي عُدّلت Ikonli عبر `AppIcon`؛ لا تبقَ أيقونة PNG/`Image_Setting` بديلة فيها، وما لم يبقَ له مرجع من الصور حُذف
- [ ] كل `TextField` رقمي في الشاشة الجديدة أو المعدّلة له `TextFormatter` يمنع الحروف والرموز غير المسموحة أثناء الكتابة واللصق
- [ ] لا نصّ مستخدم مثبّت في خدمة لمستها — مفتاح ووسائط
- [ ] لا صنف داخلي مجهول جديد ينفّذ واجهة شاشة

---

## 4. ما يُترك للخطة، لا يُصلَح الآن

هذه ديون معروفة ومقيسة. **لا تُصلحها ضمن عمل ميزة** — لها خطوات في
[`erp-roadmap.md`](erp-roadmap.md).

> **استثناء:** ما تحكمه §5 خرج من هذا الجدول. الموديلات الحاملة لـ `Property`، وبناء
> الأعمدة بالتعليقات، والأيقونات، والنصوص المثبّتة — كلها صارت تُسدَّد **باللمسة الواحدة**
> لا بحملة مؤجّلة. لا تفتح ملفًا لأجلها، ولا تتركها في ملف فتحته.

| الدَّين | القياس (2026-08-20) | الخطوة |
|---|---|---|
| `ServiceRegistry.get` | 120 نداءً | 2.6 |
| ملفات تمسك `DaoFactory` خارج طبقة الخدمات | 75 | 2.6 |
| مواضع `insertMultiData` | 18 | 3.3 |
| `stock_movements` جدول ميت لا يقرؤه أحد | 0 قارئ | 8 |
| لا دفتر أستاذ عام | — | 9 |

---

## 5. قواعد «اللمسة الواحدة» — ما يخرج به أي ملف تفتحه

القواعد في §2 **تمنع** دَينًا جديدًا. هذه القواعد **تسدّد** الدَّين القائم، بلا حملة كبيرة
تتعثّر في منتصفها وتترك المشروع بنمطين.

**القاعدة الحاكمة:** أي ملف تفتحه لتعديل أي شيء — إصلاح عطب، ميزة، تحسين — **يخرج
مستوفيًا القواعد السبع التالية**. لا تفتح ملفًا لتطبيقها وحدها، ولا تتركه دونها.

### ق-ل1 — أعمدة الجدول تُبنى بالكود، لا بـ `@ColumnData`

`TableColumnAnnotation` يبني الأعمدة بـ `PropertyValueFactory`، وهو انعكاس باسم نصّي:
**حقل يُعاد تسميته يُنتج عمودًا فارغًا بلا أي خطأ تصريف**. اليوم 170 تعليقًا في 30 ملفًا
و21 موضع استدعاء — أي 170 نصًّا غير مفحوص. وهو أصلًا نصف مهجور: كل شاشة احتاجت تنسيقًا
أو عمودًا محسوبًا أو زرًّا هربت منه وكتبت `setCellValueFactory` يدويًّا بجواره.

```java
// ❌
@ColumnData(titleName = "item.name")
private StringProperty nameItem = new SimpleStringProperty();

// ✅ في الـ controller
Columns.text("item.name", row -> row.getItems().getNameItem())
```

**الهدف النهائي حذف `ColumnData.java` و`TableColumnAnnotation.java`.**

**تنبيه — موضعان لا يُعالَجان بنقل الكود إلى الـ controller:**
`TableController` و`TableWithTextSearchController` عامّان ويخدمان 17 شاشة عبر
`DataTable.classForColumn()`؛ لا يعرفان الموديل فلا يستطيعان كتابة أعمدتهما. البديل هو
تغيير الـ seam نفسه من `Class` إلى قائمة أعمدة مكتوبة:

```java
// interfaces/api/DataTable.java
List<TableColumn<T, ?>> columns();     // بدل  Class<? super T> classForColumn()
```

مرجع الدالة مفحوص عند التصريف (إعادة التسمية تتبعه)، وصفر انعكاس، والتنسيق والمحاذاة
و RTL صارت قابلة للتعبير.

### ق-ل2 — الموديل الذي تلمسه يخرج بلا `javafx`

امتداد ق-ج1 من المنع إلى الترحيل: 32 موديلًا من 59 ما زال يستورد
`javafx.beans.property`.

**الترتيب إلزامي:** ق-ل1 أولًا. `PropertyValueFactory` هو ما **يُبقي** الـ property في
الموديل؛ تنظيف الموديل قبل استبدال بناء الأعمدة يكسر الجداول **صامتة**.

خارج `model/` تُستعمل هذه الـ property في الغالب الأعمّ للقراءة فقط ولكائن متداخل
(`f -> f.getValue().getItems().nameItemProperty()`)، وتحويلها مباشر. الاستثناء الوحيد
الذي يستحقّ `Property` فعلًا هو تحرير سطور الفاتورة — والقدوة موجودة ومطبَّقة:
[`features/invoice/InvoiceEditorViewModel`](../account/src/main/java/com/hamza/account/features/invoice/InvoiceEditorViewModel.java)
يضع الـ property في **ViewModel** بطبقة الواجهة، لا في الموديل.

الموديل والـ mapper في الـ DAO وكل مستدعي `xxxProperty()` **في نفس الـ commit**؛ موديل
واحد لكل commit. وعند الفراغ يُحذف `DForColumnTable` — حقله
`private Users users = CurrentUser.getOrNull()` يُنفَّذ عند إنشاء **كل صف**، فيلتقط كل
صف المستخدم النشط لحظتها؛ على خادم متعدد الطلبات هذا خطأ صامت لا يظهر في أي اختبار.

### ق-ل3 — كل FXML يعلن `fx:controller`، وكل تحميل يمرّر `ResourceBundle`

**البنية التحتية جاهزة:** `OpenFxmlApplication.bindController` يركّب `setControllerFactory`
حين يعلن الملف `fx:controller`، فيُحترَم الإعلان **ومع ذلك** يُستعمل الكائن الذي بناه
المستدعي بمعاملات الـ constructor. التعارض القديم («Controller value already specified»)
محلول، ومع ذلك 46 ملفًا من 56 ما زال بلا الإعلان. المكسب أن IDE يربط `@FXML` بالملف،
فيصير `fx:id` المُعاد تسميته خطأً يجده المحرّر لا المستخدم.

والشقّ الثاني ليس تجميلًا: `FXMLLoader` **يرمي `LoadException`** عند أول `%key` إن كان
الـ bundle فارغًا، **وكذلك إن كان المفتاح غير موجود فيه**. خمس شاشات كانت معطّلة بهذا
تمامًا (`MonthlySalesView`، `customer-purchased-items-view`، `CustomerReceivableView`،
`ItemSalesRankView`، `DailyItemSalesView`) وأُصلحت.

```java
// ✅ الأسلوب المتّبع
new FXMLLoader(getClass().getResource("x.fxml"),
        LanguageManager.getInstance().getResourceBundle());
```

**الأفضل: التحميل عبر `OpenFxmlApplication`** فيمرّر الحزمة عنك. ما زال 14 موضعًا
يستدعي `new FXMLLoader` مباشرةً متجاوزًا إياه؛ كل واحد منها قنبلة موقوتة تنفجر لحظة
إضافة `%key` إلى ملفه.

### ق-ل4 — الأيقونات Ikonli، لا `InputStream`

`ikonli-javafx` و`ikonli-feather-pack` في الـ pom بالفعل، ومستعملان في 3 شاشات فقط.
المقابل `Image_Setting` بـ **69 موضع `new Image_Setting()`**، وكل حقوله `InputStream`
تُفتح **كلها** في مُهيّئ الحقل: كل استدعاء يفتح ~40 مجرى من داخل الـ jar ليستعمل واحدًا،
ولا يُغلق أيًّا منها. و`InputStream` أحادي الاستعمال — قراءته مرتين تُنتج صورة فارغة.
(`ImageSetting` في `controlsfx` له العيب نفسه.)

وPNG لا يقبل إعادة التلوين، فأي عمل على الوضع الداكن أو التباين سيصطدم به، بينما
`FontIcon` يقبل `-fx-icon-color` من CSS.

**الطريقة:** `enum AppIcon` يترجم الاسم الدلالي إلى أيقونة، فتصير أي بدائل لاحقة ملفًّا
واحدًا لا 69 موضعًا. واحذف صورة لم يبقَ لها مرجع في نفس الـ commit.
**حزمة Ikonli جديدة ⇒ `requires` في `module-info.java`**، وإلا فشلت وقت التشغيل لا وقت
التصريف.

**الإلزام عند لمس شاشة:** كل شاشة جديدة، أو شاشة موجودة عُدّلت لأي سبب، تخرج وأيقونات
واجهة المستخدم فيها من `AppIcon`/Ikonli — الأزرار، العناصر الشجرية، القوائم والحالات.
لا تُستبدل صور محتوى العمل (مثل صورة الصنف التي يرفعها المستخدم)، فهي ليست أيقونات واجهة.
لا تضف `Image_Setting` أو PNG جديدًا لأيقونة؛ أضف اسمًا دلاليًّا إلى `AppIcon` أولًا ثم لوّنه
بـ CSS. هذا يحافظ على الوضعين الفاتح والداكن ويمنع فتح `InputStream` غير ضروري.

### ق-ل5 — الحقل الرقمي يمنع الحروف عند الإدخال

التحقق عند الحفظ لا يكفي: يترك المستخدم يكتب أو يلصق قيمة غير صالحة ثم يكتشف الخطأ في
نهاية النموذج. كل `TextField` يمثل رقمًا في شاشة جديدة أو شاشة عُدّلت يجب أن يرفض الحروف
والرموز غير المسموحة **أثناء الكتابة واللصق** باستخدام `TextFormatter`.

```java
// كمية / سعر / رصيد: يسمح بسالب وكسور وفق نمط الأرقام الموحد
Utils.setTextFormatter(txtQuantity, txtPrice);

// رقم صحيح موجب فقط، مثل الهاتف أو الرقم التسلسلي أو الباركود
txtSerial.setTextFormatter(TextFormat.createNumericTextFormatter());
```

لا تستخدم `setOnKeyPressed` أو listener يحذف الحرف بعد ظهوره؛ `TextFormatter` يرفض
التغيير قبل وصوله إلى الحقل ويغطي اللصق كذلك. يظل تحقق مجال العمل عند الحفظ مطلوبًا:
السعر غير السالب، الكمية الموجبة، وحدود الطول — الفلتر يمنع الحروف فقط ولا يقرر صحة قيمة
المجال.

### ق-ل6 — الخدمة لا تُركّب نصًّا للمستخدم

اليوم 525 نصًّا عربيًّا مثبّتًا في 101 ملف `.java`، وأسوأها ليس في الشاشات بل في المنطق:
`RbacService` (24)، `ReportExportService` (24)، `InvoicePaymentTerms` (11)،
`WipeCatalog` (7).

`LanguageManager` مفرد ثابت بلغة **واحدة للعملية كلها**. على خادم تصير اللغة **لكل طلب**،
تمامًا كما يصير `CurrentUser` لكل طلب (ق-ج2). فالنصّ المثبّت داخل خدمة ليس مخالفة ترجمة
بل مخالفة الفصل نفسه.

```java
// ❌ داخل خدمة
throw new BusinessRuleException("الصنف غير موجود");

// ✅ مفتاح ووسائط، والتنسيق عند حافّة الواجهة
throw new BusinessRuleException("item.not.found", itemId);
```

**ابدأ بحزمة `features/`** فهي المرشّحة لتصير endpoints. والحزم الثلاث متطابقة المفاتيح
(1338 مفتاحًا)، لكن `messages.properties` و`messages_ar.properties` متطابقان محتوًى —
100 كيلوبايت مكرّرة يجب أن تُكتب مرتين وإلا انحرفا.

### ق-ل7 — لا صنف داخلي مجهول ينفّذ واجهة شاشة

`new DataTable<>(){...}` مضمّنًا داخل controller يربط منطق الشاشة بالـ controller ويمنع
اختباره. المواضع القائمة: `AddAreaController`، `EmployeesController`، `UserController`،
`ExpensesDetailsApplication`، `CreditLimitSource`، `LowStockSource`.

**المعيار الحاسم هو نفسه في كل مكان:** هل يمكن اختباره دون إقلاع JavaFX toolkit؟ صنف
مسمّى في `features/<area>/` يمكن؛ صنف داخلي مجهول لا يمكن.

وصنف يحتاج **4 معاملات نوع** عَرَض لا سبب: السبب غياب موديل `Document` موحّد. لا تُصلح
الأعراض واحدًا واحدًا — البند في [`erp-roadmap.md`](erp-roadmap.md).

---

### قاعدة بلا اختبار أمنية

السابقة موجودة وتعمل: `AuthorizationArchitectureTest` و`ErrorHandlingArchitectureTest`
**تُفشلان البناء**. كل قاعدة أعلاه تُثبَّت باختبار معماري يمنع الارتداد، وإلا عاد الدَّين
من باب المراجعة البشرية:

الحُرّاس **مكتوبون ويعملون**، في `account/src/test/java/com/hamza/account/architecture/`:

| القاعدة | الاختبار الحارس | ما يثبّته |
|---|---|---|
| ق-ل1 | `TableColumnArchitectureTest` | 170 تعليقًا في 30 ملفًا، و21 نداءً — **لا تزيد**، وملف جديد يتبنّاه يفشل |
| ق-ل2 | `ModelPurityArchitectureTest` | 32 موديلًا — يمنع رقم 33، ويمنع تهيئة حقل من `CurrentUser` |
| ق-ل3 | `FxmlArchitectureTest` | **صارم بلا خط أساس**: كل `%key` موجود في الحزم الثلاث، وأي FXML فيه `%key` لا يُحمَّل بلا `ResourceBundle`. والـ`fx:controller` بخط أساس 46 |
| ق-ل6 | `LocalizationArchitectureTest` | 264 نصًّا في 31 ملفًا تحت `features/` — لا تزيد |

**لكل حارس اختبار «القائمة تبقى صادقة»**: حين تنظّف ملفًا يفشل البناء حتى تشطبه من
القائمة. هذا ما يمنع القوائم من أن تصير خيالًا، ويجعل الدَّين المتبقّي رقمًا يمكن الوثوق به.

وقد جُرِّب كل حارس بزرع مخالفة متعمّدة والتأكد من أنه **يفشل فعلًا** — بما في ذلك إعادة
إنتاج عطب `MonthlyView` نفسه. حارس أخضر لا يعضّ لا يساوي شيئًا.

**اكتب الاختبار مع أول دفعة من القاعدة، لا بعدها.** اختبار «العدّ لا يزيد» (baseline
counter) يعمل من اليوم الأول ولا ينتظر اكتمال الترحيل. ورسائل الفشل بالإنجليزية عمدًا:
كونسول Maven هنا ليس UTF-8، فالمعرّف العربي يظهر فيه `?-?3`.

### ملاحظة تُطبَّق مع ق-ل2

النقود في الموديلات المالية `double`. حين تنظّف موديلًا ماليًّا من الـ property، حوّلها
إلى `BigDecimal` في نفس الـ commit — نفس الملف ونفس الـ mapper، والتكلفة صفر مقارنةً
بفتحه مرة أخرى لاحقًا.

---

*آخر تحديث: 2026-08-20*
