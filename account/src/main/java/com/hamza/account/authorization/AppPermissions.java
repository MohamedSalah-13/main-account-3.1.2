package com.hamza.account.authorization;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application permission catalogue. Adding a permission is one constant; metadata is derived and
 * synchronized automatically. No database id, switch statement, or permission screen edit is needed.
 */
public final class AppPermissions {

    public static final PermissionKey PURCHASE_SHOW = key("purchase.show");
    public static final PermissionKey PURCHASE_CREATE = key("purchase.create");
    public static final PermissionKey PURCHASE_UPDATE = key("purchase.update");
    public static final PermissionKey PURCHASE_DELETE = key("purchase.delete");
    public static final PermissionKey TOTAL_PURCHASE_SHOW = key("total.purchase.show");
    public static final PermissionKey TOTAL_PURCHASE_SHOW_INVOICE = key("total.purchase.show.invoice");
    public static final PermissionKey PURCHASE_RE_SHOW = key("purchase.re.show");
    public static final PermissionKey PURCHASE_RE_CREATE = key("purchase.re.create");
    public static final PermissionKey PURCHASE_RE_UPDATE = key("purchase.re.update");
    public static final PermissionKey PURCHASE_RE_DELETE = key("purchase.re.delete");
    public static final PermissionKey TOTAL_PURCHASE_RE_SHOW = key("total.purchase.re.show");
    public static final PermissionKey TOTAL_PURCHASE_RE_SHOW_INVOICE = key("total.purchase.re.show.invoice");
    public static final PermissionKey SALES_SHOW = key("sales.show");
    public static final PermissionKey SALES_CREATE = key("sales.create");
    public static final PermissionKey SALES_UPDATE = key("sales.update");
    public static final PermissionKey SALES_DELETE = key("sales.delete");
    public static final PermissionKey TOTAL_SALES_SHOW = key("total.sales.show");
    public static final PermissionKey TOTAL_SALES_SHOW_INVOICE = key("total.sales.show.invoice");
    public static final PermissionKey SALES_RE_SHOW = key("sales.re.show");
    public static final PermissionKey SALES_RE_CREATE = key("sales.re.create");
    public static final PermissionKey SALES_RE_UPDATE = key("sales.re.update");
    public static final PermissionKey SALES_RE_DELETE = key("sales.re.delete");
    public static final PermissionKey TOTAL_SALES_RE_SHOW = key("total.sales.re.show");
    public static final PermissionKey TOTAL_SALES_RE_SHOW_INVOICE = key("total.sales.re.show.invoice");
    public static final PermissionKey ITEMS_SHOW = key("items.show");
    public static final PermissionKey ITEMS_CREATE = key("items.create");
    public static final PermissionKey ITEMS_UPDATE = key("items.update");
    public static final PermissionKey ITEMS_DELETE = key("items.delete");
    public static final PermissionKey ITEMS_ADD_EXCEL = key("items.add.excel");
    /** Reclassifying items between subgroups without granting every other item edit. */
    public static final PermissionKey ITEMS_GROUP_MOVE = key("items.group.move");
    /** Folding one item into another and deleting it. Held with {@link #ITEMS_DELETE}, never instead of it. */
    public static final PermissionKey ITEMS_MERGE = key("items.merge");
    /**
     * Opening the price-check screen - the one that hangs on the shop wall and answers a
     * customer's barcode with a price.
     * <p>
     * Deliberately not {@link #ITEMS_SHOW}: that key opens the item list, which carries the
     * buying price and the value of the stock. A screen standing unattended in front of
     * customers is exactly where those must not be, so the two are different abilities and
     * a shop can grant one without the other. V36 grants it to every role that could
     * already read an item.
     */
    public static final PermissionKey ITEMS_PRICE_CHECK = key("items.price.check");
    public static final PermissionKey MAIN_GROUP_SHOW = key("main.group.show");
    public static final PermissionKey MAIN_GROUP_CREATE = key("main.group.create");
    public static final PermissionKey MAIN_GROUP_UPDATE = key("main.group.update");
    public static final PermissionKey MAIN_GROUP_DELETE = key("main.group.delete");
    public static final PermissionKey SUB_GROUP_SHOW = key("sub.group.show");
    public static final PermissionKey SUB_GROUP_CREATE = key("sub.group.create");
    public static final PermissionKey SUB_GROUP_UPDATE = key("sub.group.update");
    public static final PermissionKey SUB_GROUP_DELETE = key("sub.group.delete");
    public static final PermissionKey INVENTORY_SHOW = key("inventory.show");
    public static final PermissionKey TREASURY_SHOW = key("treasury.show");
    public static final PermissionKey TREASURY_UPDATE = key("treasury.update");
    public static final PermissionKey TREASURY_DELETE = key("treasury.delete");
    // Separated on purpose: a cashier deposits and withdraws, and only the owner moves
    // money between treasuries or touches an opening balance. One "treasury.update" for
    // all four would have made the split impossible to express in a role.
    public static final PermissionKey TREASURY_TRANSFER = key("treasury.transfer");
    public static final PermissionKey TREASURY_DEPOSIT = key("treasury.deposit");
    public static final PermissionKey TREASURY_BALANCE_SHOW = key("treasury.balance.show");
    // The owner's own money, and the number every balance is measured from. Both are
    // the owner's alone: a cashier who could record "capital paid in" could cover a
    // shortage with it, and one who could edit an opening balance could cover anything.
    public static final PermissionKey TREASURY_CAPITAL = key("treasury.capital");
    public static final PermissionKey TREASURY_OPENING = key("treasury.opening");
    public static final PermissionKey UNITS_SHOW = key("units.show");
    public static final PermissionKey UNITS_CREATE = key("units.create");
    public static final PermissionKey UNITS_UPDATE = key("units.update");
    public static final PermissionKey UNITS_DELETE = key("units.delete");
    public static final PermissionKey SEL_PRICE_SHOW = key("sel.price.show");
    public static final PermissionKey SEL_PRICE_UPDATE = key("sel.price.update");
    public static final PermissionKey SEL_PRICE_DELETE = key("sel.price.delete");
    public static final PermissionKey CUSTOMER_SHOW = key("customer.show");
    public static final PermissionKey CUSTOMER_CREATE = key("customer.create");
    public static final PermissionKey CUSTOMER_UPDATE = key("customer.update");
    public static final PermissionKey CUSTOMER_DELETE = key("customer.delete");
    public static final PermissionKey CUSTOMER_ACCOUNT_SHOW = key("customer.account.show");
    public static final PermissionKey CUSTOMER_ACCOUNT_CREATE = key("customer.account.create");
    public static final PermissionKey CUSTOMER_ACCOUNT_UPDATE = key("customer.account.update");
    public static final PermissionKey CUSTOMER_ACCOUNT_DELETE = key("customer.account.delete");
    /**
     * Recording a debit or credit note on a party's account - a movement with no cash behind
     * it. Separate from {@code account.create} on purpose, and granted by {@code V55} to
     * whoever already held it: collecting money is matched by cash in the drawer, while
     * adjusting a balance by decision is not, and the two are not the same trust. It is also
     * the only way to correct an opening balance once a party has moved, which
     * {@code OpeningBalanceGuard} forbids and {@code opening.correction.customers} promises.
     */
    public static final PermissionKey CUSTOMER_ACCOUNT_ADJUST = key("customer.account.adjust");
    public static final PermissionKey SUPPLIERS_SHOW = key("suppliers.show");
    public static final PermissionKey SUPPLIERS_CREATE = key("suppliers.create");
    public static final PermissionKey SUPPLIERS_UPDATE = key("suppliers.update");
    public static final PermissionKey SUPPLIERS_DELETE = key("suppliers.delete");
    public static final PermissionKey SUPPLIERS_ACCOUNT_SHOW = key("suppliers.account.show");
    public static final PermissionKey SUPPLIERS_ACCOUNT_CREATE = key("suppliers.account.create");
    public static final PermissionKey SUPPLIERS_ACCOUNT_UPDATE = key("suppliers.account.update");
    public static final PermissionKey SUPPLIERS_ACCOUNT_DELETE = key("suppliers.account.delete");
    /** The supplier side of {@link #CUSTOMER_ACCOUNT_ADJUST}. */
    public static final PermissionKey SUPPLIERS_ACCOUNT_ADJUST = key("suppliers.account.adjust");
    public static final PermissionKey EXPENSES_SHOW = key("expenses.show");
    public static final PermissionKey EXPENSES_CREATE = key("expenses.create");
    public static final PermissionKey EXPENSES_UPDATE = key("expenses.update");
    public static final PermissionKey EXPENSES_DELETE = key("expenses.delete");
    public static final PermissionKey EMPLOYEE_SHOW = key("employee.show");
    public static final PermissionKey EMPLOYEE_CREATE = key("employee.create");
    public static final PermissionKey EMPLOYEE_UPDATE = key("employee.update");
    public static final PermissionKey EMPLOYEE_DELETE = key("employee.delete");
    public static final PermissionKey SETTING_SHOW = key("setting.show");
    public static final PermissionKey SETTING_COMPANY_SHOW = key("setting.company.show");
    public static final PermissionKey COMPANY_UPDATE = key("company.update");
    /**
     * Opening the backup screen and taking a copy.
     * <p>
     * It was declared here and used by nothing: the sidebar button and the settings tab
     * both hung off {@link #SETTING_SHOW}, so anybody who could open the settings could
     * dump the whole database to a file and walk away with it. V39 grants it to every role
     * that already held {@code setting.show}, so nobody loses the ability they had.
     */
    public static final PermissionKey SETTING_BACKUP_SHOW = key("setting.backup.show");
    /**
     * Replacing the live database with a backup file.
     * <p>
     * Deliberately not the same key as taking one. A restore runs {@code DROP TABLE} over
     * the shop's data and is the single most destructive thing the program can do; a
     * backup is a read. V39 grants this one only to the roles that could already reach the
     * screen, and it can be taken away without taking backups away with it.
     */
    public static final PermissionKey BACKUP_RESTORE = key("backup.restore");
    public static final PermissionKey SETTING_OTHER_SHOW = key("setting.other.show");
    public static final PermissionKey SETTING_ITEMS_SHOW = key("setting.items.show");
    public static final PermissionKey SETTING_SHOWS_SHOW = key("setting.shows.show");
    /**
     * Who may decide whether the home screen shows the day's totals - which is to say,
     * who may let the staff standing at that screen read the revenue.
     * <p>
     * The settings tab used to ask {@code CurrentUser.get().getId() != 1} instead: the
     * numbered-administrator test this permission system replaced, and one that breaks on
     * any install where the owner is not user number one. A newly declared key is granted
     * to SYSTEM_ADMIN by the startup synchronisation, so the person who could set it
     * before still can.
     */
    public static final PermissionKey MAIN_TOTALS_MANAGE = key("main.totals.manage");
    public static final PermissionKey INVOICE_PROFIT_SHOW = key("invoice.profit.show");
    public static final PermissionKey EMPLOYEES_SHOW_SALARY = key("employees.show.salary");
    public static final PermissionKey SHOW_COLUMN_BUY_PRICE = key("show.column.buy.price");
    public static final PermissionKey UPDATE_DATA_BEFORE_MONTH = key("update.data.before.month");
    public static final PermissionKey SHOW_DATA_BEFORE_MONTH = key("show.data.before.month");
    public static final PermissionKey SETTING_UPDATE_NAME = key("setting.update.name");
    public static final PermissionKey SETTING_UPDATE_PASS = key("setting.update.pass");
    public static final PermissionKey REPORTS_SHOW_SUMMARY = key("reports.show.summary");
    public static final PermissionKey REPORTS_SHOW_ITEMS = key("reports.show.items");
    public static final PermissionKey REPORTS_SHOW_CUSTOMERS = key("reports.show.customers");
    public static final PermissionKey REPORTS_SHOW_SUPPLIERS = key("reports.show.suppliers");
    public static final PermissionKey REPORTS_SHOW_CUSTOMERS_ACCOUNT_AREA = key("reports.show.customers.account.area");
    public static final PermissionKey REPORTS_SHOW_SALES = key("reports.show.sales");
    public static final PermissionKey REPORTS_SHOW_PURCHASE = key("reports.show.purchase");
    public static final PermissionKey REPORTS_SHOW_DAY_DETAILS = key("reports.show.day.details");
    public static final PermissionKey REPORTS_SHOW_DELEGATE = key("reports.show.delegate");
    public static final PermissionKey REPORTS_SHOW_PROFIT = key("reports.show.profit");
    public static final PermissionKey REPORTS_SHOW_RETURNS = key("reports.show.returns");
    public static final PermissionKey STOCK_COUNT_SHOW = key("stock.count.show");
    public static final PermissionKey STOCK_COUNT_POST = key("stock.count.post");
    public static final PermissionKey STOCK_TRANSFER_POST = key("stock.transfer.post");
    public static final PermissionKey STOCK_TRANSFER_DELETE = key("stock.transfer.delete");
    public static final PermissionKey STOCK_SHOW = key("stock.show");
    public static final PermissionKey STOCK_CREATE = key("stock.create");
    public static final PermissionKey STOCK_UPDATE = key("stock.update");
    public static final PermissionKey STOCK_DELETE = key("stock.delete");
    public static final PermissionKey ACCOUNTING_LOCK_MANAGE = key("accounting.lock.manage");
    public static final PermissionKey ACCOUNTING_LOCK_BYPASS = key("accounting.lock.bypass");
    public static final PermissionKey USERS_SHOW = key("users.show");
    public static final PermissionKey USERS_MANAGE = key("users.manage");
    public static final PermissionKey ROLES_MANAGE = key("roles.manage");
    /**
     * Reading the areas list. An area belongs to a customer or a supplier, not to an item,
     * and until this key existed the section borrowed {@link #ITEMS_SHOW} - the permission
     * of the button that used to open it. V35 grants it to every role that held either.
     */
    public static final PermissionKey AREA_SHOW = key("area.show");
    public static final PermissionKey AREA_CREATE = key("area.create");
    public static final PermissionKey AREA_UPDATE = key("area.update");
    public static final PermissionKey AREA_DELETE = key("area.delete");
    /** Reading before/after database values; split from the broad settings permission in V46. */
    public static final PermissionKey AUDIT_VIEW = key("audit.view");
    /** Writing sensitive before/after values outside the application. */
    public static final PermissionKey AUDIT_EXPORT = key("audit.export");
    public static final PermissionKey AUDIT_DELETE = key("audit.delete");
    /** Enabling or executing permanent age-based deletion of audit rows. */
    public static final PermissionKey AUDIT_RETENTION_MANAGE = key("audit.retention.manage");
    /** Reading immutable evidence about export, deletion and retention administration. */
    public static final PermissionKey AUDIT_ADMIN_VIEW = key("audit.admin.view");
    /** Exporting the immutable administration journal outside the application. */
    public static final PermissionKey AUDIT_ADMIN_EXPORT = key("audit.admin.export");
    public static final PermissionKey USER_SHIFT_MANAGE = key("user.shift.manage");
    public static final PermissionKey SHIFT_SELF_VIEW = key("shift.self.view");
    public static final PermissionKey SHIFT_SELF_OPEN = key("shift.self.open");
    public static final PermissionKey SHIFT_SELF_CLOSE = key("shift.self.close");
    public static final PermissionKey SHIFT_X_REPORT_VIEW = key("shift.xreport.view");
    public static final PermissionKey SHIFT_FORCE_CLOSE = key("shift.force.close");
    public static final PermissionKey SHIFT_POLICY_MANAGE = key("shift.policy.manage");
    public static final PermissionKey SHIFT_REPORT_REPRINT = key("shift.report.reprint");
    public static final PermissionKey SHIFT_LEDGER_VIEW = key("shift.ledger.view");
    public static final PermissionKey PUBLIC_ACCESS = PermissionKey.publicAccess();
    public static final PermissionKey DISABLE_BUTTON = PermissionKey.deny();

    private static final List<PermissionDefinition> DEFINITIONS = discover();
    private static final Map<String, PermissionKey> BY_VALUE = DEFINITIONS.stream()
            .collect(Collectors.toUnmodifiableMap(definition -> definition.key().value(), PermissionDefinition::key));

    private AppPermissions() {
    }

    public static List<PermissionDefinition> definitions() {
        return DEFINITIONS;
    }

    public static PermissionKey fromValue(String value) {
        return BY_VALUE.get(value);
    }

    private static PermissionKey key(String value) {
        return PermissionKey.of(value);
    }

    private static List<PermissionDefinition> discover() {
        return Arrays.stream(AppPermissions.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers())
                        && Modifier.isStatic(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())
                        && field.getType() == PermissionKey.class)
                .map(field -> {
                    try {
                        return (PermissionKey) field.get(null);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .filter(key -> !key.isMarker())
                .sorted(Comparator.comparing(PermissionKey::value))
                .map(key -> definition(key, 0))
                .toList();
    }

    private static PermissionDefinition definition(PermissionKey key, int sortOrder) {
        String[] parts = key.value().split("\\.");
        String module = parts[0].toUpperCase(Locale.ROOT);
        String action = parts[parts.length - 1].toUpperCase(Locale.ROOT);
        String resource = String.join(".", Arrays.copyOf(parts, parts.length - 1));
        PermissionRisk permissionRisk = "audit.view".equals(key.value()) || "audit.export".equals(key.value())
                || "audit.admin.view".equals(key.value()) || "audit.admin.export".equals(key.value())
                ? PermissionRisk.HIGH : risk(action);
        return new PermissionDefinition(key, module, resource, action, permissionRisk, sortOrder);
    }

    private static PermissionRisk risk(String action) {
        return switch (action) {
            // RESTORE replaces every table in the database from a file. There is no
            // action here that undoes more, so it cannot be the LOW the default gives it.
            case "DELETE", "BYPASS", "MANAGE", "POST", "RESTORE" -> PermissionRisk.CRITICAL;
            case "UPDATE", "ADD", "CREATE", "MOVE" -> PermissionRisk.HIGH;
            case "INVOICE", "PRICE", "SALARY", "PROFIT" -> PermissionRisk.MEDIUM;
            default -> PermissionRisk.LOW;
        };
    }
}
