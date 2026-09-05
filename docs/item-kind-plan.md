# نوع الصنف (`item_kind`) — الخطة

**الحالة:** مقترحة، لم يُنفَّذ منها شيء بعد. الوثيقة دي هي العقد: أي تنفيذ يخالفها يخالف
قرارًا اتُّخذ بوعي، مش تفصيلة نُسيت.

---

## 0. المشكلة

البرنامج ما بيعرفش غير نوع واحد من الأصناف: بضاعة لها رصيد مخزني مشتق من سطور الفواتير
(`quantity_items_table`). لكن المحل بيبيع تلات حاجات مختلفة محاسبيًا:

1. **بضاعة** — كارت فكة ورقي، علبة عصير. تُشترى وتُخزَّن وتُباع. الإيراد إيرادها والتكلفة تكلفتها.
2. **خدمة** — رسوم توصيل، عمولة خدمة، أجر صيانة. **مالهاش رصيد أصلًا**، ودلوقتي حارس المخزون
   بيرفض بيعها (الرصيد صفر → سالب)، وتنبيه النواقص بيصرخ عند كل بيعة، وشاشة الجرد بتطلب عدّها.
3. **تحصيل نيابةً** — شحن كارت كهرباء أو فاتورة عبر فوري. العميل بيدفع 102 والمحل يوصّل 100
   للمُوكِّل ويكسب 2. الـ100 دي **مش إيراد** المحل، ومع ذلك بتعدي في `document_profit` كإيراد
   كامل، فمحل بيمرر 50 ألف كهرباء بيظهر في قائمة الدخل بـ50 ألف مبيعات.

الحيلة المستعملة اليوم — تشغيل إعداد «بيع بدون رصيد» عشان الخدمات تعدي — بتعطّل حارس المخزون
عن **البضاعة الحقيقية** كمان، لأن الإعداد عام (`PropertiesName.getSelWithoutBalance`).

---

## 1. القرارات المحسومة

| # | القرار |
|---|---|
| 1 | عمود واحد `items.item_kind ENUM('GOODS','SERVICE','AGENCY') NOT NULL DEFAULT 'GOODS'`. كل صف قائم يبقى `GOODS`، فالترقية ما بتغيّرش سلوك أي تركيب قائم. |
| 2 | **مش عمودين بوليان.** السؤالين (هل له رصيد؟ هل إيراده إيراده؟) مستقلين، بس التوليفة الرابعة بلا معنى. القيمة الواحدة بتجاوب على التلاتة بدوال — نفس نمط `DocumentType` بالضبط. |
| 3 | `SERVICE` **يتخطى حارس المخزون بالكامل**. `AGENCY` **لأ**: المحفظة لها رصيد حقيقي، وبيع 100 من محفظة فيها 50 لازم يُرفض. |
| 4 | استبعاد الإيراد بيتم **بطرح تكلفة سطور `AGENCY` من الإيراد ومن التكلفة معًا** في `document_profit`. الربح بيفضل ثابتًا رياضيًا — وده نص الاختبار، مش تعليق. |
| 5 | **النوع يُقفل بمجرد أن يتحرك الصنف** (`OpeningBalanceRegistry.ITEMS`)، ويُرفض تغييره صراحةً لا يُسقَط بصمت. نفس قاعدة `first_balance` ولنفس السبب: تغييره بيعيد كتابة ربح كل فاتورة قديمة. |
| 6 | **مفيش صلاحية جديدة.** النوع جزء من `items.update`. |
| 7 | دمج صنفين من نوعين مختلفين **مرفوض** — نفس عائلة رفض الوحدة الأساسية ورفض الصلاحية في `ItemMergeService.checkRules`. |
| 8 | قيد «البيع بأقل من سعر الشراء» **يفضل على الأنواع التلاتة**. مالوش علاقة بالمخزون. |
| 9 | فاتورة **الشراء** ما بتفرّقش بين الأنواع: شحن محفظة فوري فاتورة شراء عادية، وهي الطريقة الوحيدة لتمويلها. |
| 10 | `SERVICE` + `item_has_validity` توليفة مرفوضة عند الحفظ، والشاشة بتعطّل الاختيار. |

---

## 2. النموذج

`account.item.ItemKind` — enum في حزمة جديدة على وزن `account.document` و`account.party`،
**بلا أي import من `interfaces.*` أو `features.*`**، فتقدر كل الطبقات تقرأها.

```java
public enum ItemKind {
    /** بضاعة. الافتراضي، وسلوك كل صنف قائم. */
    GOODS   (true,  true,  true),
    /** خدمة بلا رصيد: رسوم، عمولة، أجر عمل. */
    SERVICE (false, true,  false),
    /** تحصيل نيابةً: رصيد محفظة يُباع بقيمته ويُورَّد لمُوكِّل. */
    AGENCY  (true,  false, false);

    /** هل يُحسب له رصيد ويُحرس؟ حارس المخزون، تنبيه النواقص، ورقة الجرد. */
    public boolean tracksStock();
    /** هل قيمة بيعه إيراد للمحل، ولا مجرد مال يعبر؟ */
    public boolean countsAsRevenue();
    /** هل يدخل تقييم المخزون؟ */
    public boolean valuedAsInventory();
}
```

**القاعدة الوحيدة على الكود الجديد:** ما حدش يسأل `kind == SERVICE`. كل موضع بيسأل السؤال
اللي يخصه (`tracksStock()`…)، فإضافة نوع رابع بعد كده تكون سطرًا واحدًا هنا لا بحثًا في المشروع.

`ItemsModel.itemKind` حقل عادي بقيمة ابتدائية `GOODS` — **لا `ObjectProperty`**. النموذج ده
لسه بيحمل `javafx.beans.property` من عهد سابق، وتحويله بالكامل خارج نطاق الميزة دي؛ الحقول
العادية `activeItem` و`hasValidate` و`mini_quantity` هي السابقة، والحقل الجديد بيلحق بيها
ولا بيزوّد الاعتماد على JavaFX.

---

## 3. الهجرة

`V36__item_kind.sql` — ملف واحد:

```sql
ALTER TABLE items
    ADD COLUMN item_kind ENUM('GOODS','SERVICE','AGENCY') NOT NULL DEFAULT 'GOODS'
        COMMENT 'GOODS بضاعة | SERVICE خدمة بلا رصيد | AGENCY تحصيل نيابة عن مُوكِّل'
        AFTER item_active;
```

مفيش تحديث بيانات: الافتراضي هو السلوك القائم. والـ `COMMENT` مقصود — نفس سبب `V20` مع
`treasury.amount`: العمود اللي معناه غير اسمه لازم يقوله في السكيمة نفسها.

الـ views في `R__views.sql` بتتعاد تلقائيًا بعد كل الهجرات المرقّمة، فتقدر تقرأ العمود الجديد
بلا ترتيب خاص.

---

## 4. أين يهبط كل تغيير

### أ) القراءة والكتابة

| الملف | التغيير |
|---|---|
| [ItemsDao.java](account/src/main/java/com/hamza/account/model/dao/ItemsDao.java) | ثابت `itemKind`؛ يُضاف لقائمة أعمدة `insertItem` وللفرعين في `updateStatementFor`؛ يُقرأ في `getItemsModel` و`mapCatalogRow`. |
| نفس الملف، `quickUpdate` | **لا يُضاف.** التعديل السريع من القائمة بيكتب أعمدة مسمّاة، والنوع مش من بينها — تغييره قرار له شاشة. |
| [ItemsModel.java](account/src/main/java/com/hamza/account/model/domain/ItemsModel.java) | حقل `ItemKind itemKind = ItemKind.GOODS`. |
| [ItemsService.java](account/src/main/java/com/hamza/account/service/ItemsService.java) | رفض `SERVICE` + تتبع صلاحية؛ ورفض تغيير النوع لصنف تحرّك. |

`QUERY_ITEMS` بيستعمل `SELECT *`، فإضافة عمود آمنة على كل القرّاء.

### ب) قفل النوع بعد الحركة

`OpeningBalanceRegistry.ITEMS` بيجاوب بالضبط على «هل تحرّك الصنف؟» (ستة جداول: سطور البيع
والشراء والمرتجعين والتحويل والجرد). القفل بيستعمله كما هو:

- `ItemsDao.updateStatementFor` بيُسقط `item_kind` من قائمة الأعمدة لما يكون الصنف اتحرّك —
  نفس ما بيعمله بالظبط مع `FIRST_BALANCE`، وبنفس السبب.
- والقيمة المتغيّرة **تُرفض برسالة**، ما تُسقطش بصمت: المستخدم اختار وله حق يعرف إن اختياره
  ما اتحفظش.
- شاشة الصنف بتعطّل القائمة وبتشرح ليه، عن طريق `ItemsDao.isOpeningBalanceLocked` الموجود أصلًا.

### ج) المخزون

| الملف | التغيير |
|---|---|
| [InvoiceLineService.java:177](account/src/main/java/com/hamza/account/features/invoice/InvoiceLineService.java:177) | `requireStock` بترجع فورًا لو `!kind.tracksStock()`. |
| [InvoiceStockGuard.java:52](account/src/main/java/com/hamza/account/features/invoice/InvoiceStockGuard.java:52) | `validateTotalBalances` بتتخطى الأصناف غير المتتبَّعة. |
| [InvoiceStockRepository.java](account/src/main/java/com/hamza/account/features/invoice/InvoiceStockRepository.java) | `lockItems` بترجع `Map<Integer, LockedItem>` بدل `Map<Integer, String>` — `record LockedItem(String name, ItemKind kind)`. النوع بيوصل من نفس الصف المقفول، فمفيش استعلام تاني ولا سباق. |
| [StockLevelAlert.java](account/src/main/java/com/hamza/account/features/notification/StockLevelAlert.java) | `check` بترجع بلا تنبيه لو الصنف غير متتبَّع. |
| `R__views.sql` — `mini_quantity_view` | `AND i.item_kind = 'GOODS'`. من غيره كل خدمة هتظهر في تقرير النواقص، لأن `mini_quantity` صفر و`0 >= رصيد سالب` صحيحة. |
| شاشة الجرد | ورقة الجرد بضاعة فقط. |

**`AGENCY` بيفضل محروسًا.** رصيد صنف «رصيد فوري» هو رصيد المحفظة الحقيقي، وتخطي الحرس فيه
معناه بيع شحنة مش موجودة.

### د) الإيراد

التعديل في `document_profit` داخل `R__views.sql`، في نصّيه (البيع والمرتجع):

```sql
LEFT JOIN (SELECT s.invoice_number,
                  SUM(s.total_buy_price) AS cost_of_sales,
                  SUM(CASE WHEN i.item_kind = 'AGENCY'
                           THEN s.total_buy_price ELSE 0 END) AS pass_through
           FROM sales s JOIN items i ON i.id = s.num
           GROUP BY s.invoice_number) c ON c.invoice_number = ts.invoice_number
```

ثم:

```
net_revenue   = (ts.total - ts.discount) - COALESCE(c.pass_through, 0)
cost_of_sales = COALESCE(c.cost_of_sales, 0) - COALESCE(c.pass_through, 0)
profit        = كما هو
```

**ليه التكلفة لا قيمة البيع؟** لأن المطروح لازم يكون واحدًا من الطرفين عشان الربح ما يتغيّرش:

```
(T − p) − (C − p) = T − C
```

المطروح هو المال اللي بيعبُر للمُوكِّل — أي تكلفة السطر — واللي بيفضل في الإيراد هو الهامش.
ده بالظبط تقييد الوكيل على أساس الصافي، وهو الوحيد اللي بيخلّي `document_profit` تعرّف الربح
بنفس التعريف قبل التغيير وبعده.

> **تحذير للمستخدم، لازم يتقال في الشاشة:** صنف `AGENCY` **لازم** يحمل سعر شراء. لو سعر شراءه
> صفر فالمطروح صفر، وكل المبلغ المحصَّل هيتحسب إيرادًا — وهو نفس العطب اللي الميزة موجودة عشانه.

القرّاء المتأثرون، وكلهم مقصودون: `ProfitLossDao`، و`total_sales_names_table` /
`sales_return_names_table` (بياخدوا `dp.cost_of_sales` و`dp.profit`)، والتقرير السنوي.
إجمالي الفاتورة في القائمة بيفضل 102 — هو فعلًا اللي اتقبض — والتكلفة والربح بس اللي بيتصححوا.

### هـ) الشاشات والتقارير

| الملف | التغيير |
|---|---|
| `items/addItem-view.fxml` + [ItemForm.java](account/src/main/java/com/hamza/account/controller/items/ItemForm.java) + [AddItemController.java](account/src/main/java/com/hamza/account/controller/items/AddItemController.java) | `ComboBox<ItemKind>` بجانب «صنف نشط»، وتعطيل خانة الصلاحية والرصيد الافتتاحي لغير البضاعة. |
| [ItemCatalogFilter.java](account/src/main/java/com/hamza/account/features/items/ItemCatalogFilter.java) + [ItemCatalogSql.java:162](account/src/main/java/com/hamza/account/features/items/ItemCatalogSql.java:162) | مُرشِّح `kind` (null = الكل)، بجوار `tracksExpiry` تمامًا. |
| `CatalogFact` + [JdbcCatalogFactRepository.java](account/src/main/java/com/hamza/account/features/itemreports/JdbcCatalogFactRepository.java) | العمود يُقرأ. |
| `ValuationReport`, `StockLevelReport`, `UnusedItemsReport` | بضاعة فقط (`valuedAsInventory` / `tracksStock`). |
| عمود الرصيد في قائمة الأصناف | يعرض «—» للخدمة بدل رقم بلا معنى. |
| [ItemMergeService.java:174](account/src/main/java/com/hamza/account/features/itemmerge/ItemMergeService.java:174) + `MergeItem` + `ItemMergeStatements` | قاعدة رفض رابعة، والعمود يُقرأ في الاستعلامين اللي بيقروا `item_has_validity`. |
| `messages*.properties` (التلاتة) | `item.kind.goods` / `.service` / `.agency`، `item.kind.label`، `item.error.kind.locked`، `item.error.kind.validity`، `item.error.kind.merge`. |

المفاتيح تُكتب **حرفيًا كاملة** في نداءات `getString` — `MessageKeyArchitectureTest` بيقرأ وسائط
النداء نصًّا، و`"item.kind." + name` غير مرئي لأي فحص ساكن.

---

## 5. ما لا تفعله الميزة عمدًا

1. **مش دفتر أستاذ عام.** «الـ100 المستحقة لفوري» مش بتتقيَّد كالتزام — هي بتنقص من رصيد صنف
   المحفظة وخلاص. الالتزامات بند §9 في `erp-roadmap.md`.
2. **مفيش تسوية آلية مع فوري** ولا حساب عمولة تلقائي. المستخدم بيدخل سعر الشراء.
3. **مفيش صلاحية لكل نوع** — مين يبيع خدمات ومين يبيع بضاعة سؤال ما حدش طلبه.
4. **مفيش رفض لصنف `AGENCY` على فاتورة شراء** — دي طريقة شحن المحفظة.
5. **مفيش نوع رابع للأصول أو المصروفات.** المصروف له شاشته وله `treasury_deposit_expenses`.
6. **تقييم المخزون بيستبعد `AGENCY`** رغم إن رصيد المحفظة أصل حقيقي. التقرير بيجاوب «إيه اللي
   على الرفوف»، وضم محفظة إلكترونية له بيخلط سؤالين. قرار قابل للعكس بتغيير `valuedAsInventory`
   لوحدها لو اتطلب.

---

## 6. الاختبارات

| الاختبار | يقول إيه |
|---|---|
| `ItemKindTest` (جديد) | الدوال التلاتة لكل قيمة. تافه، وهو اللي بيمنع قلب `AGENCY` و`SERVICE`. |
| `ItemKindMigrationTest` (جديد) | العمود موجود في V36 بافتراضي `GOODS` — على وزن `AuditProcedureMigrationTest` اللي بيقرأ ملفات الهجرة. |
| `InvoiceStockGuardTest` (قائم) | سطر `SERVICE` ما بيترفضش أبدًا؛ سطر `AGENCY` لسه بيترفض على رصيد ناقص. |
| `ItemsCatalogQueryTest` (قائم) | مُرشِّح النوع بيدخل نفس الـ`WHERE` في الصفحة وفي الـ`COUNT`. |
| `ItemMergeServiceTest` (قائم) | رفض دمج نوعين مختلفين. |
| **`ProfitDefinitionDatabaseAcceptanceTest`** (قائم، مبوَّب) | **أهم واحد.** حالة جديدة: فاتورة فيها سطر `AGENCY` بقيمة 100 تكلفتها 100 وسطر خدمة بـ2 → الإيراد 2 والتكلفة 0 والربح 2؛ و**الربح لكل مستند ما بيتغيرش** قبل التعديل وبعده. |
| `MessageKeyArchitectureTest` (قائم) | المفاتيح الجديدة في الحزم التلاتة — بيشتغل لوحده. |

الاختبار المبوَّب لازم **يُشغَّل** على سكيمة من الصفر:

```bash
mvn -o -pl account -am test -Dtest=ProfitDefinitionDatabaseAcceptanceTest -Daccount.db.acceptance=true
```

(الملف المقروء `account/config.xml` مش الجذر، و`-am` إجباري). والاختبار ده بيرفض الشغل في سنة
فيها مستندات، فالسكيمة الخام هي الطريق الوحيد.

---

## 7. المراحل

| # | المرحلة | قابلة للشحن وحدها؟ |
|---|---|---|
| **أ** | الهجرة + `ItemKind` + النموذج + الـ DAO + شاشة الصنف + القفل. النوع بيتحفظ ويتعرض، **ومفيش سلوك بيتغير**. | ✅ نعم — قيمة صفرية وخطر صفري. |
| **ب** | المخزون: الحارس، التنبيه، `mini_quantity_view`، ورقة الجرد، تقارير المخزون. من هنا الخدمة بتتباع من غير «بيع بدون رصيد». | ✅ نعم، وهي أكبر مكسب فوري. |
| **ج** | الإيراد: `document_profit` + اختبار القبول. | ✅ لكن **في commit لوحدها** — دي بتلمس view كل شاشات الربح بتقرأها. |
| **د** | التلميع: مُرشِّح القائمة، عمود الرصيد، قاعدة الدمج. | ✅ |

الترتيب مش اختياري: (ج) قبل (ب) معناها إيراد صحيح على شاشة لسه بترفض بيع الخدمة أصلًا.

---

## 8. ما لن يكون له اختبار

- **إن الشاشة بتعطّل القائمة فعلًا** لصنف اتحرّك — قاعدة في `initialize()`، والقاعدة اللي
  بتحرس فعلًا هي اللي في الـ DAO. الشاشة بتُتحقق بالتشغيل لا غير.
- **إن المستخدم ملأ سعر شراء لصنف `AGENCY`** — رقم صحيح نحويًا وكارثي معنويًا. الدفاع الوحيد
  رسالة في الشاشة، والأفضل منها تحذير عند الحفظ لو `AGENCY` وسعر الشراء صفر.
- **إن رصيد صنف المحفظة يساوي رصيد المحفظة عند فوري.** مفيش تسوية، والانحراف بيتكشف بالعدّ اليدوي.
