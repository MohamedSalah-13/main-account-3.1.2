package com.hamza.account.features.currency;

import com.hamza.controlsfx.error.UserValidationException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * What a currency may and may not become, decided over the currencies as they stand.
 * <p>
 * No database and no JavaFX: every refusal is a message key and every rule is a question about a plain
 * list, so {@code CurrencyRulesTest} can ask each one. The limits are V80's columns and CHECKs, restated
 * here so the ordinary mistake reads as a sentence rather than as a reference code; the uniqueness of a
 * code or a name is still the index's decision, and the service only asks first as a courtesy.
 *
 * <h2>The base currency</h2>
 * Changing it relabels every figure in the books and converts none of them: every amount column was
 * written in whatever the base was. That is right exactly while it is a <i>correction</i> - V80 guessed
 * the base from the settings screen, and a shop whose setting was never saved got the Egyptian pound.
 * Once a rate is recorded it is wrong, because a rate is written in the base currency: "the dollar at
 * 48.50" means 48.50 pounds, and after the base became the riyal the same row would read 48.50 riyals.
 * So the base is changed only while no rate exists at all (docs/currency-plan.md ق-٦).
 */
public final class CurrencyRules {

    /** {@code currency.name} is {@code VARCHAR(50)}. */
    public static final int NAME_MAX = 50;

    /** {@code currency.symbol} and {@code currency.symbol_latin} are {@code VARCHAR(10)}. */
    public static final int SYMBOL_MAX = 10;

    /** {@code currency_decimals_chk}: 0 for the yen, 3 for the Kuwaiti dinar, and nothing past it. */
    public static final int DECIMALS_MAX = 3;

    /** ISO 4217 - the same pattern as {@code currency_code_chk}, case included. */
    private static final Pattern CODE = Pattern.compile("[A-Z]{3}");

    private CurrencyRules() {
    }

    /**
     * Refuses a draft the currencies as they stand cannot take.
     *
     * @param existing every currency, active or not, as read from the database
     */
    public static void requireValid(CurrencyDraft draft, List<Currency> existing) throws UserValidationException {
        if (!CODE.matcher(draft.code()).matches()) {
            throw new UserValidationException("currency.error.code");
        }
        if (draft.name().isEmpty()) {
            throw new UserValidationException("currency.error.name");
        }
        if (length(draft.name()) > NAME_MAX) {
            throw new UserValidationException("currency.error.name.length");
        }
        if (draft.symbol().isEmpty()) {
            throw new UserValidationException("currency.error.symbol");
        }
        if (length(draft.symbol()) > SYMBOL_MAX) {
            throw new UserValidationException("currency.error.symbol.length");
        }
        // Optional; but one typed is what the English interface prints, so an Arabic letter in it is
        // the Arabic symbol typed into the wrong box.
        if (draft.latinSymbol() != null) {
            if (length(draft.latinSymbol()) > SYMBOL_MAX) {
                throw new UserValidationException("currency.error.latin.symbol.length");
            }
            if (Currency.hasArabicLetter(draft.latinSymbol())) {
                throw new UserValidationException("currency.error.latin.symbol.script");
            }
        }
        if (draft.decimalPlaces() < 0 || draft.decimalPlaces() > DECIMALS_MAX) {
            throw new UserValidationException("currency.error.decimals");
        }
        if (draft.sortOrder() < 0) {
            throw new UserValidationException("currency.error.sort");
        }

        Currency stored = draft.isNew() ? null : find(existing, draft.id());
        if (!draft.isNew() && stored == null) {
            throw new UserValidationException("currency.error.not.found");
        }
        for (Currency other : existing) {
            if (other.id() == draft.id()) {
                continue;
            }
            if (other.code().equalsIgnoreCase(draft.code())) {
                throw new UserValidationException("currency.error.code.taken");
            }
            if (other.name().equalsIgnoreCase(draft.name())) {
                throw new UserValidationException("currency.error.name.taken");
            }
        }
        // The CHECK refuses it too (currency_base_active_chk); said here as a sentence.
        if (stored != null && stored.base() && !draft.active()) {
            throw new UserValidationException("currency.error.base.stop");
        }
    }

    /**
     * Refuses to make this currency the base.
     *
     * @param recordedRates how many exchange rates exist, for any currency - read inside the
     *                      transaction that moves the flag, with every currency row locked
     */
    public static void requireCanBecomeBase(Currency currency, int recordedRates) throws UserValidationException {
        if (currency == null) {
            throw new UserValidationException("currency.error.not.found");
        }
        if (!currency.active()) {
            throw new UserValidationException("currency.error.base.inactive");
        }
        if (recordedRates > 0) {
            throw new UserValidationException("currency.error.base.locked");
        }
    }

    /**
     * Refuses to delete the base. What else holds a currency - its rates today, a treasury or a document
     * in a later phase - is {@code DeleteRegistry.CURRENCIES}'s to say, with the count in the message.
     */
    public static void requireDeletable(Currency currency) throws UserValidationException {
        if (currency == null) {
            throw new UserValidationException("currency.error.not.found");
        }
        if (currency.base()) {
            throw new UserValidationException("currency.error.base.delete");
        }
    }

    static Currency find(List<Currency> currencies, int id) {
        for (Currency currency : currencies) {
            if (currency.id() == id) {
                return currency;
            }
        }
        return null;
    }

    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }
}
