package com.hamza.account.features.treasury;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the user asked for: move this much from here to there, on this date.
 * <p>
 * A plain record with no JavaFX, built by the screen and validated by the service -
 * the service does not read a control and the screen does not write a row.
 */
public record TreasuryTransferCommand(int fromTreasuryId,
                                      int toTreasuryId,
                                      BigDecimal amount,
                                      LocalDate transferDate,
                                      String notes,
                                      int userId) {
}
