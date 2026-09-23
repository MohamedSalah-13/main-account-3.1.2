package com.hamza.account.features.currency.online;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What a rate source answered for one base currency: how many units of each other currency one unit of
 * the base buys, as the source published them (docs/currency-plan.md ق-٩, §12).
 * <p>
 * <b>The direction is the source's, not this system's.</b> A source asked about the pound answers "one
 * pound is 0.0206 dollars"; a rate here is "the dollar at 48.54 pounds". {@link OnlineRateMath} turns
 * one into the other, once, where it is tested - nothing else reads {@link #perBaseUnit} as a rate.
 *
 * @param source      the name the paper and the rate's notes give it, e.g. "ExchangeRate-API"
 * @param site        where it lives, shown beside its name - the attribution its terms ask for
 * @param baseCode    the currency asked about, ISO 4217
 * @param published   the day the source says its figures are from
 * @param perBaseUnit per ISO code, capital letters: that currency's units per one unit of the base
 */
public record QuotedRates(String source, String site, String baseCode, LocalDate published,
                          Map<String, BigDecimal> perBaseUnit) {

    public QuotedRates {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(baseCode, "baseCode");
        Objects.requireNonNull(published, "published");
        perBaseUnit = Map.copyOf(perBaseUnit);
    }

    /** What one unit of the base buys of {@code code}, or {@code null} when the source did not quote it. */
    public BigDecimal quoteFor(String code) {
        return code == null ? null : perBaseUnit.get(code.toUpperCase(Locale.ROOT));
    }
}
