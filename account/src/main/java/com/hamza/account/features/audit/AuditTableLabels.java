package com.hamza.account.features.audit;

/** Translation keys for known audited tables; unknown historical names remain readable verbatim. */
public final class AuditTableLabels {

    private AuditTableLabels() {
    }

    public static String keyFor(String tableName) {
        if (tableName == null) return null;
        return switch (tableName.toUpperCase()) {
            case "COMPANY" -> "audit.log.table.company";
            case "CUSTOM" -> "audit.log.table.customers";
            case "CUSTOMERS_ACCOUNTS", "CUSTOMER_ACC" -> "audit.log.table.customer.accounts";
            case "EMPLOYEES" -> "audit.log.table.employees";
            case "EXPENSES_DETAILS", "EXPENSES" -> "audit.log.table.expenses";
            case "MAIN_GROUP", "GROUP_MAIN" -> "audit.log.table.main.groups";
            case "SUB_GROUP", "GROUP_SUB" -> "audit.log.table.sub.groups";
            case "ITEMS" -> "audit.log.table.items";
            case "ITEMS_UNITS" -> "audit.log.table.item.units";
            case "STOCKS" -> "audit.log.table.stocks";
            case "STOCK_TRANSFER" -> "audit.log.table.stock.transfers";
            case "SUPPLIERS" -> "audit.log.table.suppliers";
            case "SUPPLIERS_ACCOUNTS", "SUPPLIERS_ACCOUNT" -> "audit.log.table.supplier.accounts";
            case "TOTAL_BUY" -> "audit.log.table.purchases";
            case "TOTAL_BUY_RE", "TOTAL_BUY_RETURN" -> "audit.log.table.purchase.returns";
            case "TOTAL_SALES" -> "audit.log.table.sales";
            case "TOTAL_SALES_RE", "TOTAL_SALES_RETURN" -> "audit.log.table.sales.returns";
            case "TREASURY" -> "audit.log.table.treasuries";
            case "TREASURY_TRANSFERS" -> "audit.log.table.treasury.transfers";
            case "UNITS" -> "audit.log.table.units";
            case "USERS" -> "audit.log.table.users";
            case "AUTH_ROLE" -> "audit.log.table.auth.roles";
            case "AUTH_ROLE_PERMISSION" -> "audit.log.table.auth.role.permissions";
            case "AUTH_USER_ROLE" -> "audit.log.table.auth.user.roles";
            case "AUTH_ROLE_INHERITANCE" -> "audit.log.table.auth.role.inheritance";
            case "AUTH_USER_PERMISSION_OVERRIDE" -> "audit.log.table.auth.user.overrides";
            default -> null;
        };
    }
}
