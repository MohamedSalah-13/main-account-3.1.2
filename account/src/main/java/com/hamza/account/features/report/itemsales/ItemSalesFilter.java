package com.hamza.account.features.report.itemsales;

import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Which items' sales, over which days: a period and, optionally, a text that narrows the items.
 *
 * <p>The text is the items list's own search ({@link ItemCatalogFilter#withSearch}) - digits are an id or
 * a barcode matched exactly, anything else a part of a name - so an item is found here the way it is found
 * on its own screen. A text narrows the rows and nothing else: the invoices' own discounts belong to no
 * item, so a narrowed report leaves them out rather than set every invoice's discount against a handful of
 * items ({@link ItemSalesReport#headerDiscounts()}).</p>
 */
public record ItemSalesFilter(LocalDate from, LocalDate to, String text) {

    public ItemSalesFilter {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("A period ends on or after its start: " + from + " > " + to);
        }
        text = text == null ? "" : text.strip();
    }

    public static ItemSalesFilter of(LocalDate from, LocalDate to) {
        return new ItemSalesFilter(from, to, "");
    }

    /**
     * A period and a text from the screen: a date left empty, or the two the wrong way round, is a refusal
     * with a sentence rather than a stack trace.
     */
    public static ItemSalesFilter fromScreen(LocalDate from, LocalDate to, String text) throws UserValidationException {
        LanguageManager language = LanguageManager.getInstance();
        if (from == null || to == null) {
            throw new UserValidationException(language.getString("report.error.date.range.required"));
        }
        if (from.isAfter(to)) {
            throw new UserValidationException(language.getString("party.statement.validation.period.reversed"));
        }
        return new ItemSalesFilter(from, to, text);
    }

    /** Whether some items are left out - the report's totals are then the listed items', not the period's. */
    public boolean narrows() {
        return !text.isEmpty();
    }

    /** The items screen's filter carrying this text, whose {@code WHERE} narrows the rows. */
    public ItemCatalogFilter catalogFilter() {
        return ItemCatalogFilter.EMPTY.withSearch(text);
    }
}
