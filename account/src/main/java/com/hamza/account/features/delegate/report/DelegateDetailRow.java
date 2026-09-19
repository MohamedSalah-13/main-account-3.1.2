package com.hamza.account.features.delegate.report;

import java.math.BigDecimal;

/**
 * One customer, area, item or group in a delegate's period.
 *
 * @param measure how many documents, or how much quantity in base units - see
 *                {@link DelegateBreakdown#readOffLines()}
 */
public record DelegateDetailRow(int keyId, String name, BigDecimal measure, BigDecimal sales, BigDecimal returns) {

    public DelegateDetailRow {
        name = name == null ? "" : name;
        measure = measure == null ? BigDecimal.ZERO : measure;
        sales = sales == null ? BigDecimal.ZERO : sales;
        returns = returns == null ? BigDecimal.ZERO : returns;
    }

    public BigDecimal net() {
        return sales.subtract(returns);
    }
}
