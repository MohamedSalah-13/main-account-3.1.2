# خطة الانتقال إلى Spring Boot

خطة نقل التطبيق من JavaFX desktop مع MySQL محلي إلى خادم Spring Boot يخدم عميل JavaFX،
مع تمهيد الطريق لتعدد المستأجرين (SaaS).

المبدأ الحاكم: **الـ SQL المكتوب يدويًا هو حيث تعيش قواعد العمل — لا يُعاد كتابته.**
كل خطوة تنقل الكود دون تغيير منطقه، وتُثبَّت باختبارات قبل الانتقال للتالية.

---

## 1. تقييم الوضع الحالي

### الحجم

| المقياس | القيمة |
|---|---|
| ملفات Java | 585 |
| ملفات FXML | 146 |
| جداول قاعدة البيانات | 43 |
| DAOs | 56 |
| دوال DAO عامة | ~413 |
| Services | 41 (كلها `record`) |
| أسطر داخل الـ controllers | 23,312 |
| حدود المعاملات (`insertMultiData`) | 15 موضعًا في 8 DAOs |
| تغطية اختبارية | سويت واحد (`CryptoDatabaseConfigTest`) |

### ما يساعد على الانتقال

- **`DaoFactory` يُمرَّر عبر الـ constructor** في كل مكان
  (`ItemsController(DaoFactory, DataPublisher)`) — ليس `static` مبعثرًا في جسم الدوال.
  هذه نقطة فصل واحدة يمكن استبدالها.
- **`ConnectionManager`** يربط الاتصال بالـ thread عبر `ThreadLocal` — وهو نفس نموذج
  Spring `@Transactional` بالضبط، فالتحويل تعديل في ملف واحد.
- **`AbstractDao`** JDBC خالص، بلا أي اعتماد على JavaFX.
- **41 service كـ `record X(DaoFactory)`** — طبقة موجودة فعلًا وصالحة لتصبح حدود الـ API.
- **MapStruct مُعرَّف بالفعل** في `annotationProcessorPaths` بالـ pom الجذري.
- **`V000_genesis_baseline.sql`** baseline موحّد بلا triggers — جاهز كـ Flyway baseline.

### العوائق (مرتبة حسب الخطورة)

**ع-1 — حدود المعاملات ستنكسر عبر الشبكة.** *(الأخطر)*
`TotalsSalesDao.insert` يكتب الهيدر ثم يستدعي `salesDao.insertList(...)` معتمدًا على أن
`ConnectionManager` يبقيهما على نفس الاتصال. لو صار كل نداء DAO طلب HTTP منفصلًا،
ستُحفظ الفاتورة ناقصة. المواضع الـ 15:

```
ItemsDao:124, ItemsDao:142
StockTransferDao:47, StockTransferDao:86
TotalsBuyDao:63, TotalsBuyDao:75
TotalsPurchaseReturnDao:61, TotalsPurchaseReturnDao:72
TotalsSalesDao:69, TotalsSalesDao:83
TotalsSalesReturnDao:67, TotalsSalesReturnDao:85
TreasuryDepositExpensesDao:107
TreasuryTransferDao:98
```

**ع-2 — 28 domain model تستخدم `JavaFX Property`.**
`ItemsModel.id` هو `IntegerProperty` لا `int`. Jackson لا يُسلسِلها وJPA لا تتعامل معها.

```
Audit_log, CustomerAccount, Customers, Employees, Expenses, ExpensesDetails,
ItemsModel, ItemsUnitsModel, Items_Package, Purchase, Sales, SelPriceTypeModel,
Stock, StockTransfer, StockTransferListItems, SubGroups, SupplierAccount, Target,
Total_buy, Total_Buy_Re, Total_Sales, Total_Sales_Re, TreasuryBalance,
TreasuryData, TreasuryMovementData, UnitsModel, Users, UserShift
```

**ع-3 — 79 ملفًا خارج `service/` و`model/dao/` يستخدم `DaoFactory` مباشرة.**
طبقة الـ services قابلة للتجاوز، فلا تصلح كحد API قبل إغلاق هذه الثغرات.
التوزيع: 39 controller، 11 dash، 19 view، 10 interfaces/others.

**ع-4 — `TrialManager` يُستدعى من داخل الـ DAOs**
(`ItemsDao:122`، `CustomerDao:83`، `TotalsBuyDao:61`، `TotalsSalesDao:66`)
ومربوط بـ Windows `MachineGuid` — بلا معنى في SaaS.
تحذير: `MAX_FAILS = 1`، فأي false positive يقفل النسخة نهائيًا.

**ع-5 — لا يوجد أي عمود tenant** في الـ 43 جدولًا. `company` صف واحد بـ `comp_id`.

**ع-6 — التغطية الاختبارية ≈ صفر.** نقل 413 دالة SQL بلا شبكة أمان يعني كسرًا مؤكدًا
لا يُكتشف إلا في الإنتاج.

**ع-7 — العميل ثرثار.** 22 service تجلب كل الصفوف ثم تفلتر في Java
(`ItemsService.getMainItemsListWithoutInactiveByMainGroupId` مثال واضح).
مقبول محليًا، كارثي عبر الإنترنت.

**ع-8 — حالة العميل المحلية.** `BackupService` يشغّل mysqldump محليًا، و11 ملفًا
تخزّن إعدادات في Java `Preferences` (مسار النسخ الاحتياطي، كلمة التشفير، الثيم، اللغة).

---

## 2. القرار المعماري

**الهدف:** Spring Boot backend + عميل JavaFX يتكلم HTTP. **لا** JavaFX داخل Spring Boot.

**القرار الحاسم:** الـ DAOs تُنقل كما هي إلى وحدة الخادم. الاستبدال الوحيد هو طريقة
الحصول على الاتصال داخل `ConnectionManager`:

```java
// acquire()  ->  DataSourceUtils.getConnection(dataSource)
// release()  ->  DataSourceUtils.releaseConnection(connection, dataSource)
```

هذا التعديل — في ملف واحد — يجعل الـ 56 DAO كلها تشارك في `@Transactional`
الخاصة بـ Spring، بلا لمس أي استعلام من الـ 413.

**الترتيب الحاكم:** Spring داخل نفس العملية أولًا (بلا شبكة)، ثم الفصل الشبكي.
هذا يفصل مخاطر الـ DI/المعاملات عن مخاطر الشبكة بدل خلطهما.

### بنية الوحدات المستهدفة

```
main (pom)
├── shared        DTOs عادية + العقود  (جديدة)
├── backend       Spring Boot + DAOs + services  (جديدة)
├── controlsfx    مكتبة الواجهة المشتركة (يبقى، بعد إخراج database/)
└── desktop       JavaFX + controllers  (هو account الحالي)
```

---

## 3. المرحلة أ — Spring داخل نفس العملية

بلا شبكة وبلا تقسيم. الهدف: DI، `@Transactional`، Flyway، واختبارات حقيقية.

### الخطوة 0 — شبكة الأمان الاختبارية *(إلزامية أولًا)*

لا تبدأ الخطوة 1 قبل اكتمال هذه.

- [ ] **0.1** إضافة `org.testcontainers:mysql` و`junit-jupiter` إلى الـ pom الجذري (scope: test).
- [ ] **0.2** إنشاء `AbstractDatabaseTest` يشغّل حاوية MySQL، يطبّق
      `V000_genesis_baseline.sql`، ويهيّئ `DataSourceProvider` عليها.
- [ ] **0.3** اختبارات توصيفية لمسارات الكتابة الحرجة — واحدة لكل موضع `insertMultiData`
      من الـ 15، تتحقق من **صفوف قاعدة البيانات** بعد العملية لا من القيمة المعادة
      (`insertMultiData` تعيد `1` دائمًا عند النجاح):
  - [ ] حفظ فاتورة بيع (`TotalsSalesDao:69, :83`)
  - [ ] حفظ فاتورة شراء (`TotalsBuyDao:63, :75`)
  - [ ] مرتجع بيع (`TotalsSalesReturnDao:67, :85`)
  - [ ] مرتجع شراء (`TotalsPurchaseReturnDao:61, :72`)
  - [ ] إضافة/تعديل صنف بوحداته وباركوداته (`ItemsDao:124, :142`)
  - [ ] تحويل مخزون (`StockTransferDao:47, :86`)
  - [ ] إيداع/مصروف خزينة (`TreasuryDepositExpensesDao:107`)
  - [ ] تحويل بين خزينتين (`TreasuryTransferDao:98`)
- [ ] **0.4** اختبار للتراجع (rollback): إجبار فشل في الاستدعاء الداخلي والتأكد من
      عدم بقاء أي صف — يحمي من ع-1 في كل خطوة تالية.
- [ ] **0.5** اختبار للمعاملة المتداخلة: `insertMultiData` داخل `insertMultiData` يجب
      أن يترك الـ commit للخارجية.

**معيار الاكتمال:** `mvn clean test` يمر، وتعطيل `beginTransaction` عمدًا يُفشِل
اختبارات 0.4 و0.5.

### الخطوة 1 — Flyway بدل `DatabaseMigrationService`

- [ ] **1.1** إضافة `flyway-core` و`flyway-mysql`.
- [ ] **1.2** نقل `V000_genesis_baseline.sql` إلى `db/migration/V1__genesis_baseline.sql`
      بتسمية Flyway.
- [ ] **1.3** `baselineOnMigrate=true` و`baselineVersion=1` للتثبيتات القائمة.
- [ ] **1.4** حذف `service/version/DatabaseMigrationService.java` و`MigrationResult`
      والنداء المعطّل في `DownLoadApplication:39` والدالة `updateDatabaseIfNeeded`.
- [ ] **1.5** اختبار: قاعدة فارغة → Flyway ينشئ الـ 43 جدولًا؛ قاعدة قائمة → لا تغيير.

**ملاحظة:** النداء معطّل عمدًا حاليًا (مذكور في `CLAUDE.md`). Flyway هو الحل الصحيح
لهذا التعطيل، لا التفافًا عليه.

### الخطوة 2 — Spring context داخل التطبيق المكتبي

- [ ] **2.1** إضافة `spring-boot-starter-jdbc` (بلا `web` بعد).
- [ ] **2.2** `AppConfig` بـ `@Configuration` يعرّف `DataSource` (نفس إعدادات Hikari
      من `DataSourceProvider`) و`DataSourceTransactionManager`.
- [ ] **2.3** نقل قراءة `config.xml` إلى `EnvironmentPostProcessor` يغذّي
      `spring.datasource.*` — يبقى التشفير كما هو، فلا تنكسر التثبيتات القائمة.
      لاحقًا يُقبل `application.yml` عادي للخادم.
- [ ] **2.4** تحويل الـ 41 service إلى `@Service` (تبقى `record`، Spring يدعم
      الـ constructor injection عليها).
- [ ] **2.5** استبدال `ServiceRegistry` بـ `ApplicationContext`. الإبقاء على
      `ServiceRegistry.get()` كواجهة رفيعة فوق الـ context أولًا حتى لا تتغير
      الـ 45 نقطة استدعاء دفعة واحدة.
- [ ] **2.6** `DownLoadApplication` يبني الـ context في الـ constructor بدل
      الـ 38 سطر تسجيل يدوي (`DownLoadApplication:45-82`).
- [ ] **2.7** التحقق: التطبيق يفتح ويعمل بالكامل. اختبارات الخطوة 0 تمر.

**ما لا يتغير:** أي controller أو FXML أو DAO.

### الخطوة 3 — نقل المعاملات إلى Spring

هذه أهم خطوة تقنية في الخطة كلها.

- [ ] **3.1** تعديل `ConnectionManager.acquire/release` لاستخدام
      `DataSourceUtils` بدل الـ `ThreadLocal` الخاص.
- [ ] **3.2** تشغيل اختبارات 0.3–0.5 — يجب أن تمر بلا تعديل. **لو فشلت، توقّف:**
      هذا يعني أن سلوك المعاملة تغيّر.
- [ ] **3.3** تحويل الـ 15 موضع `insertMultiData` إلى `@Transactional` على دوال
      الـ service المقابلة، موضعًا واحدًا في كل commit، مع تشغيل اختباره بعده.
- [ ] **3.4** حذف `beginTransaction`/`endTransaction`/`inTransaction` بعد أن يخلو
      الكود منها، وإبقاء `AbstractDao.insertMultiData` كغلاف مهجور مؤقتًا.
- [ ] **3.5** توثيق حدود المعاملات الجديدة في `CLAUDE.md`.

**معيار الاكتمال:** كل حدود المعاملات صريحة ومعلَنة بـ `@Transactional`، ولا شيء
يعتمد على `ThreadLocal` مخصص.

> **نقطة فاصلة:** بعد هذه الخطوة يصبح المشروع صالحًا لبناء الشاشات والمزايا الجديدة
> عليه. راجع القسم 6.

---

## 4. المرحلة ب — الفصل الشبكي

### الخطوة 4 — وحدة `shared` وDTOs بلا JavaFX

- [ ] **4.1** إنشاء وحدة `shared` بلا أي اعتماد على JavaFX أو Spring.
- [ ] **4.2** لكل موديل من الـ 28 (ع-2): `record` مقابل في `shared/dto`.
      البدء بـ `ItemsModel` و`Total_Sales` و`Sales` — أكثرها استخدامًا.
- [ ] **4.3** MapStruct mappers بين الـ DTO وموديل JavaFX (البنية التحتية جاهزة في
      الـ pom الجذري).
- [ ] **4.4** تعديل الـ `GenericMapper` في الـ DAOs لتنتج DTOs بدل موديلات JavaFX.
- [ ] **4.5** الـ controllers تحوّل DTO → موديل JavaFX عند الربط بـ TableView فقط.
      موديلات JavaFX تبقى في العميل ولا تعبر الشبكة أبدًا.
- [ ] **4.6** اختبار تسلسل Jackson لكل DTO ذهابًا وإيابًا.

### الخطوة 5 — إغلاق تجاوز طبقة الـ services

عمل ميكانيكي، تحميه اختبارات الخطوة 0. الـ 79 ملفًا من ع-3، على دفعات:

- [ ] **5.1** `dash/*` (11 ملفًا) — الأبسط، أغلبها تمرير `DaoFactory` للأمام فقط.
- [ ] **5.2** `view/*` (19 ملفًا) — نقاط تركيب، تُحقن الـ services بدل `DaoFactory`.
- [ ] **5.3** `controller/reports/*` (9 ملفات) — قراءة فقط، منخفضة المخاطر.
- [ ] **5.4** `controller/name_account/*` (7) و`controller/convert_treasury/*` (4)
      و`controller/convert_stock/*` (2).
- [ ] **5.5** `controller/items/*` (3)، `controller/setting/*` (3)،
      `controller/main/*` (4)، `controller/invoice/*` (2)، والمتبقي.
- [ ] **5.6** `interfaces/impl_dataInterface/*` — الأربع تنفيذات
      (`CustomData`، `CustomDataReturn`، `SuppliersData`، `SuppliersDataReturn`)
      تُعدَّل معًا دائمًا؛ أي تغيير في واحدة يلزم الثلاث الأخرى.
- [ ] **5.7** التحقق النهائي: `grep -rl DaoFactory` خارج `service/` و`model/dao/`
      لا يعيد شيئًا.

**معيار الاكتمال:** طبقة الـ services هي المدخل الوحيد لقاعدة البيانات — وهي شرط
الخطوة 7.

### الخطوة 6 — تقسيم الوحدات

- [ ] **6.1** نقل `controlsfx/database/*` (11 ملفًا) إلى وحدة `backend`.
- [ ] **6.2** نقل `model/dao/*` (56) و`model/domain/*` و`service/*` (41) إلى `backend`.
- [ ] **6.3** إعادة تسمية `account` إلى `desktop`، وإزالة اعتماده على MySQL connector.
- [ ] **6.4** `backend` يعتمد على `shared` فقط؛ `desktop` يعتمد على `shared`
      و`controlsfx`.
- [ ] **6.5** التحقق: `mvn clean package` ينتج jar للخادم وjar للعميل، ولا يحتوي
      jar العميل على أي كود JDBC.

### الخطوة 7 — REST API خشن

**القاعدة الحاكمة (تحمي من ع-1): endpoint واحد لكل عملية عمل مكتملة، لا endpoint لكل DAO.**

- [ ] **7.1** إضافة `spring-boot-starter-web` للـ `backend`.
- [ ] **7.2** endpoints الكتابة الخشنة — واحد لكل موضع من مواضع الـ 15:
      `POST /api/sales-invoices` بالفاتورة كاملة (الهيدر + البنود + الدفع) في طلب واحد،
      وكذلك الشراء والمرتجعان وتحويل المخزون وحركات الخزينة.
- [ ] **7.3** endpoints القراءة، مع **فلترة وترقيم صفحات في SQL**.
      قبل ذلك: إصلاح الـ 22 service التي تفلتر في Java (ع-7) — نقل الشرط إلى `WHERE`.
- [ ] **7.4** `RemoteServiceFactory` في العميل ينفّذ نفس واجهات الـ services الحالية
      عبر `RestClient`. الـ controllers لا تعلم بالفرق.
- [ ] **7.5** معالجة الأخطاء: `DaoException` → HTTP status → `DaoException` في العميل،
      مع الحفاظ على الرسائل العربية في `Error_Text_Show`.
- [ ] **7.6** إعادة تشغيل اختبارات الخطوة 0 عبر HTTP — يجب أن تمر بنفس التأكيدات.
- [ ] **7.7** التعامل مع انقطاع الشبكة: مهلات، إعادة محاولة للقراءة فقط،
      ورسالة واضحة للمستخدم. الكتابة **لا** يُعاد إرسالها تلقائيًا (خطر ازدواج الفواتير).

### الخطوة 8 — تعدد المستأجرين والأمان

- [ ] **8.1 — تعدد المستأجرين: قاعدة بيانات لكل مستأجر.**
      عبر `AbstractRoutingDataSource` يختار الاتصال حسب المستأجر من الـ JWT.
      **صفر تعديل على الـ 413 استعلامًا.**
      البديل (عمود `tenant_id`) يعني تعديل كل استعلام في المشروع ومراجعة كل `WHERE`
      يدويًا — مخاطرة تسريب بيانات بين العملاء لا تستحق العناء. لا تسلكه.
- [ ] **8.2** جدول تسجيل المستأجرين + إنشاء قاعدة تلقائي عند التسجيل (Flyway لكل قاعدة).
- [ ] **8.3** Spring Security + JWT. جدول `users` الحالي يصبح مستخدمي مستأجر واحد؛
      تُضاف طبقة مصادقة فوقه.
- [ ] **8.4** نقل `UserPermissionService` إلى تحقق على الخادم — الصلاحيات في العميل
      حاليًا إخفاء أزرار فقط (`ButtonWithPerm`، `DisableButtons`)، وهو غير كافٍ
      حين يصبح الـ API متاحًا عبر الشبكة.
- [ ] **8.5 — الترخيص:** حذف `TrialManager` من الـ DAOs الأربعة (ع-4) ونقل الفحص
      إلى الخادم كاشتراك. **احجب المسار القديم خلف feature flag أثناء الانتقال:**
      `MAX_FAILS = 1` يعني أن أي false positive يقفل نسخة العميل نهائيًا.
- [ ] **8.6 — النسخ الاحتياطي:** `BackupService` (mysqldump محلي) → مهمة مجدولة على
      الخادم لكل مستأجر. `ScheduledBackup` و`BackupController` يصبحان واجهة عرض فقط.
- [ ] **8.7 — الإعدادات:** الـ 11 ملفًا التي تستخدم `Preferences` — الإعدادات
      الشخصية (ثيم، لغة، أبعاد النوافذ) تبقى محلية؛ إعدادات العمل
      (مسار النسخ الاحتياطي، الفترة، كلمة التشفير) تنتقل للخادم.
- [ ] **8.8** إبطال المفتاح الاحتياطي المدمج في `CryptoDatabaseConfig`: بيانات
      الاعتماد التي يحميها يجب اعتبارها معروفة (موثّق في `CLAUDE.md`)، ولا يجوز
      حملها إلى بيئة SaaS.

---

## 5. المخاطر والتحذيرات

| # | الخطر | التخفيف |
|---|---|---|
| خ-1 | كسر ذرّية الفاتورة عبر الشبكة (ع-1) | endpoints خشنة (7.2) + اختبارات التراجع (0.4) |
| خ-2 | `MAX_FAILS = 1` يقفل نسخة عميل بسبب false positive | feature flag في 8.5، ولا تُضاف مسارات فشل جديدة في `TrialManager` |
| خ-3 | الثرثرة تجعل الأداء غير مقبول عبر الإنترنت (ع-7) | إصلاح 22 service في 7.3 **قبل** بناء الـ endpoints |
| خ-4 | تسريب بيانات بين المستأجرين | قاعدة لكل مستأجر (8.1) تجعله مستحيلًا بنيويًا |
| خ-5 | Lombok: خطأ ترجمة واحد يُنتج مئات الأخطاء الوهمية | أصلح أول خطأ حقيقي فقط (موثّق في `CLAUDE.md`) |
| خ-6 | البناء التراكمي يتخطى التعديلات صامتًا | استخدم `clean` دائمًا عند التحقق |
| خ-7 | تعديل واحدة من الأربع `impl_dataInterface` دون الثلاث | 5.6 يعالجها كوحدة واحدة |
| خ-8 | العميل القديم يتصل بخادم جديد | ترقيم إصدار الـ API من 7.1، ورفض العملاء القدامى صراحة |

---

## 6. متى تُبنى الشاشات والمزايا الجديدة

**بعد الخطوة 3، لا قبلها.**

عند تلك النقطة يتوفر: حقن التبعيات، معاملات صريحة بـ `@Transactional`، Flyway،
واختبارات تعمل. أي شاشة جديدة تُكتب مرة واحدة على العقد النهائي.

لو بُنيت الآن على النمط الحالي (controller يمسك `DaoFactory` ويستدعي DAO مباشرة)،
فستُضاف إلى قائمة الـ 79 ملفًا في الخطوة 5 وتُعاد كتابتها بالكامل.

قواعد الشاشات الجديدة بعد الخطوة 3:
- تُحقن الـ services، ولا تُلمس `DaoFactory` مطلقًا.
- كل عملية كتابة داخل دالة service واحدة موسومة بـ `@Transactional`.
- الفلترة والترتيب والترقيم في SQL، لا في Java.
- الموديل المستخدم للربط بالواجهة محلي؛ ما يعبر الحدود DTO عادي.

---

## 7. ترتيب التنفيذ

```
0 ──▶ 1 ──▶ 2 ──▶ 3 ══▶ [الشاشات الجديدة تبدأ هنا]
                  │
                  ├──▶ 4 ─┐
                  └──▶ 5 ─┴──▶ 6 ──▶ 7 ──▶ 8
```

- **0 → 3 تسلسلية بصرامة.** كل خطوة تعتمد على شبكة أمان سابقتها.
- **4 و5 متوازيتان** — الأولى تمس الموديلات، الثانية تمس نقاط الاستدعاء.
- **6 → 7 → 8 تسلسلية.**
- **8.1 (تعدد المستأجرين) يمكن تأجيله** بعد 7؛ الخادم يعمل بمستأجر واحد إلى حين.

**بوابة الجودة بعد كل خطوة:**

```bash
mvn clean test
```

مع `clean` دائمًا. أي فشل في اختبارات الخطوة 0 يوقف التقدّم فورًا — فهي التعريف
العملي لـ«لم يُكسر المنطق».
