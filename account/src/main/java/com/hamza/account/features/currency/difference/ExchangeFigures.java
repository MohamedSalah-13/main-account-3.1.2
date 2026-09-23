package com.hamza.account.features.currency.difference;

import java.math.BigDecimal;

/**
 * What the profit and loss statement shows of the exchange differences, under the net profit and not
 * counted in it (docs/currency-plan.md §16 ق-هـ١, ق-هـ٣).
 *
 * @param accounts         the foreign accounts - none means there is nothing to show, and no line is drawn
 * @param realized         realized in the period, a gain positive
 * @param unrealizedChange how far the unrealized moved over it, over the accounts that could be valued
 * @param withoutRate      the accounts left out of {@code unrealizedChange} for want of a rate
 */
public record ExchangeFigures(int accounts, BigDecimal realized, BigDecimal unrealizedChange, int withoutRate) {

    public static final ExchangeFigures NONE = new ExchangeFigures(0, BigDecimal.ZERO, BigDecimal.ZERO, 0);

    public ExchangeFigures {
        realized = realized == null ? BigDecimal.ZERO : realized;
        unrealizedChange = unrealizedChange == null ? BigDecimal.ZERO : unrealizedChange;
    }

    /** Whether the shop holds anything in a foreign currency at all. */
    public boolean applies() {
        return accounts > 0;
    }

    public BigDecimal result() {
        return realized.add(unrealizedChange);
    }
}
