package com.hamza.account.otherSetting;

import java.util.*;

import static com.hamza.account.config.PropertiesName.getSettingCurrency;

public class Currency_Setting {

    public static final String CURRENCY_DISPLAY_FORMAT = "Display name: %s, symbol: %s, code: %s, numericCode: %s";

    public static Optional<Map.Entry<Locale, Currency>> getCurrency() {
        String pro = getSettingCurrency();
        List<Map.Entry<Locale, Currency>> entries = listOfCurrency2().stream()
                .filter(localeCurrencyEntry -> localeCurrencyEntry.getKey().getLanguage().contains("ar"))
                .toList();

        return entries.stream()
                .filter(localeCurrencyEntry -> localeCurrencyEntry.getKey().toString().equals(pro))
                .findFirst();
//        return first.map(localeCurrencyEntry -> localeCurrencyEntry.getValue().getDisplayName(localeCurrencyEntry.getKey())).orElse(null);

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
