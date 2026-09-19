package com.hamza.account.features.delegate.report;

/**
 * The ways one delegate's sales are taken apart. Which statement, which family - and so what the
 * third column of a row means - is decided here once, not by the screen.
 */
public enum DelegateBreakdown {

    CUSTOMER("delegate.detail.by.customer", DelegateDetailQuery.BY_CUSTOMER_SQL, false),
    AREA("delegate.detail.by.area", DelegateDetailQuery.BY_AREA_SQL, false),
    ITEM("delegate.detail.by.item", DelegateDetailQuery.BY_ITEM_SQL, true),
    GROUP("delegate.detail.by.group", DelegateDetailQuery.BY_GROUP_SQL, true);

    private final String messageKey;
    private final String sql;
    private final boolean readOffLines;

    DelegateBreakdown(String messageKey, String sql, boolean readOffLines) {
        this.messageKey = messageKey;
        this.sql = sql;
        this.readOffLines = readOffLines;
    }

    /** For display only. */
    public String messageKey() {
        return messageKey;
    }

    public String sql() {
        return sql;
    }

    /**
     * True when the rows are line values: their measure is a quantity rather than a count of
     * documents, and they add up to the net <b>before</b> what was taken off whole invoices.
     */
    public boolean readOffLines() {
        return readOffLines;
    }
}
