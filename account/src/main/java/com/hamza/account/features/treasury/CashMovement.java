package com.hamza.account.features.treasury;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of {@code treasury_deposit_expenses}, for the list the screen shows. */
public record CashMovement(int id,
                           int treasuryId,
                           String treasuryName,
                           CashDirection direction,
                           CashCategory category,
                           BigDecimal amount,
                           LocalDate date,
                           String statement,
                           String description) {
}
