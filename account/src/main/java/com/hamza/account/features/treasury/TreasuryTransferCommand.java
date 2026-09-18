package com.hamza.account.features.treasury;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the user asked for: move this much from here to there, on this date.
 * <p>
 * A plain record with no JavaFX, built by the screen and validated by the service -
 * the service does not read a control and the screen does not write a row.
 * <p>
 * {@code fee} is what the sending treasury was charged for the transfer - a wallet's withdrawal
 * fee, a bank's charge - and is zero for the ordinary case of moving cash between two drawers.
 * It is an expense on the sending treasury, never a deduction: the destination receives
 * {@code amount} in full, and the source gives up {@code amount + fee}.
 */
public record TreasuryTransferCommand(int fromTreasuryId,
                                      int toTreasuryId,
                                      BigDecimal amount,
                                      LocalDate transferDate,
                                      String notes,
                                      int userId,
                                      BigDecimal fee) {

    public TreasuryTransferCommand {
        fee = fee == null ? BigDecimal.ZERO : fee;
    }

    /** A transfer that cost nothing - what every caller meant before the fee existed. */
    public TreasuryTransferCommand(int fromTreasuryId, int toTreasuryId, BigDecimal amount,
                                   LocalDate transferDate, String notes, int userId) {
        this(fromTreasuryId, toTreasuryId, amount, transferDate, notes, userId, BigDecimal.ZERO);
    }
}
