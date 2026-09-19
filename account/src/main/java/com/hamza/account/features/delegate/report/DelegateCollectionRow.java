package com.hamza.account.features.delegate.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One collection attributed to a delegate. {@code invoiceNumber == 0} is a payment on account. */
public record DelegateCollectionRow(long movementId, LocalDate date, String customer, long invoiceNumber,
                                    BigDecimal amount, String treasury) {

    public boolean onAccount() {
        return invoiceNumber <= 0;
    }
}
