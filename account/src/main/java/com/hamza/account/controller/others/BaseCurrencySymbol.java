package com.hamza.account.controller.others;

import com.hamza.account.features.currency.CurrencyService;
import lombok.extern.log4j.Log4j2;

/**
 * The symbol printed beside an amount of the books - the base currency's (V80), read once and kept.
 * <p>
 * <b>It replaced two answers to one question.</b> The price-check kiosk printed the symbol of whatever
 * locale the settings screen had stored, and the dashboard printed {@code report.dashboard.currency.symbol}
 * from the bundle - "ج.م" whatever the shop's currency was. A shop in Riyadh saw pounds on its dashboard
 * and riyals on its kiosk. Both read this now, and this reads the one row V80 flags as the base.
 * <p>
 * Kept because the kiosk and the dashboard write a figure many times a second and a symbol does not
 * change between them; {@link #forget()} is called on {@code CurrenciesChanged}, which the relay also
 * raises when the base moves on another till. A database that cannot be read gives no symbol rather than
 * a wrong one.
 */
@Log4j2
public final class BaseCurrencySymbol {

    private static volatile String cached;

    private BaseCurrencySymbol() {
    }

    public static String get() {
        String symbol = cached;
        if (symbol == null) {
            symbol = read();
            cached = symbol;
        }
        return symbol;
    }

    /** The next {@link #get()} reads the base again. */
    public static void forget() {
        cached = null;
    }

    private static String read() {
        CurrencyService currencies = ServiceRegistry.get(CurrencyService.class);
        if (currencies == null) {
            return "";
        }
        try {
            return currencies.base().symbol();
        } catch (Exception e) {
            log.warn("Could not read the base currency; amounts are written without a symbol", e);
            return "";
        }
    }
}
