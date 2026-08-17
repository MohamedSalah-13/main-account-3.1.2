# مراجعة الكود: TotalsController.java

**المسار:** account/src/main/java/com/hamza/account/controller/invoice/TotalsController.java
**آخر مراجعة:** 2026-08-17
**التقدم:** 4 / 5 خطوات منفذة

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

- [ ] 2. **لسه قائم:** الـ task بتاع الحذف (`btnDelete.setOnAction`) بيسجل `setOnSucceeded` بس على
      `maskerPaneSetting.getVoidTask()` من غير `setOnFailed`. لو `deleteMultiData` رمى exception
      جوه الـ background task، المستخدم مش هياخد أي رسالة خطأ - الفشل بيبقى صامت، بعكس
      `search()` الجديدة اللي بقت بتمسك الـ exception وتعرضه عن طريق `exceptionHandle` جوه
      `Platform.runLater`.

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
- الخطوة 2 هي الأهم اللي لسه باقية.
