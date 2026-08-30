package com.hamza.account.features.treasury;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of {@code treasury_transfers_and_names}, for the list the screen shows. */
public record TreasuryTransfer(int id,
                               int fromTreasuryId,
                               String fromTreasuryName,
                               int toTreasuryId,
                               String toTreasuryName,
                               BigDecimal amount,
                               LocalDate transferDate,
                               String notes) {
}
