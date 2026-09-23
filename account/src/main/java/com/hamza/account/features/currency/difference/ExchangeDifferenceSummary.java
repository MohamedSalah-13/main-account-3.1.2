package com.hamza.account.features.currency.difference;

import java.math.BigDecimal;
import java.util.List;

/**
 * The report's totals, each the sum of its rows' - in the base, where every account's differences can be
 * added whatever its currency.
 *
 * <p>A row that could not be valued (ق-هـ٥) is in {@link #realized} - the realized needs no rate - and out
 * of every unrealized total, and {@link #currenciesWithoutRate} names what it waits for. So the totals are
 * what they say and the screen says what they leave out, rather than counting a missing rate as zero.</p>
 */
public record ExchangeDifferenceSummary(int accounts, BigDecimal realized, BigDecimal unrealizedChange,
                                        BigDecimal unrealizedEnd, BigDecimal totalEnd, int accountsWithoutRate,
                                        List<String> currenciesWithoutRate) {

    public ExchangeDifferenceSummary {
        currenciesWithoutRate = List.copyOf(currenciesWithoutRate);
    }

    /** The period's result over the accounts that could be valued, and the realized of all of them. */
    public BigDecimal result() {
        return realized.add(unrealizedChange);
    }

    public ExchangeFigures figures() {
        return new ExchangeFigures(accounts, realized, unrealizedChange, accountsWithoutRate);
    }

    static ExchangeDifferenceSummary of(List<ExchangeDifferenceRow> rows) {
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal change = BigDecimal.ZERO;
        BigDecimal unrealized = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        int withoutRate = 0;
        List<String> codes = new java.util.ArrayList<>();
        for (ExchangeDifferenceRow row : rows) {
            realized = realized.add(row.realizedPeriod());
            if (row.valued()) {
                change = change.add(row.unrealizedChange());
            } else {
                withoutRate++;
                if (!codes.contains(row.currency().code())) {
                    codes.add(row.currency().code());
                }
            }
            if (row.unrealizedEnd() != null) {
                unrealized = unrealized.add(row.unrealizedEnd());
                total = total.add(row.totalEnd());
            }
        }
        return new ExchangeDifferenceSummary(rows.size(), realized, change, unrealized, total, withoutRate, codes);
    }
}
