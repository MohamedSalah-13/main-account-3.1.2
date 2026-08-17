package com.hamza.account.features.stockledger;

import java.time.LocalDate;

/**
 * One row of {@code stock_movements} - a plain POJO on purpose (see
 * {@code docs/new-code-rules.md} ق-ج1): this table has no screen of its own yet, so
 * nothing here needs a JavaFX {@code Property} or a {@code DForColumnTable} ancestor,
 * and the day it does, this is what a DTO for it should already look like.
 * <p>
 * Mirrors the table's own {@code CHECK} constraints rather than re-deriving them:
 * exactly one of {@link #quantityIn}/{@link #quantityOut} is positive, the other zero.
 */
public final class StockMovement {

    private final int itemId;
    private final int stockId;
    private final LocalDate movementDate;
    private final MovementType movementType;
    private final double quantityIn;
    private final double quantityOut;
    private final int unitId;
    private final double unitValue;
    private final String referenceType;
    private final long referenceId;
    private final Integer userId;

    public StockMovement(int itemId, int stockId, LocalDate movementDate, MovementType movementType,
                         double quantityIn, double quantityOut, int unitId, double unitValue,
                         String referenceType, long referenceId, Integer userId) {
        if ((quantityIn > 0) == (quantityOut > 0)) {
            throw new IllegalArgumentException(
                    "exactly one of quantityIn/quantityOut must be positive, the other zero: "
                            + quantityIn + "/" + quantityOut);
        }
        this.itemId = itemId;
        this.stockId = stockId;
        this.movementDate = movementDate;
        this.movementType = movementType;
        this.quantityIn = quantityIn;
        this.quantityOut = quantityOut;
        this.unitId = unitId;
        this.unitValue = unitValue;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.userId = userId;
    }

    public int itemId() {
        return itemId;
    }

    public int stockId() {
        return stockId;
    }

    public LocalDate movementDate() {
        return movementDate;
    }

    public MovementType movementType() {
        return movementType;
    }

    public double quantityIn() {
        return quantityIn;
    }

    public double quantityOut() {
        return quantityOut;
    }

    public int unitId() {
        return unitId;
    }

    public double unitValue() {
        return unitValue;
    }

    public String referenceType() {
        return referenceType;
    }

    public long referenceId() {
        return referenceId;
    }

    public Integer userId() {
        return userId;
    }
}
