package com.hamza.account.features.treasury;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of {@code treasury_deposit_expenses}, for the list the screen shows.
 * <p>
 * {@code amount} is in the base. On a treasury in a foreign currency (V81) {@code foreignAmount} is
 * what moved in its own currency, {@code exchangeRate} the day's rate it was valued at and
 * {@code currencyCode} the treasury's currency; all three are {@code null} for a treasury in the base.
 */
public record CashMovement(int id,
                           int treasuryId,
                           String treasuryName,
                           CashDirection direction,
                           CashCategory category,
                           BigDecimal amount,
                           LocalDate date,
                           String statement,
                           String description,
                           BigDecimal foreignAmount,
                           BigDecimal exchangeRate,
                           String currencyCode) {

    /** A movement on a treasury in the base. */
    public CashMovement(int id, int treasuryId, String treasuryName, CashDirection direction,
                        CashCategory category, BigDecimal amount, LocalDate date, String statement,
                        String description) {
        this(id, treasuryId, treasuryName, direction, category, amount, date, statement, description,
                null, null, null);
    }

    /** On a treasury in a foreign currency. */
    public boolean isForeign() {
        return foreignAmount != null;
    }

    /** What moved, in the treasury's own currency. */
    public BigDecimal ownAmount() {
        return foreignAmount != null ? foreignAmount : amount;
    }
}
