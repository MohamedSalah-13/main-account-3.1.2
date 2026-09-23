package com.hamza.account.features.currency.difference;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.RateInForce;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Walks every foreign account at its average rate and reads what the period made of it
 * (docs/currency-plan.md §16). No database and no toolkit: the movements, the currencies and the two days'
 * rates are handed in, so every rule is tested on figures worked out by hand.
 *
 * <p>The period's start is the position after every movement dated before its first day, valued at the rate
 * in force on the day before it; its end, after every movement up to its last day, at that day's rate. A
 * movement is walked in the order its own statement lists it, which the repository answers in.</p>
 */
public final class ExchangeDifferenceCalculator {

    private ExchangeDifferenceCalculator() {
    }

    /**
     * @param movements  every movement of every account in {@code accounts} up to {@code to}, each account's in
     *                   its statement's order
     * @param currencies the catalogue by id
     * @param ratesStart the rates in force on the day before {@code from}, by currency
     * @param ratesEnd   the rates in force on {@code to}, by currency
     */
    public static ExchangeDifferenceReport report(LocalDate from, LocalDate to, List<ExchangeAccount> accounts,
                                                  List<ExchangeMovement> movements,
                                                  Map<Integer, Currency> currencies,
                                                  Map<Integer, RateInForce> ratesStart,
                                                  Map<Integer, RateInForce> ratesEnd) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Map<String, List<ExchangeMovement>> byAccount = new LinkedHashMap<>();
        for (ExchangeMovement movement : movements) {
            byAccount.computeIfAbsent(key(movement.kind(), movement.accountId()), k -> new ArrayList<>())
                    .add(movement);
        }
        List<ExchangeDifferenceRow> rows = new ArrayList<>();
        for (ExchangeAccount account : accounts) {
            Currency currency = currencies.get(account.currencyId());
            if (currency == null) {
                throw new IllegalStateException("No currency " + account.currencyId() + " for " + account);
            }
            rows.add(row(account, currency, from, to,
                    byAccount.getOrDefault(key(account.kind(), account.id()), List.of()),
                    rate(ratesStart, account.currencyId()), rate(ratesEnd, account.currencyId())));
        }
        rows.sort(Comparator.comparing((ExchangeDifferenceRow row) -> row.account().kind())
                .thenComparing(row -> row.account().name())
                .thenComparingInt(row -> row.account().id()));
        return new ExchangeDifferenceReport(from, to, rows, ExchangeDifferenceSummary.of(rows));
    }

    static ExchangeDifferenceRow row(ExchangeAccount account, Currency currency, LocalDate from, LocalDate to,
                                     List<ExchangeMovement> movements, BigDecimal rateStart,
                                     BigDecimal rateEnd) {
        ExchangeAccountKind kind = account.kind();
        AveragePosition position = new AveragePosition();
        AveragePosition.Snapshot start = null;
        List<ExchangeMovementLine> lines = new ArrayList<>();
        for (ExchangeMovement movement : movements) {
            if (movement.date().isAfter(to)) {
                break;
            }
            boolean inPeriod = !movement.date().isBefore(from);
            if (inPeriod && start == null) {
                start = position.snapshot();
            }
            BigDecimal realizedBefore = position.snapshot().realizedToDate();
            position.apply(movement.own(), movement.book());
            if (inPeriod) {
                AveragePosition.Snapshot after = position.snapshot();
                lines.add(new ExchangeMovementLine(movement.date(), movement.labelKey(), movement.reference(),
                        movement.own(), movement.book(), after.own(), after.averageRate(),
                        kind.gain(after.realizedToDate().subtract(realizedBefore))));
            }
        }
        AveragePosition.Snapshot end = position.snapshot();
        if (start == null) {
            start = end;
        }
        return new ExchangeDifferenceRow(account, currency,
                start.own(), start.book(), start.averageRate(), rateStart,
                end.own(), end.book(), end.averageRate(), rateEnd, end.valueAt(rateEnd),
                kind.gain(end.realizedToDate().subtract(start.realizedToDate())),
                kind.gain(end.realizedToDate()),
                kind.gain(start.unrealizedAt(rateStart)),
                kind.gain(end.unrealizedAt(rateEnd)),
                lines);
    }

    private static BigDecimal rate(Map<Integer, RateInForce> rates, int currencyId) {
        RateInForce rate = rates == null ? null : rates.get(currencyId);
        return rate == null ? null : rate.rate();
    }

    private static String key(ExchangeAccountKind kind, int id) {
        return kind.name() + ":" + id;
    }
}
