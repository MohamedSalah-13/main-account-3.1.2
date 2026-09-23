package com.hamza.account.features.returns.reasons;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One return under a reason, as the report's panel lists it.
 *
 * @param sourceInvoice the invoice it reverses, or 0 for a free return
 * @param value         its total less its own discount - what the reason's row sums
 */
public record ReturnDocument(long number, LocalDate date, String party, long sourceInvoice, BigDecimal value,
                             String notes) {

    public ReturnDocument {
        Objects.requireNonNull(date, "date");
        party = Objects.requireNonNullElse(party, "");
        value = value == null ? BigDecimal.ZERO : value;
        notes = Objects.requireNonNullElse(notes, "");
    }
}
