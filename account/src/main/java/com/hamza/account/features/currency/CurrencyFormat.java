package com.hamza.account.features.currency;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * How an amount in a currency and a rate are written on a screen.
 * <p>
 * <b>An amount is written to its own currency's places, not to two.</b> {@code Columns.money} writes
 * every figure with two decimals, which is right for the books - they are in the base currency - and
 * wrong for a Kuwaiti dinar, which has three: 1.250 KWD written 1.25 hides a fils the converter
 * computed, and a yen written 1,500.00 invents two places it does not have.
 * <p>
 * <b>A rate is written with the places it has, up to the column's ten.</b> 48.5 is 48.5, not 48.50,
 * and 0.0000187 is not 0.00. Grouping and the separators follow the machine's symbols, as
 * {@code Columns.quantity} does, so the same number reads the same way on two screens.
 */
public final class CurrencyFormat {

    private CurrencyFormat() {
    }

    /** {@code value} in {@code currency}, to its places, without the symbol. Empty for {@code null}. */
    public static String amount(BigDecimal value, Currency currency) {
        if (value == null) {
            return "";
        }
        int places = currency.decimalPlaces();
        String pattern = places == 0 ? "#,##0" : "#,##0." + "0".repeat(places);
        DecimalFormat format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance());
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    /** A rate, with the places it has and no more. Empty for {@code null}. */
    public static String rate(BigDecimal rate) {
        if (rate == null) {
            return "";
        }
        DecimalFormat format = new DecimalFormat("#,##0." + "#".repeat(ExchangeRateRules.SCALE),
                DecimalFormatSymbols.getInstance());
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(rate);
    }

    /**
     * A derived figure - the inverse of a rate - to six significant digits. The inverse is computed to the
     * column's ten places ({@code CurrencyConverter.inverse}) and never stored, so ten places on screen
     * are digits nobody typed: "0.0771010023" is read as "0.077101".
     */
    public static String indicative(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return rate(value.round(new MathContext(6, RoundingMode.HALF_UP)));
    }

    /** A rate's movement in percent, signed, to two places - "+1.25%". Empty when there is none. */
    public static String change(BigDecimal percent) {
        if (percent == null) {
            return "";
        }
        DecimalFormat format = new DecimalFormat("+#,##0.00;-#,##0.00", DecimalFormatSymbols.getInstance());
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(percent) + "%";
    }
}
