package com.hamza.account.features.report;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.features.productprofile.FeatureKey;
import com.hamza.account.features.productprofile.ProductFeatures;

import java.util.List;

/**
 * Every report the program has, wherever it lives - the reports hub's list.
 *
 * <p><b>An entry names the keys and the feature its existing road already asks, and adds none of
 * its own.</b> A report reached from a button on another screen asks what opening that screen asks
 * as well as what its own button asks: the expense reports are behind {@code expenses.show} and
 * {@code expenses.reports}, so the hub lists them only to a reader holding both. A report here is
 * opened through that same entry point, so the hub cannot show somebody a report their sidebar or
 * their screen would not have shown them, nor open one the signed product profile leaves out.
 * {@code docs/reports-plan.md} §3.3 - the hub is a guide, not a host.</p>
 *
 * <p>Titles are the keys the reports already use for their own headings; descriptions are the hub's.
 * Both are whole literals so {@code ReportCatalogTest} can find each in the three bundles - the screen
 * resolves them through a variable, which the message-key scan cannot see.</p>
 */
public enum ReportEntry {

    SUMMARY(ReportSection.SALES, "report.summary.accounts.title", "report.hub.describe.summary",
            ProductFeatures.REPORT_SUMMARY, AppPermissions.REPORTS_SHOW_SUMMARY),
    PROFIT_LOSS(ReportSection.SALES, "report.profit.loss.title", "report.hub.describe.profit.loss",
            ProductFeatures.REPORT_PROFIT_LOSS, AppPermissions.REPORTS_SHOW_PROFIT),
    YEARLY(ReportSection.SALES, "report.yearly.title", "report.hub.describe.yearly",
            ProductFeatures.REPORT_YEARLY, AppPermissions.REPORTS_SHOW_PROFIT),
    SALES_BY_YEAR(ReportSection.SALES, "report.monthly.sales.title", "report.hub.describe.sales.year",
            ProductFeatures.REPORT_SALES_YEAR, AppPermissions.REPORTS_SHOW_SALES),
    PURCHASES_BY_YEAR(ReportSection.SALES, "report.monthly.purchase.title", "report.hub.describe.purchases.year",
            ProductFeatures.REPORT_PURCHASES_YEAR, AppPermissions.REPORTS_SHOW_PURCHASE),
    ITEMS_RANK(ReportSection.SALES, "report.itemsales.hub.month", "report.hub.describe.items.rank",
            ProductFeatures.REPORT_ITEMS, AppPermissions.REPORTS_SHOW_ITEMS),
    ITEMS_DAILY(ReportSection.SALES, "report.itemsales.hub.today", "report.hub.describe.items.daily",
            ProductFeatures.REPORT_ITEMS_DAILY, AppPermissions.REPORTS_SHOW_ITEMS),
    RETURN_REASONS(ReportSection.SALES, "report.returns.reasons.title", "report.hub.describe.return.reasons",
            ProductFeatures.REPORT_RETURN_REASONS, AppPermissions.REPORTS_SHOW_RETURNS),

    CUSTOMER_BALANCES(ReportSection.PARTIES, "party.balances.customers.title", "report.hub.describe.customer.balances",
            ProductFeatures.CUSTOMERS_ACCOUNT, AppPermissions.CUSTOMER_ACCOUNT_SHOW),
    CUSTOMER_AGEING(ReportSection.PARTIES, "party.ageing.identity.customers.title", "report.hub.describe.customer.ageing",
            ProductFeatures.CUSTOMERS_ACCOUNT, AppPermissions.CUSTOMER_ACCOUNT_SHOW),
    CUSTOMER_TREND(ReportSection.PARTIES, "party.trend.customers.title", "report.hub.describe.customer.trend",
            ProductFeatures.CUSTOMERS_ACCOUNT, AppPermissions.CUSTOMER_ACCOUNT_SHOW),
    CUSTOMER_RFM(ReportSection.PARTIES, "party.rfm.title", "report.hub.describe.customer.rfm",
            ProductFeatures.CUSTOMERS_ACCOUNT, AppPermissions.CUSTOMER_ACCOUNT_SHOW),
    CUSTOMER_PAYMENTS(ReportSection.PARTIES, "report.customer.payments.title", "report.hub.describe.customer.payments",
            ProductFeatures.REPORT_CUSTOMER_PAYMENTS, AppPermissions.REPORTS_SHOW_SALES),
    SUPPLIER_BALANCES(ReportSection.PARTIES, "party.balances.suppliers.title", "report.hub.describe.supplier.balances",
            ProductFeatures.SUPPLIERS_ACCOUNT, AppPermissions.SUPPLIERS_ACCOUNT_SHOW),
    SUPPLIER_AGEING(ReportSection.PARTIES, "party.ageing.identity.suppliers.title", "report.hub.describe.supplier.ageing",
            ProductFeatures.SUPPLIERS_ACCOUNT, AppPermissions.SUPPLIERS_ACCOUNT_SHOW),
    SUPPLIER_TREND(ReportSection.PARTIES, "party.trend.suppliers.title", "report.hub.describe.supplier.trend",
            ProductFeatures.SUPPLIERS_ACCOUNT, AppPermissions.SUPPLIERS_ACCOUNT_SHOW),
    SUPPLIER_PAYMENTS(ReportSection.PARTIES, "report.supplier.payments.title", "report.hub.describe.supplier.payments",
            ProductFeatures.REPORT_SUPPLIER_PAYMENTS, AppPermissions.REPORTS_SHOW_PURCHASE),

    ITEM_REPORTS(ReportSection.ITEMS, "itemreport.screen.title", "report.hub.describe.item.reports",
            ProductFeatures.ITEMS_LIST, AppPermissions.ITEMS_SHOW),
    INVENTORY(ReportSection.ITEMS, "nav.inventory.title", "report.hub.describe.inventory",
            ProductFeatures.ITEMS_INVENTORY, AppPermissions.INVENTORY_SHOW),

    CAPITAL(ReportSection.TREASURY, "treasury.capital.title", "report.hub.describe.capital",
            ProductFeatures.TREASURY_CAPITAL, AppPermissions.TREASURY_CAPITAL),
    WALLET_FEES(ReportSection.TREASURY, "treasury.fee.report.title", "report.hub.describe.wallet.fees",
            ProductFeatures.TREASURY_LIST, AppPermissions.TREASURY_UPDATE, AppPermissions.TREASURY_SHOW),

    EXPENSE_REPORTS(ReportSection.EXPENSES, "expense.report.title", "report.hub.describe.expenses",
            ProductFeatures.TREASURY_EXPENSES, AppPermissions.EXPENSES_SHOW, AppPermissions.EXPENSES_REPORTS),

    DELEGATE_PERFORMANCE(ReportSection.EMPLOYEES, "delegate.performance.title", "report.hub.describe.delegates",
            ProductFeatures.EMPLOYEES_LIST, AppPermissions.EMPLOYEE_SHOW, AppPermissions.COMMISSION_REPORTS),
    COMMISSION_RUNS(ReportSection.EMPLOYEES, "commission.run.title", "report.hub.describe.commission.runs",
            ProductFeatures.EMPLOYEES_LIST, AppPermissions.EMPLOYEE_SHOW, AppPermissions.COMMISSION_SHOW),

    SHIFT_REPORTS(ReportSection.SHIFTS, "user.shift.admin.button", "report.hub.describe.shifts",
            ProductFeatures.SYSTEM_SHIFT_REPORTS, AppPermissions.PUBLIC_ACCESS);

    private final ReportSection section;
    private final String titleKey;
    private final String descriptionKey;
    private final FeatureKey feature;
    private final List<PermissionKey> permissions;

    ReportEntry(ReportSection section, String titleKey, String descriptionKey, FeatureKey feature,
                PermissionKey... permissions) {
        this.section = section;
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
        this.feature = feature;
        this.permissions = List.of(permissions);
    }

    public ReportSection section() {
        return section;
    }

    public String titleKey() {
        return titleKey;
    }

    public String descriptionKey() {
        return descriptionKey;
    }

    /** Every key the report's existing road asks - hints for the hub, as for the sidebar. */
    public List<PermissionKey> permissions() {
        return permissions;
    }

    /** The product feature the report's existing entry point is gated by. */
    public FeatureKey feature() {
        return feature;
    }
}
