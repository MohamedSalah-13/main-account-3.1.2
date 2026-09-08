package com.hamza.account.features.treasury.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/** JavaFX-free row from the unified treasury statement. */
public record TreasuryStatementRow(
        int referenceId, LocalDate movementDate, LocalDateTime recordedAt,
        TreasuryMovementKind kind, String storedLabel, int treasuryId, String treasuryName,
        BigDecimal income, BigDecimal output, BigDecimal runningBalance,
        int userId, String username) {

    public TreasuryStatementRow {
        Objects.requireNonNull(movementDate, "movementDate");
        Objects.requireNonNull(kind, "kind");
        treasuryName = Objects.requireNonNullElse(treasuryName, "");
        storedLabel = Objects.requireNonNullElse(storedLabel, "");
        income = Objects.requireNonNullElse(income, BigDecimal.ZERO);
        output = Objects.requireNonNullElse(output, BigDecimal.ZERO);
        runningBalance = Objects.requireNonNullElse(runningBalance, BigDecimal.ZERO);
        username = Objects.requireNonNullElse(username, "");
    }

    public BigDecimal netMovement() { return income.subtract(output); }
}
