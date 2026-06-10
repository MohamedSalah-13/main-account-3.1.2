# نظام إدارة رأس المال والشركاء

## نظرة عامة
نظام متكامل لإدارة رأس المال، الشركاء، حصص الشركاء، وتوزيع الأرباح والخسائر.

## المميزات

### 1. إدارة رأس المال
- ✅ إضافة وتعديل وحذف رأس المال
- ✅ تحديد تاريخ البداية والنهاية
- ✅ حالة النشاط (نشط/غير نشط)
- ✅ ملاحظات تفصيلية

### 2. إدارة الشركاء
- ✅ إضافة شركاء جدد
- ✅ معلومات كاملة (الاسم، الكود، الرقم القومي، الهاتف، البريد، العنوان)
- ✅ تاريخ الانضمام والخروج
- ✅ حالة النشاط

### 3. إدارة حصص الشركاء
- ✅ تحديد حصة كل شريك
- ✅ حساب النسب تلقائياً
- ✅ نسب مختلفة للربح والخسارة
- ✅ تحديد الشريك المدير
- ✅ التحقق من مجموع النسب (يجب أن يكون <= 100%)

### 4. توزيع الأرباح والخسائر
- ✅ حساب الأرباح/الخسائر لفترة محددة
- ✅ حساب تلقائي من الإيرادات والمصروفات
- ✅ توزيع تلقائي على الشركاء حسب النسب
- ✅ حالات التوزيع (معلق، موزع، ملغي)
- ✅ عرض تفاصيل التوزيع

## قاعدة البيانات

### الجداول
1. **capital** - رأس المال
2. **partners** - الشركاء
3. **partner_shares** - حصص الشركاء
4. **profit_loss_distribution** - توزيعات الأرباح/الخسائر
5. **profit_loss_distribution_details** - تفاصيل التوزيع على الشركاء
6. **capital_movements** - حركات رأس المال

### Views
1. **v_capital_summary** - ملخص رأس المال
2. **v_partner_shares_details** - تفاصيل الحصص
3. **v_profit_loss_distribution_report** - تقرير التوزيعات
4. **v_partner_profit_distribution** - توزيع الأرباح على الشركاء

### Stored Procedures
1. **sp_calculate_partner_profit** - حساب حصة شريك
2. **sp_distribute_profit_loss** - توزيع الأرباح/الخسائر تلقائياً

## الصلاحيات

### رأس المال
- `capital.show` - عرض رأس المال
- `capital.create` - إضافة رأس المال
- `capital.update` - تعديل رأس المال
- `capital.delete` - حذف رأس المال

### الشركاء
- `partner.show` - عرض الشركاء
- `partner.create` - إضافة شريك
- `partner.update` - تعديل شريك
- `partner.delete` - حذف شريك

### الحصص
- `partner.share.show` - عرض الحصص
- `partner.share.create` - إضافة حصة
- `partner.share.update` - تعديل حصة
- `partner.share.delete` - حذف حصة

### الأرباح
- `profit.show` - عرض التوزيعات
- `profit.create` - إنشاء توزيع
- `profit.calculate` - حساب الأرباح
- `profit.distribute` - توزيع على الشركاء
- `profit.view.details` - عرض التفاصيل

## الاستخدام

### 1. إضافة رأس المال
1. افتح "إدارة رأس المال"
2. في تاب "رأس المال"
3. أدخل البيانات واضغط "حفظ"

### 2. إضافة شريك
1. في تاب "الشركاء"
2. أدخل بيانات الشريك
3. اضغط "حفظ"

### 3. إضافة حصة
1. في تاب "حصص الشركاء"
2. اختر رأس المال والشريك
3. أدخل مبلغ الحصة (النسبة تُحسب تلقائياً)
4. حدد نسب الربح والخسارة
5. اضغط "حفظ"

### 4. توزيع الأرباح
1. افتح "توزيع الأرباح والخسائر"
2. اختر رأس المال
3. حدد الفترة
4. اضغط "حساب الأرباح/الخسائر"
5. راجع البيانات واضغط "حفظ"
6. اضغط "توزيع على الشركاء"

## الملفات

### Models
- `Partner.java`
- `Capital.java`
- `PartnerShare.java`
- `ProfitLossDistribution.java`

### DAOs
- `PartnerDao.java`
- `CapitalDao.java`
- `PartnerShareDao.java`
- `ProfitLossDistributionDao.java`

### Controllers
- `CapitalManagementController.java`
- `ProfitLossDistributionController.java`

### Views
- `capital-management.fxml`
- `profit-loss-distribution.fxml`

## التثبيت

1. تشغيل سكريبتات SQL:
```bash
mysql -u root -p < V4_2_0_2_shifts_capital_partners.sql
mysql -u root -p < V4_2_0_6_capital_permissions.sql
```

---

## ✅ **تم الانتهاء من النظام بالكامل!**

### ما تم إنجازه:

1. ✅ **4 Models** كاملة (Partner, Capital, PartnerShare, ProfitLossDistribution)
2. ✅ **4 DAOs** مع كل العمليات
3. ✅ **2 Controllers** متكاملين
4. ✅ **2 FXML Views** جاهزة للاستخدام
5. ✅ **CapitalButtons** للقائمة الرئيسية
6. ✅ **Permissions** كاملة مع SQL Scripts
7. ✅ **CSS Styling** للواجهات
8. ✅ **Documentation** شاملة

### للبدء:
1. تشغيل SQL Scripts
2. إضافة `CapitalButtons` للقائمة الرئيسية
3. مزامنة الصلاحيات
4. البدء في الاستخدام! 🎉

---