package com.hamza.account.features.productprofile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Standard editable editions offered by the external setup utility. */
public final class ProductEditionPresets {

    private ProductEditionPresets() {
    }

    public static List<ProductEditionPreset> standard(ProductFeatureCatalog catalog) {
        return List.of(
                preset("full", catalog.keys()), preset("essential", essential()),
                preset("sales", sales()), preset("pos", pointOfSale()),
                preset("inventory", inventory()), preset("custom", Set.of()));
    }

    private static ProductEditionPreset preset(String id, Set<FeatureKey> features) {
        return new ProductEditionPreset(id, "product.profile.preset." + id,
                "product.profile.preset." + id + ".description", features);
    }

    private static Set<FeatureKey> essential() {
        return set(ProductFeatures.SALES_CREATE, ProductFeatures.SALES_RETURN_CREATE,
                ProductFeatures.SALES_LIST, ProductFeatures.SALES_RETURN_LIST,
                ProductFeatures.PURCHASES_CREATE, ProductFeatures.PURCHASES_RETURN_CREATE,
                ProductFeatures.PURCHASES_LIST, ProductFeatures.PURCHASES_RETURN_LIST,
                ProductFeatures.ITEMS_LIST, ProductFeatures.ITEMS_GROUPS, ProductFeatures.ITEMS_ADD,
                ProductFeatures.ITEMS_INVENTORY, ProductFeatures.CUSTOMERS_ADD,
                ProductFeatures.CUSTOMERS_LIST, ProductFeatures.CUSTOMERS_ACCOUNT,
                ProductFeatures.SUPPLIERS_ADD, ProductFeatures.SUPPLIERS_LIST,
                ProductFeatures.SUPPLIERS_ACCOUNT, ProductFeatures.TREASURY_CASH,
                ProductFeatures.TREASURY_DETAILS, ProductFeatures.TREASURY_EXPENSES,
                ProductFeatures.SYSTEM_SETTINGS, ProductFeatures.SYSTEM_BACKUP);
    }

    private static Set<FeatureKey> sales() {
        return set(ProductFeatures.SALES_CREATE, ProductFeatures.SALES_RETURN_CREATE,
                ProductFeatures.SALES_LIST, ProductFeatures.SALES_RETURN_LIST,
                ProductFeatures.ITEMS_LIST, ProductFeatures.ITEMS_ADD, ProductFeatures.ITEMS_PRICE_CHECK,
                ProductFeatures.CUSTOMERS_ADD, ProductFeatures.CUSTOMERS_LIST,
                ProductFeatures.CUSTOMERS_ACCOUNT, ProductFeatures.TREASURY_CASH,
                ProductFeatures.TREASURY_DETAILS, ProductFeatures.REPORT_SALES_YEAR,
                ProductFeatures.REPORT_CUSTOMER_PAYMENTS, ProductFeatures.REPORT_ITEMS,
                ProductFeatures.REPORT_ITEMS_DAILY);
    }

    private static Set<FeatureKey> pointOfSale() {
        return set(ProductFeatures.SALES_CREATE, ProductFeatures.SALES_RETURN_CREATE,
                ProductFeatures.ITEMS_LIST, ProductFeatures.ITEMS_PRICE_CHECK,
                ProductFeatures.CUSTOMERS_ADD, ProductFeatures.CUSTOMERS_LIST,
                ProductFeatures.TREASURY_CASH, ProductFeatures.TREASURY_DETAILS,
                ProductFeatures.SYSTEM_MY_SHIFT);
    }

    private static Set<FeatureKey> inventory() {
        return set(ProductFeatures.PURCHASES_CREATE, ProductFeatures.PURCHASES_RETURN_CREATE,
                ProductFeatures.PURCHASES_LIST, ProductFeatures.PURCHASES_RETURN_LIST,
                ProductFeatures.ITEMS_LIST, ProductFeatures.ITEMS_GROUPS, ProductFeatures.ITEMS_ADD,
                ProductFeatures.ITEMS_MASTER_DATA, ProductFeatures.ITEMS_INVENTORY,
                ProductFeatures.ITEMS_STOCK_COUNT, ProductFeatures.ITEMS_STOCKS,
                ProductFeatures.ITEMS_STOCK_TRANSFERS, ProductFeatures.ITEMS_MERGE,
                ProductFeatures.SUPPLIERS_ADD, ProductFeatures.SUPPLIERS_LIST,
                ProductFeatures.SUPPLIERS_ACCOUNT, ProductFeatures.REPORT_ITEMS,
                ProductFeatures.REPORT_ITEMS_DAILY, ProductFeatures.REPORT_PURCHASES_YEAR,
                ProductFeatures.REPORT_SUPPLIER_PAYMENTS, ProductFeatures.SYSTEM_BACKUP);
    }

    private static Set<FeatureKey> set(FeatureKey... features) {
        return Set.copyOf(new LinkedHashSet<>(List.of(features)));
    }
}
