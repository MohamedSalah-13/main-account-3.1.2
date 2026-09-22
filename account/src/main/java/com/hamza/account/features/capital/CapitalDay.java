package com.hamza.account.features.capital;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One day's owner's movements on one treasury. Both amounts are positive. */
public record CapitalDay(LocalDate day, int treasuryId, String treasuryName, BigDecimal paidIn,
                         BigDecimal drawn, int movements) {
}
