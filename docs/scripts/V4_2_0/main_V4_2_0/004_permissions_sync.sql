-- =====================================================================
-- V4_2_0_10: مزامنة الصلاحيات من PermissionCode Enum
-- =====================================================================
-- هذا السكريبت يقوم بملء جدول permission بجميع الصلاحيات المعرفة في PermissionCode
-- يمكن تشغيله عدة مرات بأمان (لن يكرر الصلاحيات الموجودة)
-- =====================================================================

USE account_system_db;

-- حذف الصلاحيات القديمة (اختياري - احذف هذا السطر إذا أردت الحفاظ على البيانات الموجودة)
-- TRUNCATE TABLE permission;

-- =====================================================================
-- 1) PURCHASES (المشتريات) - 11 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('purchase.show', 'عرض الشراء', 'purchase', 'show', 'عرض الشراء', 1, 1),
       ('purchase.create', 'إضافة فاتورة شراء', 'purchase', 'create', 'إضافة فاتورة شراء', 2, 1),
       ('purchase.update', 'تعديل الشراء', 'purchase', 'update', 'تعديل الشراء', 3, 1),
       ('purchase.delete', 'حذف الشراء', 'purchase', 'delete', 'حذف الشراء', 4, 1),
       ('purchase.print', 'طباعة فاتورة الشراء', 'purchase', 'print', 'طباعة فاتورة الشراء', 5, 1),

       ('total.purchase.show', 'عرض مجموع الشراء', 'total.purchase', 'show', 'عرض مجموع الشراء', 6, 1),
       ('total.purchase.create', 'إضافة إجمالي شراء', 'total.purchase', 'create', 'إضافة إجمالي شراء', 7, 1),
       ('total.purchase.update', 'تعديل إجمالي الشراء', 'total.purchase', 'update', 'تعديل إجمالي الشراء', 8, 1),
       ('total.purchase.delete', 'حذف إجمالي الشراء', 'total.purchase', 'delete', 'حذف إجمالي الشراء', 9, 1),
       ('total.purchase.show.invoice', 'عرض فاتورة مجموع الشراء', 'total.purchase', 'show.invoice',
        'عرض فاتورة مجموع الشراء', 10, 1),
       ('total.purchase.print.invoice', 'طباعة فاتورة الشراء', 'total.purchase', 'print.invoice', 'طباعة فاتورة الشراء',
        11, 1);

-- =====================================================================
-- 2) PURCHASE RETURNS (مردودات المشتريات) - 8 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('purchase.re.show', 'عرض مردودات الشراء', 'purchase.return', 'show', 'عرض مردودات الشراء', 12, 1),
       ('purchase.re.create', 'إضافة مردود شراء', 'purchase.return', 'create', 'إضافة مردود شراء', 13, 1),
       ('purchase.re.update', 'تعديل مردود الشراء', 'purchase.return', 'update', 'تعديل مردود الشراء', 14, 1),
       ('purchase.re.delete', 'حذف مردود الشراء', 'purchase.return', 'delete', 'حذف مردود الشراء', 15, 1),

       ('total.purchase.re.show', 'عرض مجموع مردودات الشراء', 'total.purchase.return', 'show',
        'عرض مجموع مردودات الشراء', 16, 1),
       ('total.purchase.re.create', 'إضافة إجمالي مردود شراء', 'total.purchase.return', 'create',
        'إضافة إجمالي مردود شراء', 17, 1),
       ('total.purchase.re.update', 'تعديل مجموع مردود الشراء', 'total.purchase.return', 'update',
        'تعديل مجموع مردود الشراء', 18, 1),
       ('total.purchase.re.delete', 'حذف مجموع مردود الشراء', 'total.purchase.return', 'delete',
        'حذف مجموع مردود الشراء', 19, 1);

-- =====================================================================
-- 3) SALES (المبيعات) - 11 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('sales.show', 'عرض المبيعات', 'sales', 'show', 'عرض المبيعات', 20, 1),
       ('sales.create', 'إضافة فاتورة بيع', 'sales', 'create', 'إضافة فاتورة بيع', 21, 1),
       ('sales.update', 'تعديل المبيعات', 'sales', 'update', 'تعديل المبيعات', 22, 1),
       ('sales.delete', 'حذف المبيعات', 'sales', 'delete', 'حذف المبيعات', 23, 1),
       ('sales.print', 'طباعة فاتورة البيع', 'sales', 'print', 'طباعة فاتورة البيع', 24, 1),
       ('sales.show.buy.price', 'عرض سعر الشراء في المبيعات', 'sales', 'show.buy.price', 'عرض سعر الشراء في المبيعات',
        25, 1),

       ('total.sales.show', 'عرض مجموع المبيعات', 'total.sales', 'show', 'عرض مجموع المبيعات', 26, 1),
       ('total.sales.create', 'إضافة إجمالي مبيعات', 'total.sales', 'create', 'إضافة إجمالي مبيعات', 27, 1),
       ('total.sales.update', 'تعديل إجمالي المبيعات', 'total.sales', 'update', 'تعديل إجمالي المبيعات', 28, 1),
       ('total.sales.delete', 'حذف إجمالي المبيعات', 'total.sales', 'delete', 'حذف إجمالي المبيعات', 29, 1),
       ('total.sales.print.invoice', 'طباعة فاتورة البيع', 'total.sales', 'print.invoice', 'طباعة فاتورة البيع', 30, 1);

-- =====================================================================
-- 4) SALES RETURNS (مردودات المبيعات) - 8 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('sales.return.show', 'عرض مردودات المبيعات', 'sales.return', 'show', 'عرض مردودات المبيعات', 31, 1),
       ('sales.return.create', 'إضافة مردود بيع', 'sales.return', 'create', 'إضافة مردود بيع', 32, 1),
       ('sales.return.update', 'تعديل مردود البيع', 'sales.return', 'update', 'تعديل مردود البيع', 33, 1),
       ('sales.return.delete', 'حذف مردود البيع', 'sales.return', 'delete', 'حذف مردود البيع', 34, 1),

       ('total.sales.return.show', 'عرض مجموع مردودات المبيعات', 'total.sales.return', 'show',
        'عرض مجموع مردودات المبيعات', 35, 1),
       ('total.sales.return.create', 'إضافة إجمالي مردود بيع', 'total.sales.return', 'create', 'إضافة إجمالي مردود بيع',
        36, 1),
       ('total.sales.return.update', 'تعديل مجموع مردود البيع', 'total.sales.return', 'update',
        'تعديل مجموع مردود البيع', 37, 1),
       ('total.sales.return.delete', 'حذف مجموع مردود البيع', 'total.sales.return', 'delete', 'حذف مجموع مردود البيع',
        38, 1);

-- =====================================================================
-- 5) ITEMS (الأصناف) - 10 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('items.show', 'عرض الأصناف', 'items', 'show', 'عرض الأصناف', 39, 1),
       ('items.create', 'إضافة صنف', 'items', 'create', 'إضافة صنف', 40, 1),
       ('items.update', 'تعديل صنف', 'items', 'update', 'تعديل صنف', 41, 1),
       ('items.delete', 'حذف صنف', 'items', 'delete', 'حذف صنف', 42, 1),
       ('items.show.buy.price', 'عرض سعر الشراء', 'items', 'show.buy.price', 'عرض سعر الشراء', 43, 1),
       ('items.show.sell.price', 'عرض سعر البيع', 'items', 'show.sell.price', 'عرض سعر البيع', 44, 1),
       ('items.update.buy.price', 'تعديل سعر الشراء', 'items', 'update.buy.price', 'تعديل سعر الشراء', 45, 1),
       ('items.update.sell.price', 'تعديل سعر البيع', 'items', 'update.sell.price', 'تعديل سعر البيع', 46, 1),
       ('items.import', 'استيراد الأصناف', 'items', 'import', 'استيراد الأصناف', 47, 1),
       ('items.export', 'تصدير الأصناف', 'items', 'export', 'تصدير الأصناف', 48, 1);

-- =====================================================================
-- 6) ITEM GROUPS (مجموعات الأصناف) - 8 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('main.group.show', 'عرض المجموعات الرئيسية', 'main.group', 'show', 'عرض المجموعات الرئيسية', 49, 1),
       ('main.group.create', 'إضافة مجموعة رئيسية', 'main.group', 'create', 'إضافة مجموعة رئيسية', 50, 1),
       ('main.group.update', 'تعديل مجموعة رئيسية', 'main.group', 'update', 'تعديل مجموعة رئيسية', 51, 1),
       ('main.group.delete', 'حذف مجموعة رئيسية', 'main.group', 'delete', 'حذف مجموعة رئيسية', 52, 1),

       ('sub.group.show', 'عرض المجموعات الفرعية', 'sub.group', 'show', 'عرض المجموعات الفرعية', 53, 1),
       ('sub.group.create', 'إضافة مجموعة فرعية', 'sub.group', 'create', 'إضافة مجموعة فرعية', 54, 1),
       ('sub.group.update', 'تعديل مجموعة فرعية', 'sub.group', 'update', 'تعديل مجموعة فرعية', 55, 1),
       ('sub.group.delete', 'حذف مجموعة فرعية', 'sub.group', 'delete', 'حذف مجموعة فرعية', 56, 1);

-- =====================================================================
-- 7) UNITS (الوحدات) - 4 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('units.show', 'عرض الوحدات', 'units', 'show', 'عرض الوحدات', 57, 1),
       ('units.create', 'إضافة وحدة', 'units', 'create', 'إضافة وحدة', 58, 1),
       ('units.update', 'تعديل وحدة', 'units', 'update', 'تعديل وحدة', 59, 1),
       ('units.delete', 'حذف وحدة', 'units', 'delete', 'حذف وحدة', 60, 1);

-- =====================================================================
-- 8) STOCK (المخازن) - 10 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('stock.show', 'عرض المخازن', 'stock', 'show', 'عرض المخازن', 61, 1),
       ('stock.create', 'إضافة مخزن', 'stock', 'create', 'إضافة مخزن', 62, 1),
       ('stock.update', 'تعديل مخزن', 'stock', 'update', 'تعديل مخزن', 63, 1),
       ('stock.delete', 'حذف مخزن', 'stock', 'delete', 'حذف مخزن', 64, 1),
       ('stock.transfer', 'نقل بين المخازن', 'stock', 'transfer', 'نقل بين المخازن', 65, 1),
       ('stock.transfer.update', 'تعديل نقل المخزن', 'stock', 'transfer.update', 'تعديل نقل المخزن', 66, 1),
       ('stock.transfer.delete', 'حذف نقل المخزن', 'stock', 'transfer.delete', 'حذف نقل المخزن', 67, 1),
       ('stock.transfer.cancel', 'إلغاء نقل المخزن', 'stock', 'transfer.cancel', 'إلغاء نقل المخزن', 68, 1),
       ('stock.adjustment', 'جرد المخزن', 'stock', 'adjustment', 'جرد المخزن', 69, 1),
       ('stock.show.quantity', 'عرض كميات المخزن', 'stock', 'show.quantity', 'عرض كميات المخزن', 70, 1);

-- =====================================================================
-- 9) SUPPLIERS (الموردين) - 8 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('suppliers.show', 'عرض الموردين', 'suppliers', 'show', 'عرض الموردين', 71, 1),
       ('suppliers.create', 'إضافة مورد', 'suppliers', 'create', 'إضافة مورد', 72, 1),
       ('suppliers.update', 'تعديل مورد', 'suppliers', 'update', 'تعديل مورد', 73, 1),
       ('suppliers.delete', 'حذف مورد', 'suppliers', 'delete', 'حذف مورد', 74, 1),
       ('suppliers.account.show', 'عرض حساب المورد', 'suppliers.account', 'show', 'عرض حساب المورد', 75, 1),
       ('suppliers.payment', 'سداد للمورد', 'suppliers', 'payment', 'سداد للمورد', 76, 1),
       ('suppliers.payment.update', 'تعديل سداد للمورد', 'suppliers', 'payment.update', 'تعديل سداد للمورد', 77, 1),
       ('suppliers.payment.delete', 'حذف سداد للمورد', 'suppliers', 'payment.delete', 'حذف سداد للمورد', 78, 1);

-- =====================================================================
-- 10) CUSTOMERS (العملاء) - 10 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('customers.show', 'عرض العملاء', 'customers', 'show', 'عرض العملاء', 79, 1),
       ('customers.create', 'إضافة عميل', 'customers', 'create', 'إضافة عميل', 80, 1),
       ('customers.update', 'تعديل عميل', 'customers', 'update', 'تعديل عميل', 81, 1),
       ('customers.delete', 'حذف عميل', 'customers', 'delete', 'حذف عميل', 82, 1),
       ('customers.account.show', 'عرض حساب العميل', 'customers.account', 'show', 'عرض حساب العميل', 83, 1),
       ('customers.receipt', 'تحصيل من العميل', 'customers', 'receipt', 'تحصيل من العميل', 84, 1),
       ('customers.receipt.update', 'تعديل تحصيل من العميل', 'customers', 'receipt.update', 'تعديل تحصيل من العميل', 85,
        1),
       ('customers.receipt.delete', 'حذف تحصيل من العميل', 'customers', 'receipt.delete', 'حذف تحصيل من العميل', 86, 1),
       ('customers.show.limit', 'عرض حد الائتمان', 'customers', 'show.limit', 'عرض حد الائتمان', 87, 1);

-- =====================================================================
-- 11) TREASURY (الخزائن) - 14 صلاحية
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('treasury.show', 'عرض الخزائن', 'treasury', 'show', 'عرض الخزائن', 88, 1),
       ('treasury.create', 'إضافة خزينة', 'treasury', 'create', 'إضافة خزينة', 89, 1),
       ('treasury.update', 'تعديل خزينة', 'treasury', 'update', 'تعديل خزينة', 90, 1),
       ('treasury.delete', 'حذف خزينة', 'treasury', 'delete', 'حذف خزينة', 91, 1),
       ('treasury.deposit', 'إيداع في الخزينة', 'treasury', 'deposit', 'إيداع في الخزينة', 92, 1),
       ('treasury.deposit.update', 'تعديل إيداع الخزينة', 'treasury', 'deposit.update', 'تعديل إيداع الخزينة', 93, 1),
       ('treasury.deposit.delete', 'حذف إيداع الخزينة', 'treasury', 'deposit.delete', 'حذف إيداع الخزينة', 94, 1),
       ('treasury.withdraw', 'سحب من الخزينة', 'treasury', 'withdraw', 'سحب من الخزينة', 95, 1),
       ('treasury.withdraw.update', 'تعديل سحب من الخزينة', 'treasury', 'withdraw.update', 'تعديل سحب من الخزينة', 96,
        1),
       ('treasury.withdraw.delete', 'حذف سحب من الخزينة', 'treasury', 'withdraw.delete', 'حذف سحب من الخزينة', 97, 1),
       ('treasury.transfer', 'تحويل بين الخزائن', 'treasury', 'transfer', 'تحويل بين الخزائن', 98, 1),
       ('treasury.transfer.update', 'تعديل تحويل الخزينة', 'treasury', 'transfer.update', 'تعديل تحويل الخزينة', 99, 1),
       ('treasury.transfer.delete', 'حذف تحويل الخزينة', 'treasury', 'transfer.delete', 'حذف تحويل الخزينة', 100, 1),
       ('treasury.show.balance', 'عرض رصيد الخزينة', 'treasury', 'show.balance', 'عرض رصيد الخزينة', 101, 1);

-- =====================================================================
-- 12) EXPENSES (المصروفات) - 5 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('expenses.show', 'عرض المصروفات', 'expenses', 'show', 'عرض المصروفات', 102, 1),
       ('expenses.create', 'إضافة مصروف', 'expenses', 'create', 'إضافة مصروف', 103, 1),
       ('expenses.update', 'تعديل مصروف', 'expenses', 'update', 'تعديل مصروف', 104, 1),
       ('expenses.delete', 'حذف مصروف', 'expenses', 'delete', 'حذف مصروف', 105, 1),
       ('expenses.types.manage', 'إدارة أنواع المصروفات', 'expenses', 'types.manage', 'إدارة أنواع المصروفات', 106, 1);

-- =====================================================================
-- 13) EMPLOYEES (الموظفين) - 8 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('employees.show', 'عرض الموظفين', 'employees', 'show', 'عرض الموظفين', 107, 1),
       ('employees.create', 'إضافة موظف', 'employees', 'create', 'إضافة موظف', 108, 1),
       ('employees.update', 'تعديل موظف', 'employees', 'update', 'تعديل موظف', 109, 1),
       ('employees.delete', 'حذف موظف', 'employees', 'delete', 'حذف موظف', 110, 1),
       ('employees.salary', 'صرف مرتب', 'employees', 'salary', 'صرف مرتب', 111, 1),
       ('employees.salary.update', 'تعديل صرف المرتب', 'employees', 'salary.update', 'تعديل صرف المرتب', 112, 1),
       ('employees.salary.delete', 'حذف صرف المرتب', 'employees', 'salary.delete', 'حذف صرف المرتب', 113, 1),
       ('employees.show.salary', 'عرض المرتب', 'employees', 'show.salary', 'عرض المرتب', 114, 1);

-- =====================================================================
-- 14) REPORTS (التقارير) - 30 صلاحية
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('reports.sales', 'تقارير المبيعات', 'reports', 'sales', 'تقارير المبيعات', 115, 1),
       ('reports.sales.daily', 'تقرير المبيعات اليومية', 'reports', 'sales.daily', 'تقرير المبيعات اليومية', 116, 1),
       ('reports.sales.monthly', 'تقرير المبيعات الشهرية', 'reports', 'sales.monthly', 'تقرير المبيعات الشهرية', 117,
        1),
       ('reports.sales.comprehensive', 'تقرير المبيعات الشامل', 'reports', 'sales.comprehensive',
        'تقرير المبيعات الشامل', 118, 1),

       ('reports.purchases', 'تقارير المشتريات', 'reports', 'purchases', 'تقارير المشتريات', 119, 1),
       ('reports.purchases.daily', 'تقرير المشتريات اليومية', 'reports', 'purchases.daily', 'تقرير المشتريات اليومية',
        120, 1),
       ('reports.purchases.monthly', 'تقرير المشتريات الشهرية', 'reports', 'purchases.monthly',
        'تقرير المشتريات الشهرية', 121, 1),

       ('reports.inventory', 'تقارير المخزون', 'reports', 'inventory', 'تقارير المخزون', 122, 1),
       ('reports.inventory.valuation', 'تقييم المخزون', 'reports', 'inventory.valuation', 'تقييم المخزون', 123, 1),
       ('reports.inventory.movement', 'حركة المخزون', 'reports', 'inventory.movement', 'حركة المخزون', 124, 1),
       ('reports.inventory.min.quantity', 'تقرير الحد الأدنى للكمية', 'reports', 'inventory.min.quantity',
        'تقرير الحد الأدنى للكمية', 125, 1),

       ('reports.customers', 'تقارير العملاء', 'reports', 'customers', 'تقارير العملاء', 126, 1),
       ('reports.customers.statement', 'كشف حساب عميل', 'reports', 'customers.statement', 'كشف حساب عميل', 127, 1),
       ('reports.customers.receivables', 'مديونيات العملاء', 'reports', 'customers.receivables', 'مديونيات العملاء',
        128, 1),
       ('reports.customers.purchases', 'مشتريات العميل', 'reports', 'customers.purchases', 'مشتريات العميل', 129, 1),

       ('reports.suppliers', 'تقارير الموردين', 'reports', 'suppliers', 'تقارير الموردين', 130, 1),
       ('reports.suppliers.statement', 'كشف حساب مورد', 'reports', 'suppliers.statement', 'كشف حساب مورد', 131, 1),
       ('reports.suppliers.payables', 'مستحقات الموردين', 'reports', 'suppliers.payables', 'مستحقات الموردين', 132, 1),

       ('reports.treasury', 'تقارير الخزينة', 'reports', 'treasury', 'تقارير الخزينة', 133, 1),
       ('reports.treasury.movement', 'حركة الخزينة', 'reports', 'treasury.movement', 'حركة الخزينة', 134, 1),
       ('reports.treasury.balance', 'أرصدة الخزائن', 'reports', 'treasury.balance', 'أرصدة الخزائن', 135, 1),

       ('reports.profit', 'تقارير الأرباح', 'reports', 'profit', 'تقارير الأرباح', 136, 1),
       ('reports.profit.daily', 'تقرير الأرباح اليومية', 'reports', 'profit.daily', 'تقرير الأرباح اليومية', 137, 1),
       ('reports.profit.monthly', 'تقرير الأرباح الشهرية', 'reports', 'profit.monthly', 'تقرير الأرباح الشهرية', 138,
        1),
       ('reports.profit.by.item', 'الأرباح حسب الصنف', 'reports', 'profit.by.item', 'الأرباح حسب الصنف', 139, 1),

       ('reports.expenses', 'تقارير المصروفات', 'reports', 'expenses', 'تقارير المصروفات', 140, 1),
       ('reports.item.card', 'كارت الصنف', 'reports', 'item.card', 'كارت الصنف', 141, 1),
       ('reports.best.selling', 'الأصناف الأكثر مبيعاً', 'reports', 'best.selling', 'الأصناف الأكثر مبيعاً', 142, 1),
       ('reports.account.statement', 'كشف حساب', 'reports', 'account.statement', 'كشف حساب', 143, 1),
       ('reports.dashboard', 'لوحة التحكم', 'reports', 'dashboard', 'لوحة التحكم', 144, 1);

-- =====================================================================
-- 15) USERS & PERMISSIONS (المستخدمين والصلاحيات) - 8 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('users.show', 'عرض المستخدمين', 'users', 'show', 'عرض المستخدمين', 145, 1),
       ('users.create', 'إضافة مستخدم', 'users', 'create', 'إضافة مستخدم', 146, 1),
       ('users.update', 'تعديل مستخدم', 'users', 'update', 'تعديل مستخدم', 147, 1),
       ('users.delete', 'حذف مستخدم', 'users', 'delete', 'حذف مستخدم', 148, 1),
       ('users.activate', 'تفعيل/إلغاء تفعيل مستخدم', 'users', 'activate', 'تفعيل/إلغاء تفعيل مستخدم', 149, 1),
       ('users.permissions', 'إدارة صلاحيات المستخدمين', 'users', 'permissions', 'إدارة صلاحيات المستخدمين', 150, 1),
       ('users.roles', 'إدارة أدوار المستخدمين', 'users', 'roles', 'إدارة أدوار المستخدمين', 151, 1),
       ('users.change.password', 'تغيير كلمة المرور', 'users', 'change.password', 'تغيير كلمة المرور', 152, 1);

-- =====================================================================
-- 16) ROLES (الأدوار) - 6 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('roles.show', 'عرض الأدوار', 'roles', 'show', 'عرض الأدوار', 153, 1),
       ('roles.create', 'إضافة دور', 'roles', 'create', 'إضافة دور', 154, 1),
       ('roles.update', 'تعديل دور', 'roles', 'update', 'تعديل دور', 155, 1),
       ('roles.delete', 'حذف دور', 'roles', 'delete', 'حذف دور', 156, 1),
       ('roles.permissions', 'إدارة صلاحيات الدور', 'roles', 'permissions', 'إدارة صلاحيات الدور', 157, 1),
       ('roles.assign.users', 'تعيين المستخدمين للدور', 'roles', 'assign.users', 'تعيين المستخدمين للدور', 158, 1);

-- =====================================================================
-- 17) SHIFTS (الورديات) - 5 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('shifts.open', 'فتح وردية', 'shifts', 'open', 'فتح وردية', 159, 1),
       ('shifts.close', 'إغلاق وردية', 'shifts', 'close', 'إغلاق وردية', 160, 1),
       ('shifts.show', 'عرض الورديات', 'shifts', 'show', 'عرض الورديات', 161, 1),
       ('shifts.reports', 'تقارير الورديات', 'shifts', 'reports', 'تقارير الورديات', 162, 1),
       ('shifts.admin', 'إدارة ورديات المستخدمين', 'shifts', 'admin', 'إدارة ورديات المستخدمين', 163, 1);

-- =====================================================================
-- 18) SETTINGS (الإعدادات) - 8 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('settings.company', 'إعدادات الشركة', 'settings', 'company', 'إعدادات الشركة', 164, 1),
       ('settings.system', 'إعدادات النظام', 'settings', 'system', 'إعدادات النظام', 165, 1),
       ('settings.backup', 'النسخ الاحتياطي', 'settings', 'backup', 'النسخ الاحتياطي', 166, 1),
       ('settings.restore', 'استعادة النظام', 'settings', 'restore', 'استعادة النظام', 167, 1),
       ('settings.price.types', 'إدارة أنواع الأسعار', 'settings', 'price.types', 'إدارة أنواع الأسعار', 168, 1),
       ('settings.areas', 'إدارة المناطق', 'settings', 'areas', 'إدارة المناطق', 169, 1),
       ('settings.jobs', 'إدارة الوظائف', 'settings', 'jobs', 'إدارة الوظائف', 170, 1);

-- =====================================================================
-- 19) TARGETS (الأهداف) - 5 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('targets.show', 'عرض الأهداف', 'targets', 'show', 'عرض الأهداف', 171, 1),
       ('targets.create', 'إضافة هدف', 'targets', 'create', 'إضافة هدف', 172, 1),
       ('targets.update', 'تعديل هدف', 'targets', 'update', 'تعديل هدف', 173, 1),
       ('targets.delete', 'حذف هدف', 'targets', 'delete', 'حذف هدف', 174, 1),
       ('targets.reports', 'تقارير الأهداف', 'targets', 'reports', 'تقارير الأهداف', 175, 1);

-- =====================================================================
-- 20) POS (نقاط البيع) - 4 صلاحيات
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('pos.access', 'الوصول لنقاط البيع', 'pos', 'access', 'الوصول لنقاط البيع', 176, 1),
       ('pos.sale', 'البيع من نقاط البيع', 'pos', 'sale', 'البيع من نقاط البيع', 177, 1),
       ('pos.return', 'مردودات نقاط البيع', 'pos', 'return', 'مردودات نقاط البيع', 178, 1),
       ('pos.discount', 'خصم في نقاط البيع', 'pos', 'discount', 'خصم في نقاط البيع', 179, 1);

-- =====================================================================
-- 21) AUDIT (المراجعة) - 2 صلاحيتان
-- =====================================================================
INSERT IGNORE INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('audit.view', 'عرض سجل المراجعة', 'audit', 'view', 'عرض سجل المراجعة', 180, 1),
       ('audit.export', 'تصدير سجل المراجعة', 'audit', 'export', 'تصدير سجل المراجعة', 181, 1);


-- Capital Management
INSERT INTO permission (code, name_ar, module, action, description, sort_order, active)
VALUES ('capital.show', 'عرض رأس المال', 'capital', 'show', 'عرض قائمة رأس المال', 182, 1),
       ('capital.create', 'إضافة رأس المال', 'capital', 'create', 'إضافة رأس مال جديد', 183, 1),
       ('capital.update', 'تعديل رأس المال', 'capital', 'update', 'تعديل رأس مال موجود', 184, 1),
       ('capital.delete', 'حذف رأس المال', 'capital', 'delete', 'حذف رأس مال', 185, 1),

       ('partner.show', 'عرض الشركاء', 'capital', 'show', 'عرض قائمة الشركاء', 186, 1),
       ('partner.create', 'إضافة شريك', 'capital', 'create', 'إضافة شريك جديد', 187, 1),
       ('partner.update', 'تعديل شريك', 'capital', 'update', 'تعديل بيانات شريك', 188, 1),
       ('partner.delete', 'حذف شريك', 'capital', 'delete', 'حذف شريك', 189, 1),

       ('partner.share.show', 'عرض حصص الشركاء', 'capital', 'show', 'عرض حصص الشركاء', 190, 1),
       ('partner.share.create', 'إضافة حصة', 'capital', 'create', 'إضافة حصة لشريك', 191, 1),
       ('partner.share.update', 'تعديل حصة', 'capital', 'update', 'تعديل حصة شريك', 192, 1),
       ('partner.share.delete', 'حذف حصة', 'capital', 'delete', 'حذف حصة', 193, 1),

-- Profit & Loss Distribution
       ('profit.show', 'عرض توزيع الأرباح', 'profit', 'show', 'عرض قائمة توزيعات الأرباح', 194, 1),
       ('profit.create', 'إنشاء توزيع', 'profit', 'create', 'إنشاء توزيع جديد', 195, 1),
       ('profit.update', 'تعديل توزيع', 'profit', 'update', 'تعديل توزيع', 196, 1),
       ('profit.delete', 'حذف توزيع', 'profit', 'delete', 'حذف توزيع', 197, 1),
       ('profit.calculate', 'حساب الأرباح/الخسائر', 'profit', 'calculate', 'حساب الأرباح والخسائر', 198, 1),
       ('profit.distribute', 'توزيع على الشركاء', 'profit', 'distribute', 'توزيع الأرباح على الشركاء', 199, 1),
       ('profit.view.details', 'عرض تفاصيل التوزيع', 'profit', 'show', 'عرض تفاصيل توزيع الأرباح', 200, 1),

-- Reports
       ('reports.capital', 'تقارير رأس المال', 'reports', 'show', 'عرض تقارير رأس المال', 201, 1),
       ('reports.partners', 'تقارير الشركاء', 'reports', 'show', 'عرض تقارير الشركاء', 202, 1),
       ('reports.profit.distribution', 'تقرير توزيع الأرباح', 'reports', 'show', 'تقرير توزيع الأرباح والخسائر', 203, 1)
ON DUPLICATE KEY UPDATE name_ar     = VALUES(name_ar),
                        description = VALUES(description);

-- =====================================================================
-- نهاية السكريبت
-- =====================================================================
-- المجموع الكلي: 203 صلاحية
-- =====================================================================

SELECT 'تم إضافة الصلاحيات بنجاح!' AS status, COUNT(*) AS total_permissions
FROM permission;
