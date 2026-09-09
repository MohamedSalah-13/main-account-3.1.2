package com.hamza.account.features.productprofile;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Complete catalogue of client-configurable screens exposed by the main navigation. */
public final class ProductFeatures {

    public static final String CATEGORY_SALES = "product.profile.category.sales";
    public static final String CATEGORY_PURCHASES = "product.profile.category.purchases";
    public static final String CATEGORY_ITEMS = "product.profile.category.items";
    public static final String CATEGORY_CUSTOMERS = "product.profile.category.customers";
    public static final String CATEGORY_SUPPLIERS = "product.profile.category.suppliers";
    public static final String CATEGORY_TEAM = "product.profile.category.team";
    public static final String CATEGORY_TREASURY = "product.profile.category.treasury";
    public static final String CATEGORY_REPORTS = "product.profile.category.reports";
    public static final String CATEGORY_SYSTEM = "product.profile.category.system";

    public static final FeatureKey SALES_CREATE = key("sales.create");
    public static final FeatureKey SALES_RETURN_CREATE = key("sales.return.create");
    public static final FeatureKey SALES_LIST = key("sales.list");
    public static final FeatureKey SALES_RETURN_LIST = key("sales.return.list");
    public static final FeatureKey PURCHASES_CREATE = key("purchases.create");
    public static final FeatureKey PURCHASES_RETURN_CREATE = key("purchases.return.create");
    public static final FeatureKey PURCHASES_LIST = key("purchases.list");
    public static final FeatureKey PURCHASES_RETURN_LIST = key("purchases.return.list");
    public static final FeatureKey ITEMS_LIST = key("items.list");
    public static final FeatureKey ITEMS_GROUPS = key("items.groups");
    public static final FeatureKey ITEMS_ADD = key("items.add");
    public static final FeatureKey ITEMS_MASTER_DATA = key("items.master-data");
    public static final FeatureKey ITEMS_INVENTORY = key("items.inventory");
    public static final FeatureKey ITEMS_STOCK_COUNT = key("items.stock-count");
    public static final FeatureKey ITEMS_STOCKS = key("items.stocks");
    public static final FeatureKey ITEMS_STOCK_TRANSFERS = key("items.stock-transfers");
    public static final FeatureKey ITEMS_MERGE = key("items.merge");
    public static final FeatureKey ITEMS_PRICE_CHECK = key("items.price-check");
    public static final FeatureKey CUSTOMERS_ADD = key("customers.add");
    public static final FeatureKey CUSTOMERS_LIST = key("customers.list");
    public static final FeatureKey CUSTOMERS_ACCOUNT = key("customers.account");
    public static final FeatureKey SUPPLIERS_ADD = key("suppliers.add");
    public static final FeatureKey SUPPLIERS_LIST = key("suppliers.list");
    public static final FeatureKey SUPPLIERS_ACCOUNT = key("suppliers.account");
    public static final FeatureKey EMPLOYEES_ADD = key("employees.add");
    public static final FeatureKey EMPLOYEES_LIST = key("employees.list");
    public static final FeatureKey USERS_ADD = key("users.add");
    public static final FeatureKey USERS_LIST = key("users.list");
    public static final FeatureKey TREASURY_LIST = key("treasury.list");
    public static final FeatureKey TREASURY_CASH = key("treasury.cash");
    public static final FeatureKey TREASURY_TRANSFER = key("treasury.transfer");
    public static final FeatureKey TREASURY_CAPITAL = key("treasury.capital");
    public static final FeatureKey TREASURY_DETAILS = key("treasury.details");
    public static final FeatureKey TREASURY_AUDIT = key("treasury.audit");
    public static final FeatureKey TREASURY_EXPENSES = key("treasury.expenses");
    public static final FeatureKey REPORT_SUMMARY = key("reports.summary");
    public static final FeatureKey REPORT_ITEMS = key("reports.items");
    public static final FeatureKey REPORT_ITEMS_DAILY = key("reports.items-daily");
    public static final FeatureKey REPORT_SALES_YEAR = key("reports.sales-year");
    public static final FeatureKey REPORT_PURCHASES_YEAR = key("reports.purchases-year");
    public static final FeatureKey REPORT_CUSTOMER_PAYMENTS = key("reports.customer-payments");
    public static final FeatureKey REPORT_SUPPLIER_PAYMENTS = key("reports.supplier-payments");
    public static final FeatureKey REPORT_DETAILS = key("reports.details");
    public static final FeatureKey REPORT_YEARLY = key("reports.yearly");
    public static final FeatureKey REPORT_PROFIT_LOSS = key("reports.profit-loss");
    public static final FeatureKey REPORT_RETURN_REASONS = key("reports.return-reasons");
    public static final FeatureKey SYSTEM_SETTINGS = key("system.settings");
    public static final FeatureKey SYSTEM_MY_SHIFT = key("system.my-shift");
    public static final FeatureKey SYSTEM_SHIFT_REPORTS = key("system.shift-reports");
    public static final FeatureKey SYSTEM_BACKUP = key("system.backup");
    public static final FeatureKey SYSTEM_DELETE_DATA = key("system.delete-data");

    /** The only keys understood by schema version 1 profiles. */
    public static final Set<FeatureKey> VERSION_1_KEYS = Set.of(ITEMS_MERGE, ITEMS_PRICE_CHECK);

    private static final List<ProductFeatureDefinition> DEFINITIONS = List.of(
            def(SALES_CREATE, CATEGORY_SALES), def(SALES_RETURN_CREATE, CATEGORY_SALES),
            def(SALES_LIST, CATEGORY_SALES), def(SALES_RETURN_LIST, CATEGORY_SALES),
            def(PURCHASES_CREATE, CATEGORY_PURCHASES), def(PURCHASES_RETURN_CREATE, CATEGORY_PURCHASES),
            def(PURCHASES_LIST, CATEGORY_PURCHASES), def(PURCHASES_RETURN_LIST, CATEGORY_PURCHASES),
            def(ITEMS_LIST, CATEGORY_ITEMS), def(ITEMS_GROUPS, CATEGORY_ITEMS), def(ITEMS_ADD, CATEGORY_ITEMS),
            def(ITEMS_MASTER_DATA, CATEGORY_ITEMS), def(ITEMS_INVENTORY, CATEGORY_ITEMS),
            def(ITEMS_STOCK_COUNT, CATEGORY_ITEMS), def(ITEMS_STOCKS, CATEGORY_ITEMS),
            def(ITEMS_STOCK_TRANSFERS, CATEGORY_ITEMS), def(ITEMS_MERGE, CATEGORY_ITEMS),
            def(ITEMS_PRICE_CHECK, CATEGORY_ITEMS),
            def(CUSTOMERS_ADD, CATEGORY_CUSTOMERS), def(CUSTOMERS_LIST, CATEGORY_CUSTOMERS),
            def(CUSTOMERS_ACCOUNT, CATEGORY_CUSTOMERS), def(SUPPLIERS_ADD, CATEGORY_SUPPLIERS),
            def(SUPPLIERS_LIST, CATEGORY_SUPPLIERS), def(SUPPLIERS_ACCOUNT, CATEGORY_SUPPLIERS),
            def(EMPLOYEES_ADD, CATEGORY_TEAM), def(EMPLOYEES_LIST, CATEGORY_TEAM),
            def(USERS_ADD, CATEGORY_TEAM), def(USERS_LIST, CATEGORY_TEAM),
            def(TREASURY_LIST, CATEGORY_TREASURY), def(TREASURY_CASH, CATEGORY_TREASURY),
            def(TREASURY_TRANSFER, CATEGORY_TREASURY), def(TREASURY_CAPITAL, CATEGORY_TREASURY),
            def(TREASURY_DETAILS, CATEGORY_TREASURY), def(TREASURY_AUDIT, CATEGORY_TREASURY),
            def(TREASURY_EXPENSES, CATEGORY_TREASURY), def(REPORT_SUMMARY, CATEGORY_REPORTS),
            def(REPORT_ITEMS, CATEGORY_REPORTS), def(REPORT_ITEMS_DAILY, CATEGORY_REPORTS),
            def(REPORT_SALES_YEAR, CATEGORY_REPORTS), def(REPORT_PURCHASES_YEAR, CATEGORY_REPORTS),
            def(REPORT_CUSTOMER_PAYMENTS, CATEGORY_REPORTS), def(REPORT_SUPPLIER_PAYMENTS, CATEGORY_REPORTS),
            def(REPORT_DETAILS, CATEGORY_REPORTS), def(REPORT_YEARLY, CATEGORY_REPORTS),
            def(REPORT_PROFIT_LOSS, CATEGORY_REPORTS), def(REPORT_RETURN_REASONS, CATEGORY_REPORTS),
            def(SYSTEM_SETTINGS, CATEGORY_SYSTEM), def(SYSTEM_MY_SHIFT, CATEGORY_SYSTEM),
            def(SYSTEM_SHIFT_REPORTS, CATEGORY_SYSTEM), def(SYSTEM_BACKUP, CATEGORY_SYSTEM),
            def(SYSTEM_DELETE_DATA, CATEGORY_SYSTEM));

    private ProductFeatures() {
    }

    public static List<ProductFeatureDefinition> definitions() {
        return DEFINITIONS;
    }

    public static Set<FeatureKey> allKeys() {
        return DEFINITIONS.stream().map(ProductFeatureDefinition::key).collect(Collectors.toUnmodifiableSet());
    }

    public static Set<FeatureKey> keysInCategory(String categoryKey) {
        return DEFINITIONS.stream()
                .filter(definition -> definition.categoryKey().equals(categoryKey))
                .map(ProductFeatureDefinition::key)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static FeatureKey key(String value) {
        return FeatureKey.of(value);
    }

    private static ProductFeatureDefinition def(FeatureKey key, String category) {
        return new ProductFeatureDefinition(key,
                "product.profile.feature." + key.value().replace('-', '.'),
                "product.profile.feature.screen.description",
                category,
                Set.of());
    }
}
