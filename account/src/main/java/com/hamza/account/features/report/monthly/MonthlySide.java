package com.hamza.account.features.report.monthly;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.features.productprofile.FeatureKey;
import com.hamza.account.features.productprofile.ProductFeatures;

/**
 * Which half of the business the monthly totals are of. Each side is a report of its own in the edition
 * and in the roles - the sales by year and the purchases by year were two sidebar entries, each with its
 * feature and its key - and both stay: a key new to the catalogue would be missing from every profile
 * already signed.
 *
 * <p>The two tables each side reads are named here and nowhere else, so the only identifiers that ever
 * enter the statement are ones this enum owns.</p>
 */
public enum MonthlySide {

    SALES("total_sales", "total_sales_re", "report.monthly.side.sales",
            AppPermissions.REPORTS_SHOW_SALES, ProductFeatures.REPORT_SALES_YEAR),
    PURCHASES("total_buy", "total_buy_re", "report.monthly.side.purchases",
            AppPermissions.REPORTS_SHOW_PURCHASE, ProductFeatures.REPORT_PURCHASES_YEAR);

    private final String documents;
    private final String returns;
    private final String labelKey;
    private final PermissionKey permission;
    private final FeatureKey feature;

    MonthlySide(String documents, String returns, String labelKey, PermissionKey permission, FeatureKey feature) {
        this.documents = documents;
        this.returns = returns;
        this.labelKey = labelKey;
        this.permission = permission;
        this.feature = feature;
    }

    /** The invoices' header table. */
    public String documents() {
        return documents;
    }

    /** The returns' header table. */
    public String returns() {
        return returns;
    }

    /** What the side is called in the bar and on the paper. */
    public String labelKey() {
        return labelKey;
    }

    /** What reading this side asks - the key its sidebar entry asked before the two were one screen. */
    public PermissionKey permission() {
        return permission;
    }

    /** The edition's feature for this side. */
    public FeatureKey feature() {
        return feature;
    }
}
