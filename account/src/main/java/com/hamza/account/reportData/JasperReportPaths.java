package com.hamza.account.reportData;

import static com.hamza.account.config.Configs.FILE_REPORTS;

public final class JasperReportPaths {
    private static final String BASE_PATH = FILE_REPORTS.getAbsolutePath() + "/ar/";

    private JasperReportPaths() {
    } // Prevent instantiation

    public static class Invoice {
        public static final String THERMAL = BASE_PATH + "invoice-80mm.jrxml";
        public static final String STANDARD = BASE_PATH + "invoice-A4.jrxml";
        public static final String MULTI = BASE_PATH + "invoice-multi-A4.jrxml";
        public static final String MULTI_80mm = BASE_PATH + "invoice-multi-80mm.jrxml";

    }

    public static class Account {
        public static final String ACCOUNT_STATEMENT = BASE_PATH + "account-statement-A4.jrxml";
    }

    public static class Report {
        public static final String HEADER = BASE_PATH + "Header2.jasper";
        public static final String CARD_ITEMS = BASE_PATH + "items-card-A4.jrxml";
        public static final String INVENTORY_BY_TABLE = BASE_PATH + "items-inventory-A4.jrxml";
        public static final String TREASURY_STATEMENT_A4_TEMPLATE = BASE_PATH + "treasury-statement-A4.jrxml";
        public static final String STOCK_TRANSFER_HISTORY = BASE_PATH + "stock-transfer-history-A4.jrxml";
        public static final String ITEMS_ACROSS_STOCKS = BASE_PATH + "items-across-stocks-A4.jrxml";
    }

    public static class Shift {
        // تقرير غلق الوردية — طباعة حرارية 80mm
        public static final String Z_REPORT_80 = BASE_PATH + "shift-z-report-80mm.jrxml";
        // تقرير لحظي — طباعة حرارية 80mm
        public static final String X_REPORT_80 = BASE_PATH + "shift-x-report-80mm.jrxml";
    }
}

