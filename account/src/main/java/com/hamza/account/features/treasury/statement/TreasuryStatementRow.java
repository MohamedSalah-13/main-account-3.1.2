package com.hamza.account.features.treasury.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JavaFX-free row from the unified treasury statement.
 * <p>
 * The movement comes twice: {@code income}, {@code output} and {@code runningBalance} in the base -
 * the books' figures - and the same three in the currency of the row's own treasury (V81), which for
 * a treasury in the base are the same numbers. Which set a statement shows is
 * {@link TreasuryStatementCurrency}'s answer, not the row's.
 */
public record TreasuryStatementRow(
        int referenceId, LocalDate movementDate, LocalDateTime recordedAt,
        TreasuryMovementKind kind, String storedLabel, int treasuryId, String treasuryName,
        BigDecimal income, BigDecimal output, BigDecimal runningBalance,
        BigDecimal incomeOwn, BigDecimal outputOwn, BigDecimal runningBalanceOwn,
        int userId, String username) {

    public TreasuryStatementRow {
        Objects.requireNonNull(movementDate, "movementDate");
        Objects.requireNonNull(kind, "kind");
        treasuryName = Objects.requireNonNullElse(treasuryName, "");
        storedLabel = Objects.requireNonNullElse(storedLabel, "");
        income = Objects.requireNonNullElse(income, BigDecimal.ZERO);
        output = Objects.requireNonNullElse(output, BigDecimal.ZERO);
        runningBalance = Objects.requireNonNullElse(runningBalance, BigDecimal.ZERO);
        incomeOwn = Objects.requireNonNullElse(incomeOwn, income);
        outputOwn = Objects.requireNonNullElse(outputOwn, output);
        runningBalanceOwn = Objects.requireNonNullElse(runningBalanceOwn, runningBalance);
        username = Objects.requireNonNullElse(username, "");
    }

    /** A row of a treasury in the base, whose figures in its own currency are its figures. */
    public TreasuryStatementRow(int referenceId, LocalDate movementDate, LocalDateTime recordedAt,
                                TreasuryMovementKind kind, String storedLabel, int treasuryId,
                                String treasuryName, BigDecimal income, BigDecimal output,
                                BigDecimal runningBalance, int userId, String username) {
        this(referenceId, movementDate, recordedAt, kind, storedLabel, treasuryId, treasuryName,
                income, output, runningBalance, income, output, runningBalance, userId, username);
    }

    public BigDecimal netMovement() { return income.subtract(output); }
}
