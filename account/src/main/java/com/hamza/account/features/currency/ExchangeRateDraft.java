package com.hamza.account.features.currency;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the rates panel sends to be saved.
 * <p>
 * An edit may move the rate and its day but never its currency: a rate typed under the wrong currency is
 * deleted and entered again under the right one, which is two rows in the audit log saying so rather than
 * one saying a riyal rate quietly became a dollar rate.
 *
 * @param id 0 for a new rate
 */
public record ExchangeRateDraft(int id, int currencyId, LocalDate effectiveDate, BigDecimal rate, String notes) {

    public ExchangeRateDraft {
        notes = notes == null || notes.isBlank() ? null : notes.strip();
    }

    public boolean isNew() {
        return id <= 0;
    }
}
