package com.hamza.account.features.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A currency the shop deals in, as a row of {@code currency} (V80).
 * <p>
 * <b>The books are in one currency, and {@link #base()} says which.</b> Every amount column in this
 * database was written in it before V80 existed and still is: V80 named the currency the figures had
 * always been in rather than converting anything. A foreign amount, when a later phase records one, is
 * written beside its base figure and never instead of it (docs/currency-plan.md ق-١).
 *
 * @param code          ISO 4217, three capital Latin letters - what a rate and a paper name it by
 * @param name          what the shop calls it
 * @param symbol        what is printed beside an amount in it
 * @param decimalPlaces the places an amount in it is rounded to, 0 to 3 ({@code KWD} has three)
 * @param base          the currency every amount in the books is in; exactly one row says so
 * @param active        offered by the pickers; a stopped currency keeps its rates and its history
 * @param sortOrder     where it sits in every list, lowest first, ties by code
 */
public record Currency(int id,
                       String code,
                       String name,
                       String symbol,
                       int decimalPlaces,
                       boolean base,
                       boolean active,
                       int sortOrder) {

    public Currency {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        symbol = symbol == null ? "" : symbol;
    }

    /** An amount in this currency, rounded the way every figure in it is: half up, to its own places. */
    public BigDecimal round(BigDecimal amount) {
        return amount.setScale(decimalPlaces, RoundingMode.HALF_UP);
    }

    /** "USD - دولار أمريكي": the code first, because it is what a rate and a paper name it by. */
    public String label() {
        return code + " - " + name;
    }
}
