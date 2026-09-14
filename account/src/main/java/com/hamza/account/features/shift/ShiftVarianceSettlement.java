package com.hamza.account.features.shift;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** An immutable variance request whose cash movement waits for an open period. */
public record ShiftVarianceSettlement(long id, int shiftId, int treasuryId, String treasuryName,
                                      BigDecimal expectedBalance, BigDecimal actualBalance,
                                      BigDecimal difference, LocalDate originalShiftDate,
                                      int requestedByUserId, String requestedByName,
                                      LocalDateTime requestedAt) {
}
