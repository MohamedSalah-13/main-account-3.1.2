package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.party.currency.PartyCurrencies;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * How a saved document stands in its party's currency, for its paper (V83, docs/currency-plan.md §15 ق-د٩).
 * <p>
 * Read off the header's own columns: a document typed in the party's currency names it in
 * {@code currency_id}, and its typed total, discount and cash are its {@code *_foreign} figures; a document
 * written in the base and translated (V82) names no currency, and is in its party's. A document with no
 * rate on its header is in the base and has nothing to say here.
 */
public final class InvoicePrintCurrency {

    /** The two things the paper asks of the currency catalogue. */
    public interface Catalogue {
        Currency find(int currencyId) throws DaoException;

        Currency base() throws DaoException;
    }

    /**
     * The document's figures in its party's currency.
     *
     * @param written  whether it was typed in {@code currency}; otherwise these are its translation
     * @param total    its lines after their own discounts, in {@code currency}
     * @param discount its additional discount, in {@code currency}
     * @param paid     its cash, in {@code currency}
     */
    public record Figures(Currency currency, Currency base, BigDecimal rate, boolean written,
                          BigDecimal total, BigDecimal discount, BigDecimal paid) {

        public Figures {
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(base, "base");
            Objects.requireNonNull(rate, "rate");
            total = total == null ? BigDecimal.ZERO : total;
            discount = discount == null ? BigDecimal.ZERO : discount;
            paid = paid == null ? BigDecimal.ZERO : paid;
        }
    }

    private InvoicePrintCurrency() {
    }

    /**
     * @return the document's figures in its party's currency, or {@code null} for a document in the base
     */
    public static Figures read(PartyCurrencies currencies, Catalogue catalogue, DocumentType type, int number,
                               int partyId) throws DaoException {
        Objects.requireNonNull(type, "type");
        if (currencies == null || catalogue == null || number <= 0) {
            return null;
        }
        PartyCurrencies.ForeignHeader header = currencies.foreignHeader(type, number);
        if (header == null || header.rate() == null || header.rate().signum() <= 0) {
            return null;
        }
        Currency currency = header.written()
                ? catalogue.find(header.currencyId())
                : partyId > 0 ? currencies.ofParty(type.partyKind(), partyId) : null;
        Currency base = catalogue.base();
        if (currency == null || base == null || currency.base() || currency.id() == base.id()) {
            return null;
        }
        return new Figures(currency, base, header.rate(), header.written(), header.total(), header.discount(),
                header.paid());
    }
}
