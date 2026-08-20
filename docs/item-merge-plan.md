# دمج الأصناف — الخطة النهائية

**الحالة:** منفَّذة بالكامل ما عدا **تشغيل** اختبار القبول: الهجرة، السجل، الجُمل، الـ DAO،
الخدمة، الشاشة، وخمسة اختبارات (٤٢ حالة تعمل + ٤ حالات قبول مؤجَّلة خلف
`-Daccount.db.acceptance=true`). اختبار القبول مكتوب لكن **لم يُشغَّل بعد** — لا يوجد MySQL
يعمل على جهاز التطوير وقت الكتابة. الوثيقة دي هي العقد: أي تنفيذ يخالفها يخالف قرارًا
اتُّخذ بوعي، مش تفصيلة نُسيت.

---

## 0. المشكلة

قبل ما يوجد `item_barcodes` (هجرة V3) كان الباركود الوحيد للصنف هو `items.barcode`، وعمود
فريد واحد معناه صنف لكل باركود. فالشيبسي بخمس نكهات دخل خمسة أصناف، وجرت عليه فواتير بيع
وشراء ومرتجعات لسنين. اليوم النكهات دي صنف واحد بخمس باركودات، فالأصناف القديمة لازم
**تُدمَج** في الصنف الجديد: كل حركة تمت عليها تُنسب للهدف، ثم يُحذف المصدر.

الدمج **لا يغيّر أي رقم مالي**. سطر الفاتورة بيحمل سعره وسعر شرائه وربحه ومعامل وحدته
(`type_value`) على نفسه، والدمج بيغيّر عمودًا واحدًا: لمن يُنسب السطر. ورصيد المخزون
بيتحسب من `quantity_items_table` اللي بتجمع من نفس السطور، فبيتصحح لوحده بلا أي كتابة.

---

## 1. القرارات المحسومة

| # | القرار |
|---|---|
| 1 | `items.first_balance` بتاع المصدر **يُضاف** على الهدف. (`items_stock.first_balance` مالوش قارئ — الـ view بتقرأ `items.first_balance`.) |
| 2 | الدمج **مرفوض** لو `items.unit_id` (الوحدة الأساسية) مختلف. لا تحويل ضمني ولا معامل — يوحّد المستخدم الوحدة الأول. |
| 3 | باركودات المصدر **تنتقل** للهدف (`item_barcodes`)، والمكرر يُتجاهل بهدوء. مسح الكيس القديم لازم يفضل يشتغل. |
| 4 | سطور الجرد المتعارضة **تُجمَع** (`system_qty` و`counted_qty`) في سطر الهدف بدل ما الـ UPDATE يفشل. |
| 5 | الدمج **مسموح** داخل فترة مقفلة — مفيش رقم بيتغير — مع تحذير في المعاينة وتسجيل العدد. |
| 6 | لو المصدر بيتتبع صلاحية (`item_has_validity = 1`) والهدف لأ → **مرفوض**. العكس مسموح. |
| 7 | سجل دمج دائم (هجرة V17). `audit_log` ما بيسجلش تعديل سطور الفواتير، فمن غيره مفيش أثر. |
| 8 | وحدات المصدر اللي الهدف مالوش زيها **تنتقل** للهدف بمعاملها وأسعارها وباركودها. |

---

## 2. خريطة المراجع

كل مكان في السكيما بيشاور على `items.id`، وما يحصل له عند الدمج. القائمة دي **تُعلَن في
الكود** (`ItemReferenceRegistry`) و**يفحصها اختبار** يقرأ المفاتيح الأجنبية من ملفات
الهجرة — نفس أسلوب `WipeCatalogTest`.

| الجدول | العمود | الهجرة | الإجراء |
|---|---|---|---|
| `sales` | `num` | V1 | `MOVE` — UPDATE مباشر |
| `sales_re` | `item_id` | V1 | `MOVE` |
| `purchase` | `num` | V1 | `MOVE` |
| `purchase_re` | `item_id` | V1 | `MOVE` |
| `stock_movements` | `item_id` | V1 | `MOVE` |
| `stock_transfer_list` | `item_id` | V1 | `MOVE` (الشاشات اتشالت، الجدول باقٍ) |
| `stock_count_lines` | `item_id` | V8 | `MERGE_ROW` — `UNIQUE(count_id,item_id,unit_id)` |
| `items_stock` | `item_id` | V1 | `MERGE_ROW` — `UNIQUE(item_id,stock_id)` |
| `items_units` | `items_id` | V1/V5 | `MOVE_IF_ABSENT` — `UNIQUE(items_id,unit)` و`UNIQUE(items_barcode)` |
| `item_barcodes` | `item_id` | V3 | `MOVE_IF_ABSENT` — `UNIQUE(barcode)` |
| `items_package` | `item_id`, `package_id` | V1 | `MOVE_DEDUPE` — عمودان، وخطر مرجع ذاتي |

الأربعة الأخيرة كلها `ON DELETE CASCADE`، يعني لو ما اتعاملناش معاها هتُحذف بصمت مع
المصدر. ده بالظبط سبب وجود السجل المُعلَن: عمودان اسمهما `num` وعمود اسمه `items_id`
موزّعون على أربع هجرات، ونسيان واحد بيسيب تاريخًا يتيمًا بدل خطأ ظاهر.

---

## 3. قواعد الرفض

بالترتيب، وكلها قبل أي كتابة:

1. `AuthorizationGuard.require(AppPermissions.ITEMS_MERGE)`.
2. المصدر ≠ الهدف، والهدف مش ضمن قائمة المصادر.
3. الصنفان موجودان.
4. `source.unit_id == target.unit_id` وإلا `BusinessRuleException("item.merge.error.unit")`.
5. `!(source.itemHasValidity && !target.itemHasValidity)` وإلا `item.merge.error.validity`.

كل رسالة **مفتاح** في `messages.properties`، طبقًا لـ §5 من `new-code-rules.md` — لا نص
عربي داخل خدمة.

---

## 4. البنية

### `account/features/itemmerge/` — بلا JavaFX، اختبار لكل كلاس

| الملف | المسؤولية |
|---|---|
| `ItemReferenceRegistry.java` | قائمة `ItemReference(table, column, action)` — مصدر الحقيقة الوحيد |
| `ItemReference.java` | سجل: الجدول، العمود، الإجراء |
| `MergeAction.java` | `MOVE`, `MERGE_ROW`, `MOVE_IF_ABSENT`, `MOVE_DEDUPE` |
| `ItemMergeStatements.java` | كل جملة SQL في مكان واحد، مبنية من السجل |
| `ItemMergeDao.java` | التنفيذ فقط — ما بيقرأش `CurrentUser` ولا `Preferences` |
| `MergeItem.java` | الستة أعمدة اللي بيتحاكم عليها الدمج — لا `ItemsModel` بصورته ووحداته |
| `ItemMergePreview.java` | سجل: عدد السطور لكل جدول + عدد السطور داخل فترة مقفلة |
| `ItemMergeResult.java` | ما نفّذه الدمج فعلًا: رقم صف السجل + المعاينة اللي اتاخدت جوّه الترانزاكشن |
| `ItemMergeService.java` | الصلاحية + القواعد + `TransactionTemplate.execute` |
| `ItemMergeCandidate.java` | صف مرشَّح للدمج، ومعه `canMergeInto` — نفس قواعد الرفض قبل أي استعلام |
| `MergeGroupBy.java` | الاسم / أول كلمة داخل المجموعة / نفس المجموعة والسعر |

`ItemMergeService` بشكل `record ItemMergeService(DaoFactory daoFactory)` — نفس شكل
`StockCountService` و`InventoryService`، وهو بالظبط ما بيحقنه Spring لاحقًا.

### الشاشة

| الملف | ملاحظة |
|---|---|
| `controller/items/MergeItemsController.java` | `@FxmlPath(pathFile = "items/merge-items-view.fxml")` |
| `resources/com/hamza/account/view/items/merge-items-view.fxml` | يعلن `fx:controller`، ويُحمَّل بـ `ResourceBundle` |
| `dash/ItemsButtons.mergeItems()` | زر بصلاحية `ITEMS_MERGE`، تبويب زي `stockCount()` |
| `mainScreen-view.fxml` + `MainScreenController` | `btnMergeItems` في قائمة الأصناف |
| `view/DownLoadApplication` | `ServiceRegistry.register(ItemMergeService.class, ...)` |
| `authorization/AppPermissions` | `ITEMS_MERGE = key("items.merge")` — ثابت واحد، بيتزامن عند الإقلاع |

أعمدة الجدول **تُبنى في الكود**، مش بـ `@ColumnData`.

---

## 5. الهجرة V17

`V17__item_merge_log.sql` — جدولان، بلا مفاتيح أجنبية على `items` ولا `users`: الصف
المصدر بيتحذف بحكم التعريف، والسجل لازم يعيش بعده، ومفتاح أجنبي على الهدف كان هيمنع
حذفه لاحقًا.

```sql
CREATE TABLE IF NOT EXISTS item_merge
(
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    target_item_id       INT                                 NOT NULL,
    target_item_name     VARCHAR(200)                        NOT NULL,
    source_item_id       INT                                 NOT NULL,
    source_item_name     VARCHAR(200)                        NOT NULL,
    source_barcode       VARCHAR(200)                        NULL,
    source_first_balance DECIMAL(14, 3) DEFAULT 0            NOT NULL,
    locked_period_lines  INT            DEFAULT 0            NOT NULL,
    merged_at            DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    user_id              INT            DEFAULT 1            NOT NULL,
    user_name            VARCHAR(50)                         NULL
);

CREATE TABLE IF NOT EXISTS item_merge_lines
(
    id         INT AUTO_INCREMENT PRIMARY KEY,
    merge_id   INT           NOT NULL,
    table_name VARCHAR(64)   NOT NULL,
    rows_moved INT DEFAULT 0 NOT NULL,
    CONSTRAINT item_merge_lines_merge_fk FOREIGN KEY (merge_id) REFERENCES item_merge (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);
```

العدد لكل جدول في **جدول أبناء** لا في أعمدة، عشان إضافة مرجع جديد للسجل ما تحتاجش هجرة
تانية. المفتاح الوحيد هنا cascading، فما بيتعلنش في `DeleteRegistry`؛ و`WipeCatalog`
بياخد الجدولين مع هدف "الأصناف".

---

## 6. تسلسل التنفيذ

كله جوّه `TransactionTemplate.execute` واحد. لكل مصدر بالترتيب:

1. **قراءة**: الصنفان، وعدد السطور لكل جدول (المعاينة)، وعدد السطور داخل فترة مقفلة.
2. **القواعد** (§3). أي رفض بيلغي الترانزاكشن كلها — الدفعة كلها أو لا شيء.
3. **الحركات المباشرة** — جملة لكل جدول `MOVE`:
   ```sql
   UPDATE sales               SET num     = ? WHERE num     = ?;
   UPDATE sales_re            SET item_id = ? WHERE item_id = ?;
   UPDATE purchase            SET num     = ? WHERE num     = ?;
   UPDATE purchase_re         SET item_id = ? WHERE item_id = ?;
   UPDATE stock_movements     SET item_id = ? WHERE item_id = ?;
   UPDATE stock_transfer_list SET item_id = ? WHERE item_id = ?;
   ```
4. **سطور الجرد** — جمع، ثم حذف المتعارض، ثم نقل الباقي:
   ```sql
   UPDATE stock_count_lines t
       JOIN stock_count_lines s
         ON s.count_id = t.count_id AND s.unit_id = t.unit_id
            AND s.item_id = ? AND t.item_id = ?
   SET t.system_qty  = t.system_qty  + s.system_qty,
       t.counted_qty = t.counted_qty + s.counted_qty;

   DELETE s FROM stock_count_lines s
       JOIN stock_count_lines t
         ON t.count_id = s.count_id AND t.unit_id = s.unit_id AND t.item_id = ?
   WHERE s.item_id = ?;

   UPDATE stock_count_lines SET item_id = ? WHERE item_id = ?;
   ```
   الجمع بيحفظ الفرق بالظبط: الـ view بتحسب `counted_qty * type_value - system_qty`،
   و`type_value` واحد لأن المفتاح بيضم `unit_id`.
5. **`items_stock`**: يُدرَج صف للهدف لكل مخزن كان للمصدر ومالوش صف فيه. صفوف المصدر
   بتروح بالـ cascade. (عمليًا مخزن واحد، لكن القاعدة مكتوبة صح.)
6. **الوحدات**: **نقل** لا نسخ — النقل بياخد الباركود معاه فما فيش تعارض مع
   `UNIQUE(items_barcode)`:
   ```sql
   UPDATE items_units s
       LEFT JOIN items_units t ON t.items_id = ? AND t.unit = s.unit
   SET s.items_id = ?
   WHERE s.items_id = ? AND t.id IS NULL AND s.unit <> ?;   -- <> وحدة الهدف الأساسية
   ```
   اللي فضل (الهدف عنده وحدة زيها) بيتحذف بالـ cascade بعد إنقاذ باركوده في الخطوة اللي بعدها.
7. **الباركودات**: كل كود لسه المصدر ماسكه (`items.barcode`، صفوف `item_barcodes`،
   وباركودات وحداته المتبقية) يتحط في `item_barcodes` للهدف، بشرط إن الكود مش مأخوذ في
   أي من الجداول الثلاثة لصنف تاني. الترتيب مُلزِم: **قبل** حذف المصدر، لأن
   `item_barcodes` بيتحذف بالـ cascade.
8. **الرصيد الافتتاحي**:
   ```sql
   UPDATE items SET first_balance = first_balance + ? WHERE id = ?;
   ```
   القيمة تُقرأ في Java أولًا — MySQL ما بتسمحش بـ subquery على `items` جوّه UPDATE عليها.
9. **العبوات** (`items_package`): نقل العمودين، حذف المكرر، وحذف أي صف بقى مرجعًا ذاتيًا
   (`item_id = package_id`).
10. **السجل**: صف في `item_merge` + صف لكل جدول في `item_merge_lines`.
11. **الحذف**: المصدر عبر `DeletionService` بقاعدة `DeleteRegistry.ITEMS` — بقى بلا مراجع،
    فلو رفض يبقى فيه مرجع مش معلن، والترانزاكشن بتتلغي. ده فحص إضافي مجاني.
12. بعد نجاح الترانزاكشن: `EventBus.publish(new ItemsChanged())`.

---

## 7. الشاشة

تبويب "دمج الأصناف" جنب الجرد والمخزون:

- **المرشحون**: مجموعات أصناف متشابهة — تجميع بالاسم بعد التطبيع (مسافات، `أ إ آ` ← `ا`،
  `ة` ← `ه`، `ى` ← `ي`) أو نفس `sub_num` مع نفس `sel_price1`، بشرط `COUNT(*) > 1`. لكل صف:
  عدد حركات البيع والشراء وآخر حركة.
- **بحث يدوي**: صنفان بالاسم أو الباركود، لمن يعرف بالظبط هو عايز إيه.
- **العمليات**: جدول من `card_item_view` عبر `CardItemService` القائم — بلا استعلام جديد.
- **المعاينة**: "هينتقل ١٢٤ سطر بيع، ٣١ شراء، ٤ مرتجع… منهم ٤٢ داخل فترة مقفلة"، والرصيد
  الافتتاحي اللي هيتضاف، وقائمة الباركودات اللي هتنتقل.
- **تنفيذ**: تأكيد باسم الصنفين، ثم `Task` في الخلفية، ثم رسالة نجاح وتحديث.
- دفعة: أكتر من مصدر لهدف واحد في ترانزاكشن واحدة.

---

## 8. الاختبارات

| الاختبار | يمنع |
|---|---|
| `ItemReferenceRegistryTest` | مرجع جديد لـ `items` في السكيما غير معلن في السجل — يقرأ المفاتيح من ملفات الهجرة ويفشل البناء |
| `ItemMergeStatementsTest` | تغيّر جملة SQL بلا قصد — تثبيت حرفًا بحرف + عدد الوسائط، زي `DocumentDaoStatementsTest` |
| `ItemMergeRulesTest` | خرق قاعدة رفض (الوحدة، الصلاحية، المصدر = الهدف) — Java خالص |
| `ItemMergePreviewTest` | عدّ خاطئ في المعاينة |
| `ItemMergeDatabaseAcceptanceTest` | خلف `-Daccount.db.acceptance=true`: صنفان بفواتير حقيقية، دمج، ثم التحقق إن رصيد الهدف في `quantity_items_table` = مجموع الرصيدين، وعدد سطور `card_item_view` = المجموع، والمصدر اختفى |

الأخير هو الوحيد اللي بيثبت الغرض كله، فيتشغّل يدويًا قبل أي إصدار فيه الميزة دي.

---

## 9. ترتيب التنفيذ

1. ✅ `V17__item_merge_log.sql` + `ItemReferenceRegistry` + اختباره. (السكيما والإعلان أولًا.)
2. ✅ `ItemMergeStatements` + `ItemMergeDao` + `ItemMergeService` + اختباراتها. **بلا واجهة** —
   النصف الخطر كله قابل للاختبار من غير JavaFX.
3. ✅ `ITEMS_MERGE` + التسجيل في `DownLoadApplication` + مفاتيح الرسائل (عربي وإنجليزي).
   اتعمل مع الخطوة ٢ لأن الخدمة ما بتترجمش من غيره.
4. ✅ الشاشة: FXML + المتحكم + زر القائمة + استعلام المرشحين.
5. ⏳ اختبار القبول على قاعدة حقيقية — **مكتوب، لم يُشغَّل**:
   ```bash
   mvn -o -pl account -am test "-Dtest=ItemMergeDatabaseAcceptanceTest" "-Daccount.db.acceptance=true" "-Dsurefire.failIfNoSpecifiedTests=false"
   ```
   يشتغل كله داخل ترانزاكشن واحدة يتم التراجع عنها دائمًا، فقاعدة البيانات تفضل زي ما هي.
6. قسم في `CLAUDE.md` — الميزة بتضيف حزمة وجدولين ومفتاح صلاحية، والملف ده هو اللي بيقول
   للي بعدنا إن المراجع مُعلَنة في مكان واحد.

---

## 10. خارج النطاق

- **التراجع عن دمج**: السجل بيحفظ إن الدمج حصل وكام سطر، مش أرقام السطور. التراجع الحقيقي
  محتاج تخزين المعرّفات؛ لو اتطلب لاحقًا، هو جدول أبناء تالت لا أكتر.
- **تحويل الوحدات عند الدمج**: مرفوض بقرار (§1، بند 2).
- **دمج العملاء أو الموردين**: نفس الفكرة بالظبط وممكن تتبني على نفس السجل، لكن مش دلوقتي.
