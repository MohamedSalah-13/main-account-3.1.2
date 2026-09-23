package com.hamza.account.features.profitloss.statement;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A main expense heading's total over a period, its sub-headings included - the line the statement
 * shows under its expenses. It is the expenses report's heading total rolled up to the main heading,
 * over the same {@code expenses_details} rows the statement's expense column sums.
 */
public record ExpenseHeadingTotal(int headingId, String name, BigDecimal amount) {

    public ExpenseHeadingTotal {
        name = Objects.requireNonNullElse(name, "");
        amount = amount == null ? BigDecimal.ZERO : amount;
    }
}
