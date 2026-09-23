package com.hamza.account.features.treasury;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of {@code treasury_transfers_and_names}, for the list the screen shows.
 * <p>
 * {@code amount} is what the books moved, in the base. A side in a foreign currency (V81) carries
 * what it gave or received in that currency and the currency's code; both are {@code null} for a side
 * in the base - see {@link TreasuryExchange}.
 */
public record TreasuryTransfer(int id,
                               int fromTreasuryId,
                               String fromTreasuryName,
                               int toTreasuryId,
                               String toTreasuryName,
                               BigDecimal amount,
                               LocalDate transferDate,
                               String notes,
                               BigDecimal fee,
                               BigDecimal amountFrom,
                               BigDecimal amountTo,
                               String currencyFrom,
                               String currencyTo) {

    /** What the sending treasury was charged for it; zero when the transfer cost nothing. */
    public TreasuryTransfer {
        fee = fee == null ? BigDecimal.ZERO : fee;
    }

    /** A transfer between two treasuries in the base. */
    public TreasuryTransfer(int id, int fromTreasuryId, String fromTreasuryName, int toTreasuryId,
                            String toTreasuryName, BigDecimal amount, LocalDate transferDate, String notes,
                            BigDecimal fee) {
        this(id, fromTreasuryId, fromTreasuryName, toTreasuryId, toTreasuryName, amount, transferDate, notes,
                fee, null, null, null, null);
    }

    /** What left the source, in the source's own currency. */
    public BigDecimal sent() {
        return amountFrom != null ? amountFrom : amount;
    }

    /** What reached the destination, in the destination's own currency. */
    public BigDecimal received() {
        return amountTo != null ? amountTo : amount;
    }

    /** Either side in a currency other than the base. */
    public boolean isForeign() {
        return amountFrom != null || amountTo != null;
    }
}
