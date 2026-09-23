package com.hamza.account.features.report.summary;

import java.math.BigDecimal;

/**
 * What came into the treasuries over a period and what left them, as the treasury statement counts a
 * movement - read from {@code treasury_balance} itself, less the two things that are not a movement of the
 * business's cash: an opening balance, and a transfer between two of its own treasuries.
 */
public record CashFlow(BigDecimal in, BigDecimal out) {

    public static final CashFlow NONE = new CashFlow(BigDecimal.ZERO, BigDecimal.ZERO);

    public CashFlow {
        in = in == null ? BigDecimal.ZERO : in;
        out = out == null ? BigDecimal.ZERO : out;
    }

    public BigDecimal net() {
        return in.subtract(out);
    }

    public boolean isEmpty() {
        return in.signum() == 0 && out.signum() == 0;
    }
}
