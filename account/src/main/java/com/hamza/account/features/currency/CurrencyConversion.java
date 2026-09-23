package com.hamza.account.features.currency;

import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One line of the converter: an amount of one currency, in another, on a day.
 *
 * @param target the currency the amount is written in on this line
 * @param rate   the target's rate that day, or {@code null} for the base, whose rate is one
 * @param amount the converted amount, or {@code null} when the target has no rate on or before the day -
 *               shown as missing rather than as a zero, which would read as an answer
 */
public record CurrencyConversion(Currency target, RateInForce rate, BigDecimal amount) {

    public CurrencyConversion {
        Objects.requireNonNull(target, "target");
    }

    public boolean available() {
        return amount != null;
    }

    /**
     * {@code amount} of {@code from}, written in every other currency listed, each at its rate on the day
     * the rates were read for.
     *
     * @param currencies the currencies to write it in, in the order to list them; {@code from} is skipped
     * @param rates      the rate in force per currency id - {@link CurrencyService#ratesInForce}
     * @throws UserValidationException when {@code from} itself has no rate: nothing can be said then
     */
    public static List<CurrencyConversion> table(BigDecimal amount, Currency from, List<Currency> currencies,
                                                 Map<Integer, RateInForce> rates) throws UserValidationException {
        Objects.requireNonNull(amount, "amount");
        BigDecimal fromRate = rateOf(from, rates);
        if (fromRate == null) {
            throw new UserValidationException("currency.convert.error.no.rate");
        }
        List<CurrencyConversion> lines = new ArrayList<>();
        for (Currency target : currencies) {
            if (target.id() == from.id()) {
                continue;
            }
            BigDecimal toRate = rateOf(target, rates);
            RateInForce shown = target.base() ? null : rates.get(target.id());
            lines.add(new CurrencyConversion(target, shown,
                    toRate == null ? null : CurrencyConverter.convert(amount, fromRate, toRate, target)));
        }
        return List.copyOf(lines);
    }

    private static BigDecimal rateOf(Currency currency, Map<Integer, RateInForce> rates) {
        if (currency.base()) {
            return BigDecimal.ONE;
        }
        RateInForce inForce = rates.get(currency.id());
        return inForce == null ? null : inForce.rate();
    }
}
