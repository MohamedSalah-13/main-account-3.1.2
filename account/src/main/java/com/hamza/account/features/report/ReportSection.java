package com.hamza.account.features.report;

/** The groups the reports hub lists its reports under, in the order it shows them. */
public enum ReportSection {

    SALES("report.hub.section.sales"),
    PARTIES("report.hub.section.parties"),
    ITEMS("report.hub.section.items"),
    TREASURY("report.hub.section.treasury"),
    EXPENSES("report.hub.section.expenses"),
    EMPLOYEES("report.hub.section.employees"),
    SHIFTS("report.hub.section.shifts");

    private final String titleKey;

    ReportSection(String titleKey) {
        this.titleKey = titleKey;
    }

    public String titleKey() {
        return titleKey;
    }
}
