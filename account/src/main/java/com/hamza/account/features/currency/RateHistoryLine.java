package com.hamza.account.features.currency;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One row of a currency's rate history: the rate, and how far it moved from the one it replaced.
 *
 * @param changePercent the movement from the rate dated before it, or {@code null} for the first rate
 */
public record RateHistoryLine(ExchangeRate rate, BigDecimal changePercent) {

    public RateHistoryLine {
        Objects.requireNonNull(rate, "rate");
    }

    /**
     * The history as the panel lists it, newest first, each rate beside its change from the next-older
     * one - worked out from the list itself, so the screen and {@link RateInForce#changePercent()} say the
     * same thing about the same two rows.
     *
     * @param newestFirst one currency's rates, newest day first - {@link CurrencyService#rates}
     */
    public static List<RateHistoryLine> of(List<ExchangeRate> newestFirst) {
        List<RateHistoryLine> lines = new ArrayList<>(newestFirst.size());
        for (int i = 0; i < newestFirst.size(); i++) {
            ExchangeRate rate = newestFirst.get(i);
            BigDecimal previous = i + 1 < newestFirst.size() ? newestFirst.get(i + 1).rate() : null;
            lines.add(new RateHistoryLine(rate, CurrencyConverter.changePercent(rate.rate(), previous)));
        }
        return List.copyOf(lines);
    }
}
