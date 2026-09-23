package com.hamza.account.features.currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One recorded rate: from {@code effectiveDate} on, one unit of the currency is worth {@code rate} units
 * of the base currency - "the dollar at 48.50" (docs/currency-plan.md ق-٢).
 * <p>
 * It stays in force until a later-dated rate of the same currency replaces it, so a rate dated tomorrow
 * is the ordinary case rather than an error: the rate agreed tonight for tomorrow's trading is entered
 * tonight.
 *
 * @param enteredBy who recorded it, or {@code null} once that account is gone
 * @param enteredAt when it was recorded, which is not the day it applies from
 */
public record ExchangeRate(int id,
                           int currencyId,
                           LocalDate effectiveDate,
                           BigDecimal rate,
                           String notes,
                           String enteredBy,
                           LocalDateTime enteredAt) {

    public ExchangeRate {
        Objects.requireNonNull(effectiveDate, "effectiveDate");
        Objects.requireNonNull(rate, "rate");
    }
}
