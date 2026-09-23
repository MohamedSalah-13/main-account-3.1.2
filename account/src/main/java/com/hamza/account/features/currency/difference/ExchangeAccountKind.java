package com.hamza.account.features.currency.difference;

import java.math.BigDecimal;

/**
 * The three kinds of account that can hold a currency other than the base: a treasury (V81), a customer
 * and a supplier (V82).
 *
 * <p><b>A difference is signed as a gain or a loss to the shop, never as the balance's direction</b>
 * (docs/currency-plan.md §16 ق-هـ٤). A treasury's balance is what it holds and a customer's is what they
 * owe, so both are assets: a rate that rises is a gain on them. A supplier's balance is what the shop owes,
 * a liability: the same rise is a loss. The walk is done once, in the balance's own direction, and
 * {@link #gain} turns its answer into the shop's. A customer who has paid in advance has a negative balance
 * and a rising rate is then a loss on them too - with no second rule, because the sign of the balance
 * carries it.</p>
 */
public enum ExchangeAccountKind {

    TREASURY(1, "currency.difference.kind.treasury"),
    CUSTOMER(1, "currency.difference.kind.customer"),
    SUPPLIER(-1, "currency.difference.kind.supplier");

    private final int sign;
    private final String messageKey;

    ExchangeAccountKind(int sign, String messageKey) {
        this.sign = sign;
        this.messageKey = messageKey;
    }

    /** {@code amount}, worked out in the balance's own direction, as a gain (positive) or a loss to the shop. */
    public BigDecimal gain(BigDecimal amount) {
        return amount == null ? null : sign > 0 ? amount : amount.negate();
    }

    public String messageKey() {
        return messageKey;
    }
}
