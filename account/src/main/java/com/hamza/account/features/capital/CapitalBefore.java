package com.hamza.account.features.capital;

import java.math.BigDecimal;

/** What the owner had paid in and drawn before a period began. */
public record CapitalBefore(BigDecimal paidIn, BigDecimal drawn) {

    public BigDecimal net() {
        return paidIn.subtract(drawn);
    }
}
