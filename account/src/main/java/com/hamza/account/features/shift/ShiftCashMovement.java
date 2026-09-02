package com.hamza.account.features.shift;

import com.hamza.account.treasury.MovementLabel;

import java.math.BigDecimal;

/**
 * One heading's worth of cash movement inside a shift: what came in under it and
 * what went out, as {@code treasury_balance} groups it.
 * <p>
 * The label is a {@link MovementLabel} and not a string, so a heading the view
 * writes and this code does not know cannot be silently dropped into a total -
 * {@code MovementLabelTest} already fails the build if the two sides drift.
 *
 * @param label  the heading, exactly as {@code treasury_balance.information} holds it
 * @param income what entered the till under that heading
 * @param output what left it
 */
public record ShiftCashMovement(MovementLabel label, BigDecimal income, BigDecimal output) {
    public ShiftCashMovement {
        income = income == null ? BigDecimal.ZERO : income;
        output = output == null ? BigDecimal.ZERO : output;
    }
}
