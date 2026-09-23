package com.hamza.account.features.profitloss.statement;

import com.hamza.account.features.currency.difference.ExchangeFigures;

import java.math.BigDecimal;

/**
 * What is real and is not counted in the profit: what a posted stock count found missing (or over), what a
 * till was short (or over) when its shift was closed, and what the exchange rates made of the accounts held
 * in a foreign currency. The owner decided each is shown under the net profit, for information, and not
 * subtracted from it - so no screen's profit moves until that is decided otherwise, for all of them at once.
 *
 * <p>The four count and till figures are positive magnitudes. A count's line that found fewer than the book
 * is a shortage and one that found more is a surplus, valued at the item's buy price <b>today</b>: a count
 * line keeps no cost of its own, and the screen says what the valuation is. A till's difference is the one
 * its close recorded, counted less expected; a negative one is a shortage.</p>
 *
 * <p>The exchange figures are signed, a gain positive, and are the exchange differences report's own totals
 * (docs/currency-plan.md §16): realized in the period and the change in the unrealized over it.
 * {@link ExchangeFigures#NONE} for a shop that holds nothing in a foreign currency, which draws no line.</p>
 */
public record OutsideProfitFigures(BigDecimal stockShortage, BigDecimal stockSurplus,
                                   BigDecimal tillShortage, BigDecimal tillSurplus, ExchangeFigures exchange) {

    public static final OutsideProfitFigures ZERO = new OutsideProfitFigures(BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO);

    public OutsideProfitFigures {
        stockShortage = orZero(stockShortage);
        stockSurplus = orZero(stockSurplus);
        tillShortage = orZero(tillShortage);
        tillSurplus = orZero(tillSurplus);
        exchange = exchange == null ? ExchangeFigures.NONE : exchange;
    }

    /** The count and till figures alone, with no exchange differences - what the repository reads. */
    public OutsideProfitFigures(BigDecimal stockShortage, BigDecimal stockSurplus, BigDecimal tillShortage,
                                BigDecimal tillSurplus) {
        this(stockShortage, stockSurplus, tillShortage, tillSurplus, ExchangeFigures.NONE);
    }

    public OutsideProfitFigures withExchange(ExchangeFigures figures) {
        return new OutsideProfitFigures(stockShortage, stockSurplus, tillShortage, tillSurplus, figures);
    }

    /** What they all come to, gains less losses - negative when more was lost than turned up. */
    public BigDecimal net() {
        return stockSurplus.subtract(stockShortage).add(tillSurplus).subtract(tillShortage).add(exchange.result());
    }

    public boolean isEmpty() {
        return stockShortage.signum() == 0 && stockSurplus.signum() == 0
                && tillShortage.signum() == 0 && tillSurplus.signum() == 0
                && exchange.realized().signum() == 0 && exchange.unrealizedChange().signum() == 0;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.abs();
    }
}
