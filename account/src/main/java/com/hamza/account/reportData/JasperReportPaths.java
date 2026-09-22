package com.hamza.account.reportData;

import static com.hamza.account.config.Configs.FILE_REPORTS;

public final class JasperReportPaths {
    private static final String BASE_PATH = FILE_REPORTS.getAbsolutePath() + "/ar/";

    private JasperReportPaths() {
    } // Prevent instantiation

    public static class Invoice {
        public static final String THERMAL = BASE_PATH + "invoice-80mm.jrxml";
        public static final String MULTI_80mm = BASE_PATH + "invoice-multi-80mm.jrxml";

    }

    public static class Report {
        public static final String HEADER = BASE_PATH + "Header2.jasper";
    }

    public static class Shift {
        /**
         * The X and the Z report on the 80mm thermal printer, one template for both: which rows it
         * prints is {@code ShiftReportLayout}'s decision. Read from the class path, not from the
         * {@code reports/} folder, so a program started outside its own folder still finds it.
         */
        public static final String REPORT_80_RESOURCE = "/reports/ar/shift-report-80mm.jrxml";
    }
}

