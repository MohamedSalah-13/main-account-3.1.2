
package com.hamza.account.type;

import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public enum PermissionCode {

    // ============= PURCHASES (المشتريات) =============
    PURCHASE_SHOW("purchase.show", "عرض الشراء", "purchase", "show"),
    PURCHASE_CREATE("purchase.create", "إضافة فاتورة شراء", "purchase", "create"),
    PURCHASE_UPDATE("purchase.update", "تعديل الشراء", "purchase", "update"),
    PURCHASE_DELETE("purchase.delete", "حذف الشراء", "purchase", "delete"),
    PURCHASE_PRINT("purchase.print", "طباعة فاتورة الشراء", "purchase", "print"),

    TOTAL_PURCHASE_SHOW("total.purchase.show", "عرض مجموع الشراء", "total.purchase", "show"),
    TOTAL_PURCHASE_CREATE("total.purchase.create", "إضافة إجمالي شراء", "total.purchase", "create"),
    TOTAL_PURCHASE_UPDATE("total.purchase.update", "تعديل إجمالي الشراء", "total.purchase", "update"),
    TOTAL_PURCHASE_DELETE("total.purchase.delete", "حذف إجمالي الشراء", "total.purchase", "delete"),
    TOTAL_PURCHASE_SHOW_INVOICE("total.purchase.show.invoice", "عرض فاتورة مجموع الشراء", "total.purchase", "show.invoice"),
    TOTAL_PURCHASE_PRINT_INVOICE("total.purchase.print.invoice", "طباعة فاتورة الشراء", "total.purchase", "print.invoice"),

    // ============= PURCHASE RETURNS (مردودات المشتريات) =============
    PURCHASE_RE_SHOW("purchase.re.show", "عرض مردودات الشراء", "purchase.return", "show"),
    PURCHASE_RE_CREATE("purchase.re.create", "إضافة مردود شراء", "purchase.return", "create"),
    PURCHASE_RE_UPDATE("purchase.re.update", "تعديل مردود الشراء", "purchase.return", "update"),
    PURCHASE_RE_DELETE("purchase.re.delete", "حذف مردود الشراء", "purchase.return", "delete"),

    TOTAL_PURCHASE_RE_SHOW("total.purchase.re.show", "عرض مجموع مردودات الشراء", "total.purchase.return", "show"),
    TOTAL_PURCHASE_RE_CREATE("total.purchase.re.create", "إضافة إجمالي مردود شراء", "total.purchase.return", "create"),
    TOTAL_PURCHASE_RE_UPDATE("total.purchase.re.update", "تعديل مجموع مردود الشراء", "total.purchase.return", "update"),
    TOTAL_PURCHASE_RE_DELETE("total.purchase.re.delete", "حذف مجموع مردود الشراء", "total.purchase.return", "delete"),

    // ============= SALES (المبيعات) =============
    SALES_SHOW("sales.show", "عرض المبيعات", "sales", "show"),
    SALES_CREATE("sales.create", "إضافة فاتورة بيع", "sales", "create"),
    SALES_UPDATE("sales.update", "تعديل المبيعات", "sales", "update"),
    SALES_DELETE("sales.delete", "حذف المبيعات", "sales", "delete"),
    SALES_PRINT("sales.print", "طباعة فاتورة البيع", "sales", "print"),
    SALES_SHOW_BUY_PRICE("sales.show.buy.price", "عرض سعر الشراء في المبيعات", "sales", "show.buy.price"),

    TOTAL_SALES_SHOW("total.sales.show", "عرض مجموع المبيعات", "total.sales", "show"),
    TOTAL_SALES_CREATE("total.sales.create", "إضافة إجمالي مبيعات", "total.sales", "create"),
    TOTAL_SALES_UPDATE("total.sales.update", "تعديل إجمالي المبيعات", "total.sales", "update"),
    TOTAL_SALES_DELETE("total.sales.delete", "حذف إجمالي المبيعات", "total.sales", "delete"),
    TOTAL_SALES_PRINT_INVOICE("total.sales.print.invoice", "طباعة فاتورة البيع", "total.sales", "print.invoice"),

    // ============= SALES RETURNS (مردودات المبيعات) =============
    SALES_RE_SHOW("sales.return.show", "عرض مردودات المبيعات", "sales.return", "show"),
    SALES_RE_CREATE("sales.return.create", "إضافة مردود بيع", "sales.return", "create"),
    SALES_RE_UPDATE("sales.return.update", "تعديل مردود البيع", "sales.return", "update"),
    SALES_RE_DELETE("sales.return.delete", "حذف مردود البيع", "sales.return", "delete"),

    TOTAL_SALES_RE_SHOW("total.sales.return.show", "عرض مجموع مردودات المبيعات", "total.sales.return", "show"),
    TOTAL_SALES_RE_CREATE("total.sales.return.create", "إضافة إجمالي مردود بيع", "total.sales.return", "create"),
    TOTAL_SALES_RE_UPDATE("total.sales.return.update", "تعديل مجموع مردود البيع", "total.sales.return", "update"),
    TOTAL_SALES_RE_DELETE("total.sales.return.delete", "حذف مجموع مردود البيع", "total.sales.return", "delete"),

    // ============= ITEMS (الأصناف) =============
    ITEMS_SHOW("items.show", "عرض الأصناف", "items", "show"),
    ITEMS_CREATE("items.create", "إضافة صنف", "items", "create"),
    ITEMS_UPDATE("items.update", "تعديل صنف", "items", "update"),
    ITEMS_DELETE("items.delete", "حذف صنف", "items", "delete"),
    ITEMS_SHOW_BUY_PRICE("items.show.buy.price", "عرض سعر الشراء", "items", "show.buy.price"),
    ITEMS_SHOW_SELL_PRICE("items.show.sell.price", "عرض سعر البيع", "items", "show.sell.price"),
    ITEMS_UPDATE_BUY_PRICE("items.update.buy.price", "تعديل سعر الشراء", "items", "update.buy.price"),
    ITEMS_UPDATE_SELL_PRICE("items.update.sell.price", "تعديل سعر البيع", "items", "update.sell.price"),
    ITEMS_IMPORT("items.import", "استيراد الأصناف", "items", "import"),
    ITEMS_EXPORT("items.export", "تصدير الأصناف", "items", "export"),

    // ============= ITEM GROUPS (مجموعات الأصناف) =============
    MAIN_GROUP_SHOW("main.group.show", "عرض المجموعات الرئيسية", "main.group", "show"),
    MAIN_GROUP_CREATE("main.group.create", "إضافة مجموعة رئيسية", "main.group", "create"),
    MAIN_GROUP_UPDATE("main.group.update", "تعديل مجموعة رئيسية", "main.group", "update"),
    MAIN_GROUP_DELETE("main.group.delete", "حذف مجموعة رئيسية", "main.group", "delete"),

    SUB_GROUP_SHOW("sub.group.show", "عرض المجموعات الفرعية", "sub.group", "show"),
    SUB_GROUP_CREATE("sub.group.create", "إضافة مجموعة فرعية", "sub.group", "create"),
    SUB_GROUP_UPDATE("sub.group.update", "تعديل مجموعة فرعية", "sub.group", "update"),
    SUB_GROUP_DELETE("sub.group.delete", "حذف مجموعة فرعية", "sub.group", "delete"),

    // ============= UNITS (الوحدات) =============
    UNITS_SHOW("units.show", "عرض الوحدات", "units", "show"),
    UNITS_CREATE("units.create", "إضافة وحدة", "units", "create"),
    UNITS_UPDATE("units.update", "تعديل وحدة", "units", "update"),
    UNITS_DELETE("units.delete", "حذف وحدة", "units", "delete"),

    // ============= STOCK (المخازن) =============
    STOCK_SHOW("stock.show", "عرض المخازن", "stock", "show"),
    STOCK_CREATE("stock.create", "إضافة مخزن", "stock", "create"),
    STOCK_UPDATE("stock.update", "تعديل مخزن", "stock", "update"),
    STOCK_DELETE("stock.delete", "حذف مخزن", "stock", "delete"),
    STOCK_TRANSFER("stock.transfer", "نقل بين المخازن", "stock", "transfer"),
    STOCK_TRANSFER_UPDATE("stock.transfer.update", "تعديل نقل المخزن", "stock", "transfer.update"),
    STOCK_TRANSFER_DELETE("stock.transfer.delete", "حذف نقل المخزن", "stock", "transfer.delete"),
    STOCK_TRANSFER_CANCEL("stock.transfer.cancel", "إلغاء نقل المخزن", "stock", "transfer.cancel"),
    STOCK_ADJUSTMENT("stock.adjustment", "جرد المخزن", "stock", "adjustment"),
    STOCK_SHOW_QUANTITY("stock.show.quantity", "عرض كميات المخزن", "stock", "show.quantity"),

    // ============= SUPPLIERS (الموردين) =============
    SUPPLIERS_SHOW("suppliers.show", "عرض الموردين", "suppliers", "show"),
    SUPPLIERS_CREATE("suppliers.create", "إضافة مورد", "suppliers", "create"),
    SUPPLIERS_UPDATE("suppliers.update", "تعديل مورد", "suppliers", "update"),
    SUPPLIERS_DELETE("suppliers.delete", "حذف مورد", "suppliers", "delete"),
    SUPPLIERS_ACCOUNT_SHOW("suppliers.account.show", "عرض حساب المورد", "suppliers.account", "show"),
    SUPPLIERS_PAYMENT("suppliers.payment", "سداد للمورد", "suppliers", "payment"),
    SUPPLIERS_PAYMENT_UPDATE("suppliers.payment.update", "تعديل سداد للمورد", "suppliers", "payment.update"),
    SUPPLIERS_PAYMENT_DELETE("suppliers.payment.delete", "حذف سداد للمورد", "suppliers", "payment.delete"),

    // ============= CUSTOMERS (العملاء) =============
    CUSTOMERS_SHOW("customers.show", "عرض العملاء", "customers", "show"),
    CUSTOMERS_CREATE("customers.create", "إضافة عميل", "customers", "create"),
    CUSTOMERS_UPDATE("customers.update", "تعديل عميل", "customers", "update"),
    CUSTOMERS_DELETE("customers.delete", "حذف عميل", "customers", "delete"),
    CUSTOMERS_ACCOUNT_SHOW("customers.account.show", "عرض حساب العميل", "customers.account", "show"),
    CUSTOMERS_RECEIPT("customers.receipt", "تحصيل من العميل", "customers", "receipt"),
    CUSTOMERS_RECEIPT_UPDATE("customers.receipt.update", "تعديل تحصيل من العميل", "customers", "receipt.update"),
    CUSTOMERS_RECEIPT_DELETE("customers.receipt.delete", "حذف تحصيل من العميل", "customers", "receipt.delete"),
    CUSTOMERS_SHOW_LIMIT("customers.show.limit", "عرض حد الائتمان", "customers", "show.limit"),

    // ============= TREASURY (الخزائن) =============
    TREASURY_SHOW("treasury.show", "عرض الخزائن", "treasury", "show"),
    TREASURY_CREATE("treasury.create", "إضافة خزينة", "treasury", "create"),
    TREASURY_UPDATE("treasury.update", "تعديل خزينة", "treasury", "update"),
    TREASURY_DELETE("treasury.delete", "حذف خزينة", "treasury", "delete"),
    TREASURY_DEPOSIT("treasury.deposit", "إيداع في الخزينة", "treasury", "deposit"),
    TREASURY_DEPOSIT_UPDATE("treasury.deposit.update", "تعديل إيداع الخزينة", "treasury", "deposit.update"),
    TREASURY_DEPOSIT_DELETE("treasury.deposit.delete", "حذف إيداع الخزينة", "treasury", "deposit.delete"),
    TREASURY_WITHDRAW("treasury.withdraw", "سحب من الخزينة", "treasury", "withdraw"),
    TREASURY_WITHDRAW_UPDATE("treasury.withdraw.update", "تعديل سحب من الخزينة", "treasury", "withdraw.update"),
    TREASURY_WITHDRAW_DELETE("treasury.withdraw.delete", "حذف سحب من الخزينة", "treasury", "withdraw.delete"),
    TREASURY_TRANSFER("treasury.transfer", "تحويل بين الخزائن", "treasury", "transfer"),
    TREASURY_TRANSFER_UPDATE("treasury.transfer.update", "تعديل تحويل الخزينة", "treasury", "transfer.update"),
    TREASURY_TRANSFER_DELETE("treasury.transfer.delete", "حذف تحويل الخزينة", "treasury", "transfer.delete"),
    TREASURY_SHOW_BALANCE("treasury.show.balance", "عرض رصيد الخزينة", "treasury", "show.balance"),

    // ============= EXPENSES (المصروفات) =============
    EXPENSES_SHOW("expenses.show", "عرض المصروفات", "expenses", "show"),
    EXPENSES_CREATE("expenses.create", "إضافة مصروف", "expenses", "create"),
    EXPENSES_UPDATE("expenses.update", "تعديل مصروف", "expenses", "update"),
    EXPENSES_DELETE("expenses.delete", "حذف مصروف", "expenses", "delete"),
    EXPENSES_TYPES_MANAGE("expenses.types.manage", "إدارة أنواع المصروفات", "expenses", "types.manage"),

    // ============= EMPLOYEES (الموظفين) =============
    EMPLOYEES_SHOW("employees.show", "عرض الموظفين", "employees", "show"),
    EMPLOYEES_CREATE("employees.create", "إضافة موظف", "employees", "create"),
    EMPLOYEES_UPDATE("employees.update", "تعديل موظف", "employees", "update"),
    EMPLOYEES_DELETE("employees.delete", "حذف موظف", "employees", "delete"),
    EMPLOYEES_SALARY("employees.salary", "صرف مرتب", "employees", "salary"),
    EMPLOYEES_SALARY_UPDATE("employees.salary.update", "تعديل صرف المرتب", "employees", "salary.update"),
    EMPLOYEES_SALARY_DELETE("employees.salary.delete", "حذف صرف المرتب", "employees", "salary.delete"),
    EMPLOYEES_SHOW_SALARY("employees.show.salary", "عرض المرتب", "employees", "show.salary"),

    // ============= REPORTS (التقارير) =============
    REPORTS_SALES("reports.sales", "تقارير المبيعات", "reports", "sales"),
    REPORTS_SALES_DAILY("reports.sales.daily", "تقرير المبيعات اليومية", "reports", "sales.daily"),
    REPORTS_SALES_MONTHLY("reports.sales.monthly", "تقرير المبيعات الشهرية", "reports", "sales.monthly"),
    REPORTS_SALES_COMPREHENSIVE("reports.sales.comprehensive", "تقرير المبيعات الشامل", "reports", "sales.comprehensive"),

    REPORTS_PURCHASES("reports.purchases", "تقارير المشتريات", "reports", "purchases"),
    REPORTS_PURCHASES_DAILY("reports.purchases.daily", "تقرير المشتريات اليومية", "reports", "purchases.daily"),
    REPORTS_PURCHASES_MONTHLY("reports.purchases.monthly", "تقرير المشتريات الشهرية", "reports", "purchases.monthly"),

    REPORTS_INVENTORY("reports.inventory", "تقارير المخزون", "reports", "inventory"),
    REPORTS_INVENTORY_VALUATION("reports.inventory.valuation", "تقييم المخزون", "reports", "inventory.valuation"),
    REPORTS_INVENTORY_MOVEMENT("reports.inventory.movement", "حركة المخزون", "reports", "inventory.movement"),
    REPORTS_INVENTORY_MIN_QUANTITY("reports.inventory.min.quantity", "تقرير الحد الأدنى للكمية", "reports", "inventory.min.quantity"),

    REPORTS_CUSTOMERS("reports.customers", "تقارير العملاء", "reports", "customers"),
    REPORTS_CUSTOMERS_STATEMENT("reports.customers.statement", "كشف حساب عميل", "reports", "customers.statement"),
    REPORTS_CUSTOMERS_RECEIVABLES("reports.customers.receivables", "مديونيات العملاء", "reports", "customers.receivables"),
    REPORTS_CUSTOMERS_PURCHASES("reports.customers.purchases", "مشتريات العميل", "reports", "customers.purchases"),

    REPORTS_SUPPLIERS("reports.suppliers", "تقارير الموردين", "reports", "suppliers"),
    REPORTS_SUPPLIERS_STATEMENT("reports.suppliers.statement", "كشف حساب مورد", "reports", "suppliers.statement"),
    REPORTS_SUPPLIERS_PAYABLES("reports.suppliers.payables", "مستحقات الموردين", "reports", "suppliers.payables"),

    REPORTS_TREASURY("reports.treasury", "تقارير الخزينة", "reports", "treasury"),
    REPORTS_TREASURY_MOVEMENT("reports.treasury.movement", "حركة الخزينة", "reports", "treasury.movement"),
    REPORTS_TREASURY_BALANCE("reports.treasury.balance", "أرصدة الخزائن", "reports", "treasury.balance"),

    REPORTS_PROFIT("reports.profit", "تقارير الأرباح", "reports", "profit"),
    REPORTS_PROFIT_DAILY("reports.profit.daily", "تقرير الأرباح اليومية", "reports", "profit.daily"),
    REPORTS_PROFIT_MONTHLY("reports.profit.monthly", "تقرير الأرباح الشهرية", "reports", "profit.monthly"),
    REPORTS_PROFIT_BY_ITEM("reports.profit.by.item", "الأرباح حسب الصنف", "reports", "profit.by.item"),

    REPORTS_EXPENSES("reports.expenses", "تقارير المصروفات", "reports", "expenses"),
    REPORTS_ITEM_CARD("reports.item.card", "كارت الصنف", "reports", "item.card"),
    REPORTS_BEST_SELLING("reports.best.selling", "الأصناف الأكثر مبيعاً", "reports", "best.selling"),
    REPORTS_ACCOUNT_STATEMENT("reports.account.statement", "كشف حساب", "reports", "account.statement"),
    REPORTS_DASHBOARD("reports.dashboard", "لوحة التحكم", "reports", "dashboard"),

    // ============= USERS & PERMISSIONS (المستخدمين والصلاحيات) =============
    USERS_SHOW("users.show", "عرض المستخدمين", "users", "show"),
    USERS_CREATE("users.create", "إضافة مستخدم", "users", "create"),
    USERS_UPDATE("users.update", "تعديل مستخدم", "users", "update"),
    USERS_DELETE("users.delete", "حذف مستخدم", "users", "delete"),
    USERS_ACTIVATE("users.activate", "تفعيل/إلغاء تفعيل مستخدم", "users", "activate"),
    USERS_PERMISSIONS("users.permissions", "إدارة صلاحيات المستخدمين", "users", "permissions"),
    USERS_ROLES("users.roles", "إدارة أدوار المستخدمين", "users", "roles"),
    USERS_CHANGE_PASSWORD("users.change.password", "تغيير كلمة المرور", "users", "change.password"),

    // ============= ROLES (الأدوار) =============
    ROLES_SHOW("roles.show", "عرض الأدوار", "roles", "show"),
    ROLES_CREATE("roles.create", "إضافة دور", "roles", "create"),
    ROLES_UPDATE("roles.update", "تعديل دور", "roles", "update"),
    ROLES_DELETE("roles.delete", "حذف دور", "roles", "delete"),
    ROLES_PERMISSIONS("roles.permissions", "إدارة صلاحيات الدور", "roles", "permissions"),
    ROLES_ASSIGN_USERS("roles.assign.users", "تعيين المستخدمين للدور", "roles", "assign.users"),

    // ============= SHIFTS (الورديات) =============
    SHIFTS_OPEN("shifts.open", "فتح وردية", "shifts", "open"),
    SHIFTS_CLOSE("shifts.close", "إغلاق وردية", "shifts", "close"),
    SHIFTS_SHOW("shifts.show", "عرض الورديات", "shifts", "show"),
    SHIFTS_REPORTS("shifts.reports", "تقارير الورديات", "shifts", "reports"),
    SHIFTS_ADMIN("shifts.admin", "إدارة ورديات المستخدمين", "shifts", "admin"),

    // ============= SETTINGS (الإعدادات) =============
    SETTINGS_COMPANY("settings.company", "إعدادات الشركة", "settings", "company"),
    SETTINGS_SYSTEM("settings.system", "إعدادات النظام", "settings", "system"),
    SETTINGS_BACKUP("settings.backup", "النسخ الاحتياطي", "settings", "backup"),
    SETTINGS_RESTORE("settings.restore", "استعادة النظام", "settings", "restore"),
    SETTINGS_PRICE_TYPES("settings.price.types", "إدارة أنواع الأسعار", "settings", "price.types"),
    SETTINGS_AREAS("settings.areas", "إدارة المناطق", "settings", "areas"),
    SETTINGS_JOBS("settings.jobs", "إدارة الوظائف", "settings", "jobs"),

    // ============= TARGETS (الأهداف) =============
    TARGETS_SHOW("targets.show", "عرض الأهداف", "targets", "show"),
    TARGETS_CREATE("targets.create", "إضافة هدف", "targets", "create"),
    TARGETS_UPDATE("targets.update", "تعديل هدف", "targets", "update"),
    TARGETS_DELETE("targets.delete", "حذف هدف", "targets", "delete"),
    TARGETS_REPORTS("targets.reports", "تقارير الأهداف", "targets", "reports"),

    // ============= POS (نقاط البيع) =============
    POS_ACCESS("pos.access", "الوصول لنقاط البيع", "pos", "access"),
    POS_SALE("pos.sale", "البيع من نقاط البيع", "pos", "sale"),
    POS_RETURN("pos.return", "مردودات نقاط البيع", "pos", "return"),
    POS_DISCOUNT("pos.discount", "خصم في نقاط البيع", "pos", "discount"),

    // ============= AUDIT (المراجعة) =============
    AUDIT_VIEW("audit.view", "عرض سجل المراجعة", "audit", "view"),
    AUDIT_EXPORT("audit.export", "تصدير سجل المراجعة", "audit", "export");

    private final String code;
    private final String titleAr;
    private final String module;
    private final String action;

    PermissionCode(String code, String titleAr, String module, String action) {
        this.code = code;
        this.titleAr = titleAr;
        this.module = module;
        this.action = action;
    }

    public static PermissionCode fromCode(String code) {
        for (PermissionCode permission : values()) {
            if (permission.code.equals(code)) {
                return permission;
            }
        }
        return null;
    }

    /**
     * الحصول على جميع الصلاحيات الخاصة بـ Module معين
     */
    public static List<PermissionCode> getByModule(String module) {
        return Arrays.stream(values())
                .filter(p -> p.module.equals(module))
                .collect(Collectors.toList());
    }

    /**
     * الحصول على جميع الـ Modules الموجودة
     */
    public static Set<String> getAllModules() {
        return Arrays.stream(values())
                .map(PermissionCode::getModule)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * الحصول على عدد الصلاحيات لكل Module
     */
    public static Map<String, Long> getPermissionCountByModule() {
        return Arrays.stream(values())
                .collect(Collectors.groupingBy(
                        PermissionCode::getModule,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    /**
     * البحث عن الصلاحيات بناءً على نص البحث
     */
    public static List<PermissionCode> search(String searchText) {
        String searchLower = searchText.toLowerCase();
        return Arrays.stream(values())
                .filter(p -> p.code.toLowerCase().contains(searchLower) ||
                        p.titleAr.contains(searchText) ||
                        p.module.toLowerCase().contains(searchLower))
                .collect(Collectors.toList());
    }
}