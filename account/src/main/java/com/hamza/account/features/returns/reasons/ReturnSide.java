package com.hamza.account.features.returns.reasons;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.ItemNetLines;

/**
 * Which returns the report is about: what customers brought back, or what went back to suppliers. Each
 * side names its two tables - the returns and the documents they are set against - through
 * {@link ItemNetLines}, so the report spells no table name of its own.
 */
public enum ReturnSide {

    SALES(ItemNetLines.SALES, "report.returns.reasons.sales"),
    PURCHASES(ItemNetLines.PURCHASES, "report.returns.reasons.purchases");

    private final ItemNetLines lines;
    private final String messageKey;

    ReturnSide(ItemNetLines lines, String messageKey) {
        this.lines = lines;
        this.messageKey = messageKey;
    }

    public DocumentTableSpec returns() {
        return lines.returns();
    }

    public DocumentTableSpec documents() {
        return lines.documents();
    }

    public String messageKey() {
        return messageKey;
    }
}
