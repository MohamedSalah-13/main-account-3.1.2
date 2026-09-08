package com.hamza.account.features.treasury.statement;

import java.math.BigDecimal;
import java.util.Objects;

public record TreasuryStatementSummary(BigDecimal openingBalance, BigDecimal totalIncome,
                                       BigDecimal totalOutput, BigDecimal closingBalance) {
    public TreasuryStatementSummary {
        openingBalance = Objects.requireNonNullElse(openingBalance, BigDecimal.ZERO);
        totalIncome = Objects.requireNonNullElse(totalIncome, BigDecimal.ZERO);
        totalOutput = Objects.requireNonNullElse(totalOutput, BigDecimal.ZERO);
        closingBalance = Objects.requireNonNullElse(closingBalance, BigDecimal.ZERO);
    }

    public BigDecimal netMovement() { return totalIncome.subtract(totalOutput); }
}
