package com.hamza.account.features.profitloss.statement;

import java.math.BigDecimal;

/**
 * Two kinds of loss that are real and that the profit does not count: what a posted stock count found
 * missing (or over), and what a till was short (or over) when its shift was closed. The owner decided
 * they are shown under the net profit, for information, and not subtracted from it - so no screen's
 * profit moves until that is decided otherwise.
 *
 * <p>Each figure is a positive magnitude. A count's line that found fewer than the book is a shortage and
 * one that found more is a surplus, valued at the item's buy price <b>today</b>: a count line keeps no cost
 * of its own, and the screen says what the valuation is. A till's difference is the one its close
 * recorded, counted less expected; a negative one is a shortage.</p>
 */
public record OutsideProfitFigures(BigDecimal stockShortage, BigDecimal stockSurplus,
                                   BigDecimal tillShortage, BigDecimal tillSurplus) {

    public static final OutsideProfitFigures ZERO = new OutsideProfitFigures(BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO);

    public OutsideProfitFigures {
        stockShortage = orZero(stockShortage);
        stockSurplus = orZero(stockSurplus);
        tillShortage = orZero(tillShortage);
        tillSurplus = orZero(tillSurplus);
    }

    /** What the four come to, surpluses less shortages - negative when more went missing than turned up. */
    public BigDecimal net() {
        return stockSurplus.subtract(stockShortage).add(tillSurplus).subtract(tillShortage);
    }

    public boolean isEmpty() {
        return stockShortage.signum() == 0 && stockSurplus.signum() == 0
                && tillShortage.signum() == 0 && tillSurplus.signum() == 0;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.abs();
    }
}
