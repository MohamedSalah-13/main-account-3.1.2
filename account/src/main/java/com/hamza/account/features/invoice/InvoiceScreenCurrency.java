package com.hamza.account.features.invoice;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyService;
import com.hamza.account.features.currency.RateInForce;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * What an invoice screen's figures are typed in (V83, docs/currency-plan.md §15), decided without a
 * control: for a party chosen on a day, for a document reopened, and what becomes of the lines already on
 * the screen when that changes.
 * <ul>
 *   <li>A party in the base, or one whose currency is kept translated (ق-د٨), is priced in the base.</li>
 *   <li>A party in a currency a document can be typed in is priced in it, at the rate in force on the
 *       document's day - none is a screen that says so, and a save that refuses it.</li>
 *   <li>A document reopened is priced in the currency it was written in, at its own rate, and its lines
 *       show what was typed on them - never the base converted back.</li>
 * </ul>
 */
public final class InvoiceScreenCurrency {

    /** The two things the screen asks of the currency catalogue. */
    public interface Catalogue {
        Currency find(int currencyId) throws DaoException;

        /** The rate in force on {@code day}, or {@code null} when there is none. */
        BigDecimal rateOn(int currencyId, LocalDate day) throws DaoException;

        /** No catalogue at all - every party is priced in the base. */
        Catalogue NONE = new Catalogue() {
            @Override
            public Currency find(int currencyId) {
                return null;
            }

            @Override
            public BigDecimal rateOn(int currencyId, LocalDate day) {
                return null;
            }
        };

        static Catalogue of(CurrencyService service) {
            Objects.requireNonNull(service, "service");
            return new Catalogue() {
                @Override
                public Currency find(int currencyId) throws DaoException {
                    return service.find(currencyId);
                }

                @Override
                public BigDecimal rateOn(int currencyId, LocalDate day) throws DaoException {
                    return service.rateOn(currencyId, day).map(RateInForce::rate).orElse(null);
                }
            };
        }
    }

    private final Catalogue catalogue;

    public InvoiceScreenCurrency(Catalogue catalogue) {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
    }

    /**
     * The pricing a new document for a party works in.
     *
     * @param partyCurrencyId the party's currency, or {@code null} for the base
     */
    public DocumentPricing forParty(Integer partyCurrencyId, LocalDate day) throws DaoException {
        Currency currency = partyCurrencyId == null ? null : catalogue.find(partyCurrencyId);
        if (!InvoicePartyCurrency.writesIn(currency)) {
            return DocumentPricing.BASE;
        }
        return new DocumentPricing(currency, catalogue.rateOn(currency.id(), day));
    }

    /**
     * The party's currency when its documents are written in the base and translated rather than typed in
     * it (ق-د٨) - what the screen says in a sentence; {@code null} otherwise.
     */
    public Currency translatedCurrency(Integer partyCurrencyId) throws DaoException {
        Currency currency = partyCurrencyId == null ? null : catalogue.find(partyCurrencyId);
        return currency == null || currency.base() || InvoicePartyCurrency.writesIn(currency) ? null : currency;
    }

    /**
     * A saved document reopened: in the currency it was written in, at the rate it was stored at.
     *
     * @param writtenCurrencyId the header's {@code currency_id}, or {@code null} for one written in the base
     */
    public DocumentPricing forStored(Integer writtenCurrencyId, BigDecimal storedRate) throws DaoException {
        if (writtenCurrencyId == null) {
            return DocumentPricing.BASE;
        }
        Currency currency = catalogue.find(writtenCurrencyId);
        return currency == null ? DocumentPricing.BASE : new DocumentPricing(currency, storedRate);
    }

    /**
     * The lines of a document reopened for editing, showing what was typed on them: its price and discount
     * in the document's currency, and a total worked out from those. A line written in the base has none
     * and is left as it is.
     */
    public static void showTyped(List<? extends BasePurchasesAndSales> lines) {
        for (BasePurchasesAndSales line : lines) {
            if (line == null || line.getPriceForeign() == null) {
                continue;
            }
            line.setPrice(line.getPriceForeign().doubleValue());
            line.setDiscount(line.getDiscountForeign() == null ? 0 : line.getDiscountForeign().doubleValue());
            InvoiceLineService.recalculate(line);
        }
    }

    /**
     * The lines already on the screen, restated when the party is changed to one in another currency
     * (§15 ق-د٧): each price and discount back to the base at the old rate and out again at the new one.
     * The screen's trailing entry row and a line picked from a return's invoice are left alone - the
     * first is not a line, and the second's figures are its invoice's, not the screen's to reprice.
     */
    public static void restate(List<? extends BasePurchasesAndSales> lines, DocumentPricing from,
                               DocumentPricing to) {
        restate(lines, from, to, line -> line.getSourceLineId() > 0);
    }

    static void restate(List<? extends BasePurchasesAndSales> lines, DocumentPricing from, DocumentPricing to,
                        Predicate<BasePurchasesAndSales> leaveAlone) {
        Objects.requireNonNull(to, "to");
        for (BasePurchasesAndSales line : lines) {
            if (InvoiceLineTotals.isPlaceholder(line) || leaveAlone.test(line)) {
                continue;
            }
            line.setPrice(to.restate(line.getPrice(), from));
            line.setDiscount(to.restate(line.getDiscount(), from));
            InvoiceLineService.recalculate(line);
        }
    }
}
