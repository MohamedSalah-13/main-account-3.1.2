package com.hamza.account.features.profitloss.statement;

/**
 * What a period of the profit and loss statement is set against. Both are ordinary questions - "is this
 * month better than last month" and "is it better than this month last year" - and a shop with a season
 * needs the second, since every month looks worse than the one before it on the way out of one.
 */
public enum ComparisonBasis {

    PREVIOUS_PERIOD("profitloss.compare.previous.period"),
    SAME_PERIOD_LAST_YEAR("profitloss.compare.last.year");

    private final String messageKey;

    ComparisonBasis(String messageKey) {
        this.messageKey = messageKey;
    }

    /** The key the screen translates. Never compared against anything. */
    public String messageKey() {
        return messageKey;
    }
}
