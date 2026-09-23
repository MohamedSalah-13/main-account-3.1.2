package com.hamza.account.features.currency;

import java.util.Locale;

/**
 * What the currencies screen sends to be saved.
 * <p>
 * <b>No {@code base}.</b> Which currency the books are in is not a field of a form: it is
 * {@link CurrencyService#setBase}, one operation with a rule of its own (docs/currency-plan.md ق-٦) and
 * a transaction that takes the flag off one row and puts it on another. A form able to tick it would
 * be a form able to leave the books in no currency, or - for one save - in two.
 *
 * @param id 0 for a new currency
 */
public record CurrencyDraft(int id, String code, String name, String symbol, int decimalPlaces,
                            boolean active, int sortOrder) {

    public CurrencyDraft {
        code = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        name = name == null ? "" : name.strip();
        symbol = symbol == null ? "" : symbol.strip();
    }

    public boolean isNew() {
        return id <= 0;
    }

    /** The draft that edits a stored currency and changes nothing but whether it is offered. */
    public static CurrencyDraft switching(Currency currency, boolean active) {
        return new CurrencyDraft(currency.id(), currency.code(), currency.name(), currency.symbol(),
                currency.decimalPlaces(), active, currency.sortOrder());
    }
}
