package com.hamza.account.features.invoice;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.finance.MoneyMath;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The currency an invoice screen's figures are typed in, and the rate it offers prices at
 * (V83, docs/currency-plan.md §15 ق-د٧).
 * <p>
 * An item's prices and its cost are kept in the base. On a document typed in a foreign currency the screen
 * offers an item's price converted at the day's rate - {@link #fromBase} - and holds a sale above its cost
 * by what will be stored - {@link #toBase}, the typed price times the rate rounded to money, which is the
 * base price the save writes (§15 ق-د٣). A document in the base answers both with the figure unchanged.
 * <p>
 * This rate is only what the screen <em>offers</em>. The rate a document is stored at is the save's to
 * decide ({@code InvoicePartyCurrency.rateFor}): an edit keeps its own, a return takes its invoice's. So
 * nothing here writes a figure; it prices one.
 *
 * @param currency the document's currency, or {@code null} for the base
 * @param rate     base units per one unit of it on the document's day, or {@code null} when there is none
 */
public record DocumentPricing(Currency currency, BigDecimal rate) {

    /** A document typed in the base. */
    public static final DocumentPricing BASE = new DocumentPricing(null, null);

    public DocumentPricing {
        if (currency == null) {
            rate = null;
        }
    }

    public boolean foreign() {
        return currency != null;
    }

    /** Whether a foreign document has a rate to offer prices at. The base always has. */
    public boolean hasRate() {
        return !foreign() || (rate != null && rate.signum() > 0);
    }

    /** The currency the screen's figures are in, as the save is told it; {@code null} for the base. */
    public Integer currencyId() {
        return foreign() ? currency.id() : null;
    }

    /**
     * An amount kept in the base, offered in the document's currency: divided by the rate and rounded half
     * up to that currency's places. Zero on a foreign document with no rate - there is nothing to offer,
     * and a line priced at zero is one the screen refuses to save until somebody types a price.
     */
    public double fromBase(double base) {
        if (!foreign()) {
            return base;
        }
        if (!hasRate()) {
            return 0;
        }
        return MoneyMath.decimal(base).divide(rate, currency.decimalPlaces(), RoundingMode.HALF_UP).doubleValue();
    }

    /** An amount typed in the document's currency, in the base: times the rate, rounded to money. */
    public double toBase(double amount) {
        if (!foreign()) {
            return amount;
        }
        if (!hasRate()) {
            return 0;
        }
        return ForeignDocumentFigures.toBase(MoneyMath.decimal(amount), rate).doubleValue();
    }

    /**
     * A figure typed under {@code from}, restated under this pricing - what a line's price becomes when the
     * party is changed to one in another currency (§15 ق-د٧): back to the base at the old rate, and out
     * again at the new one. Unchanged when both are in one currency.
     */
    public double restate(double amount, DocumentPricing from) {
        DocumentPricing source = from == null ? BASE : from;
        if (java.util.Objects.equals(source.currencyId(), currencyId())) {
            return amount;
        }
        return fromBase(source.toBase(amount));
    }
}
