package com.hamza.account.reportData;

import static com.hamza.account.config.Configs.FILE_REPORTS;

public final class JasperReportPaths {
    private static final String BASE_PATH = FILE_REPORTS.getAbsolutePath() + "/ar/";

    private JasperReportPaths() {
    } // Prevent instantiation

    public static class Invoice {
        public static final String THERMAL = BASE_PATH + "invoice-80mm.jrxml";
        public static final String THERMAL_NUMBER_GENERATE = BASE_PATH + "number-generate-80mm.jrxml";
        public static final String THERMAL_Kitchen = BASE_PATH + "invoice-name-80mm.jrxml";
        public static final String THERMAL_KITCHEN_TEST = BASE_PATH + "invoice-kitchen-80mm.jrxml";
        public static final String STANDARD = BASE_PATH + "invoice-A4.jrxml";
        public static final String DETAILS = BASE_PATH + "invoice-details-A4.jrxml";
        public static final String MULTI = BASE_PATH + "invoice-multi-A4.jrxml";
        public static final String MULTI_80mm = BASE_PATH + "invoice-multi-80mm.jrxml";

    }

    public static class Account {
        public static final String BY_AREA = BASE_PATH + "account-area-A4.jrxml";
        public static final String ACCOUNT_DETAILS_REPORT_TEMPLATE = BASE_PATH + "account-details-80mm.jrxml";
        public static final String ACCOUNT_DETAILS_REPORT_PATH = BASE_PATH + "account-details-A4.jrxml";
        public static final String ACCOUNT_STATEMENT = BASE_PATH + "account-statement-A4.jrxml";
        public static final String TOTALS = BASE_PATH + "accounts-totals-A4.jrxml";
        public static final String TOTALS_80 = BASE_PATH + "accounts-totals-80mm.jrxml";
    }

    public static class Report {
        public static final String HEADER = BASE_PATH + "Header2.jasper";
        public static final String ITEMS = BASE_PATH + "items-A4.jrxml";
        public static final String ITEMS_BY_DELEGATE = BASE_PATH + "report-by-delegate-items-A4.jrxml";
        public static final String MONTHLY = BASE_PATH + "report-by-month-A4.jrxml";
        public static final String DELEGATE = BASE_PATH + "report-by-delegate-A4.jrxml";
        public static final String NAMES_DATA = BASE_PATH + "name-customer-by-area-A4.jrxml";
        public static final String CARD_ITEMS = BASE_PATH + "items-card-A4.jrxml";
        public static final String EXPENSE_RECEIPT = BASE_PATH + "receipt-payment-A4.jrxml";
        public static final String INVENTORY_BY_TABLE = BASE_PATH + "items-inventory-A4.jrxml";
        public static final String CONVERT_STOCK = BASE_PATH + "convert-stock-A4.jrxml";
        public static final String TREASURY_STATEMENT_A4_TEMPLATE = BASE_PATH + "treasury-statement-A4.jrxml";
        public static final String ROSARY_SUMMARY = BASE_PATH + "rosary-summary-80mm.jrxml";
        public static final String REPORT_ITEMS_QUANTITY_80MM_TEMPLATE = BASE_PATH + "report-items-quantity-80mm.jrxml";
    }

    public static class Barcode {
        public static final String VERSION_1 = BASE_PATH + "barcode-one-label.jrxml";
        public static final String VERSION_2 = BASE_PATH + "barcode-two-label.jrxml";
        public static final String ITEMS = BASE_PATH + "items-barcode-A4.jrxml";
    }
}

