package com.hamza.account.features.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
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
 * @param symbol        what is printed beside an amount in it in the Arabic interface
 * @param latinSymbol   what is printed beside it in any other interface; empty when none is set - see
 *                      {@link #symbolFor}
 * @param decimalPlaces the places an amount in it is rounded to, 0 to 3 ({@code KWD} has three)
 * @param base          the currency every amount in the books is in; exactly one row says so
 * @param active        offered by the pickers; a stopped currency keeps its rates and its history
 * @param sortOrder     where it sits in every list, lowest first, ties by code
 */
public record Currency(int id,
                       String code,
                       String name,
                       String symbol,
                       String latinSymbol,
                       int decimalPlaces,
                       boolean base,
                       boolean active,
                       int sortOrder) {

    public Currency {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        symbol = symbol == null ? "" : symbol;
        latinSymbol = latinSymbol == null ? "" : latinSymbol;
    }

    /**
     * The symbol written beside an amount in an interface in {@code locale}'s language - the way the
     * program itself is written in the language chosen in the settings.
     * <p>
     * Arabic reads {@link #symbol}. Any other language reads {@link #latinSymbol}, and where the shop set
     * none, the Arabic symbol itself when it has no Arabic letter in it ("$", "€", "Fdj"), else the ISO
     * code. An Arabic symbol in an English line - "1,050.00 ج.م" - is what the dashboard would have
     * written had it read {@code symbol} alone, and before V80 it wrote "L.E." there.
     */
    public String symbolFor(Locale locale) {
        if (locale != null && "ar".equals(locale.getLanguage())) {
            return symbol;
        }
        if (!latinSymbol.isBlank()) {
            return latinSymbol;
        }
        return hasArabicLetter(symbol) || symbol.isBlank() ? code : symbol;
    }

    /** True when {@code text} holds a letter of the Arabic script - what a Latin symbol may not. */
    static boolean hasArabicLetter(String text) {
        return text != null && text.codePoints()
                .anyMatch(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.ARABIC);
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
