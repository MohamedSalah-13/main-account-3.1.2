package com.hamza.account.features.report.summary;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the customers owe today, as the customer balances screen opens - its own "debtors today" filter,
 * read through its own service, so the card and the screen it opens are one figure.
 *
 * @param debtors how many customers owe something
 * @param owed    what they owe together, in the base
 * @param top     the few who owe most, most first
 */
public record Receivables(int debtors, BigDecimal owed, List<Debtor> top) {

    public static final Receivables NONE = new Receivables(0, BigDecimal.ZERO, List.of());

    public Receivables {
        owed = owed == null ? BigDecimal.ZERO : owed;
        top = top == null ? List.of() : List.copyOf(top);
    }

    /** One customer and what they owe, in the base. */
    public record Debtor(String name, BigDecimal balance) {
    }
}
