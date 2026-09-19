package com.hamza.account.features.delegate;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What one delegate did in a period, before any rule is applied to it.
 *
 * @param sales        his invoices, net of discount
 * @param salesReturns the returns dated in the period, net of discount - whatever month the
 *                     invoice they reverse was in
 * @param collected    cash through a till from his customers: the cash part of his invoices and
 *                     the collections attributed to him, less cash refunded on returns
 */
public record DelegateActivity(int employeeId, String name, boolean active,
                               BigDecimal sales, BigDecimal salesReturns, BigDecimal collected) {

    public DelegateActivity {
        Objects.requireNonNull(name, "name");
        sales = orZero(sales);
        salesReturns = orZero(salesReturns);
        collected = orZero(collected);
    }

    /** Sales less the period's returns. May be negative, and a negative base earns nothing. */
    public BigDecimal netSales() {
        return sales.subtract(salesReturns);
    }

    /** The amount a rule on this basis is a percentage of. */
    public BigDecimal baseFor(CommissionBasis basis) {
        return basis == CommissionBasis.COLLECTED ? collected : netSales();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
