package com.hamza.account.otherSetting;

import java.util.*;

import static com.hamza.account.config.PropertiesName.getSettingCurrency;

public class Currency_Setting {

    public static final String CURRENCY_DISPLAY_FORMAT = "Display name: %s, symbol: %s, code: %s, numericCode: %s";

    /**
     * The currencies the settings screen offers, and the only list
     * {@link #getCurrency()} looks the saved choice up in.
     * <p>
     * The filter used to be written out in both places. Two copies of "which currencies
     * exist" is one copy too many: widen one and the saved value stops being findable in
     * the other, and the screen silently shows no currency at all.
     * <p>
     * {@code equals} rather than {@code contains}: a language tag is matched whole, not
     * as a fragment of another one.
     */
    public static List<Map.Entry<Locale, Currency>> selectableCurrencies() {
        return listOfCurrency2().stream()
                .filter(entry -> entry.getKey().getLanguage().equals("ar"))
                .toList();
    }

    public static Optional<Map.Entry<Locale, Currency>> getCurrency() {
        String pro = getSettingCurrency();
        return selectableCurrencies().stream()
                .filter(localeCurrencyEntry -> localeCurrencyEntry.getKey().toString().equals(pro))
                .findFirst();
    }

    public static List<Map.Entry<Locale, Currency>> listOfCurrency2() {
        return Arrays.stream(Locale.getAvailableLocales())
                .collect(HashMap<Locale, Currency>::new,
                        (map, locale) -> map.put(locale, getLocaleCurrency(locale)), HashMap::putAll)
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().getCurrencyCode().equals("ILS"))
//                .map(entry -> formatCurrency(entry.getKey(), entry.getValue()).name)
                .toList();
    }

    public static List<String> listOfCurrency() {
        return Arrays.stream(Locale.getAvailableLocales()).filter(locale -> locale.getLanguage().contains("ar"))
                .collect(HashMap<Locale, Currency>::new,
                        (map, locale) -> map.put(locale, getLocaleCurrency(locale)), HashMap::putAll)
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().getCurrencyCode().equals("ILS"))
                .map(entry -> formatCurrency(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static String formatCurrency(Currency currency) {
        return String.format(CURRENCY_DISPLAY_FORMAT,
                currency.getDisplayName(), currency.getSymbol(), currency.getCurrencyCode(), currency.getNumericCodeAsString());
    }

    public static String formatCurrency(Locale locale, Currency currency) {
        return String.format(currency.getDisplayName(locale) + "-" + currency.getSymbol(locale));
    }

    public static Currency getLocaleCurrency(Locale locale) {
        try {
            return Currency.getInstance(locale);
        } catch (IllegalArgumentException iae) {
            return null;
        }
    }

}
