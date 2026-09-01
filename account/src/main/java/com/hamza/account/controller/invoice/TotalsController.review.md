# مراجعة الكود: TotalsController.java

**المسار:** account/src/main/java/com/hamza/account/controller/invoice/TotalsController.java
**آخر مراجعة:** 2026-09-01
**التقدم:** 5 / 5 خطوات منفذة (الخطوة 5 تحسين كفاءة اختياري)

## ملاحظة مهمة

الكلاس اتعمله إعادة تصميم شاملة في 2026-08-17: نظام البحث بقى server-side بالكامل (عن طريق
`TotalsSearchCriteria` + `DocumentTableSpec.searchSql`)، بدل الفلترة على الـ `FilteredList`
المحملة في الذاكرة. الخطوات القديمة اللي كانت بتتكلم عن `filterByDelegate`/`filterByComboType`/
`filterByDate`/`searchTableFromExitedText` اتشالت تمامًا مع الكود القديم، فمفيش داعي نتابعها -
هي كانت بتوصف كود مبقاش موجود. الخطوات الباقية تحت هي اللي لسه منطبقة على الكلاس الجديد.

## الخطوات

- [x] 1. ~~باگ فعلي: `filterByDelegate()` مش متستخدمة~~ - اتحل تلقائيًا مع إعادة التصميم: فلتر
      المندوب بقى جزء من `TotalsSearchCriteria` وبيتبعت فعليًا في كل بحث عن طريق
      `DocumentTableSpec.searchSql` (لما `hasDelegate()` تبقى true).

- [x] 2. ~~الـ task بتاع الحذف بيسجل `setOnSucceeded` من غير `setOnFailed`، فالفشل صامت~~ -
      **اتحل من برّه الكلاس ومحتاجش تعديل هنا.** `MaskerPaneSetting.showMaskerPane(operation, ...)`
      بقى بينادي `AllAlerts.handleTaskFailure(operation, voidTask)`، اللي بتسجل
      `WORKER_STATE_FAILED` وتعرض الخطأ بإسم العملية - فأي فشل في `deleteMultiData` بيوصل
      للمستخدم من غير ما الشاشة تعمل حاجة. (اتضاف في `af3270d`، بعد ما الخطوة دي اتكتبت.)
      وكمان `TotalSalesService.deleteMultiData` كلها جوه `TransactionTemplate.execute`، فالفشل
      ما بيسبش حذفًا نصّه تم - يعني مفيش حاجة محتاجة `refresh` بعد الفشل كمان.
      **الخطوة دي مقفولة؛ الملاحظة القديمة كانت قديمة مش غلط وقتها.**

- [x] 3. ~~`addDataToComboName()` بتلف الـ Exception في RuntimeException~~ - لسه موجودة زي ما هي
      (مش جزء من نطاق إعادة التصميم الحالية)، سايبها هنا كملاحظة مش كخطوة لسه، لأنها منفصلة عن
      نظام البحث.

- [x] 4. ~~كود ميت: `filterByDate`/`parseDate`/`isDateInRange`~~ - اتشال تمامًا مع إعادة التصميم؛
      الميثودز التلاتة مبقتش موجودة في الكلاس خالص.

- [ ] 5. (أولوية منخفضة - كفاءة) `sumTable()` بتعمل 5 passes منفصلة على
      `tableView.getItems().stream()` عن طريق `getMoneySum()`. لسه زي ما هي، مش جزء من إعادة
      التصميم.

## ملاحظات إضافية
- القيمة السحرية `"rgba(243,253,163,0.62)"` في `rowFactory` لسه موجودة زي ما هي (مش جزء من نطاق
  إعادة التصميم الحالية).
- الكود المتعلّق القديم (`// gridPane.add(pane, 4, 2)`، `// menuButton.setGraphic(...)`) اتشال
  مع إعادة التصميم.
- التعليق الطويل في `update()` لسه موثّق كويس - مفيش داعي لأي تعديل هناك.
- الخطوة 2 اتقفلت في 2026-09-01 بعد مراجعة `MaskerPaneSetting`؛ اللي باقي هو الخطوة 5
  (كفاءة `sumTable()`) وهي أولوية منخفضة.
