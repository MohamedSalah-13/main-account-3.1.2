package com.hamza.account.controller.others;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyService;
import com.hamza.controlsfx.language.LanguageManager;
import lombok.extern.log4j.Log4j2;

/**
 * The symbol printed beside an amount of the books - the base currency's (V80), in the interface's
 * language.
 * <p>
 * <b>It replaced two answers to one question.</b> The price-check kiosk printed the symbol of whatever
 * locale the settings screen had stored, and the dashboard printed {@code report.dashboard.currency.symbol}
 * from the bundle - "ج.م" whatever the shop's currency was. A shop in Riyadh saw pounds on its dashboard
 * and riyals on its kiosk. Both read this now, and this reads the one row V80 flags as the base.
 * <p>
 * <b>The currency is kept, not the symbol.</b> The bundle key had a value per language - "ج.م" and
 * "L.E." - and a currency has a symbol per language for the same reason ({@link Currency#symbolFor}), so
 * the symbol is chosen on every call from the language in force: switching the interface to English
 * needs no {@link #forget()}. Kept because the kiosk and the dashboard write a figure many times a
 * second and the base does not change between them; {@link #forget()} is called on
 * {@code CurrenciesChanged}, which the relay also raises when the base moves on another till. A database
 * that cannot be read gives no symbol rather than a wrong one.
 */
@Log4j2
public final class BaseCurrencySymbol {

    /** What was read, a failure included - so a database that cannot be read is not asked again per figure. */
    private record Read(Currency base) {
    }

    private static volatile Read cached;

    private BaseCurrencySymbol() {
    }

    public static String get() {
        Read read = cached;
        if (read == null) {
            read = new Read(read());
            cached = read;
        }
        return read.base() == null ? "" : read.base().symbolFor(LanguageManager.getInstance().getCurrentLocale());
    }

    /** The next {@link #get()} reads the base again. */
    public static void forget() {
        cached = null;
    }

    private static Currency read() {
        CurrencyService currencies = ServiceRegistry.get(CurrencyService.class);
        if (currencies == null) {
            return null;
        }
        try {
            return currencies.base();
        } catch (Exception e) {
            log.warn("Could not read the base currency; amounts are written without a symbol", e);
            return null;
        }
    }
}
