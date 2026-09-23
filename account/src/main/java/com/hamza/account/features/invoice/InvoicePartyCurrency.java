package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.party.currency.DocumentTranslation;
import com.hamza.account.features.party.currency.PartyCurrencies;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A document for a party in a foreign currency, translated into that currency beside its base figures
 * (V82, docs/currency-plan.md §14 ق-ج٣).
 * <p>
 * The document is written in the base as it always was; this writes the header's total, discount and
 * cash again in the party's currency, with the rate copied. Two halves, because the save has two
 * moments: the rate is chosen <b>before the number is allocated</b> - the counter does not roll back,
 * so a refusal for want of a rate must come before it - and the figures are written <b>after the header
 * is</b>, from what was stored, in the save's own transaction.
 * <p>
 * Which rate:
 * <ul>
 *   <li>an edit keeps the rate it was saved with while its party and its day are unchanged - fixing a
 *       note on an old invoice must not translate it again at a rate corrected since (ق-٤);</li>
 *   <li>a return that names its invoice takes that invoice's rate - it gives back exactly what the
 *       invoice took, in the party's currency as in the base;</li>
 *   <li>anything else takes the rate in force on the document's day, and there being none is a
 *       refusal, never a zero or a one (ق-٣).</li>
 * </ul>
 * A party in the base has nothing written, except that an edit of a document that was translated -
 * moved to a party in the base - has its old translation cleared.
 */
public final class InvoicePartyCurrency {

    /**
     * The translation a save will write: the party's currency and the rate, or neither for a party in
     * the base - in which case {@code clearStored} says whether an old translation has to go.
     */
    public record Rate(Currency currency, BigDecimal rate, boolean clearStored) {

        static final Rate NONE = new Rate(null, null, false);
        static final Rate CLEAR = new Rate(null, null, true);

        public boolean isForeign() {
            return rate != null;
        }
    }

    private final PartyCurrencies currencies;

    InvoicePartyCurrency(PartyCurrencies currencies) {
        this.currencies = currencies;
    }

    public static InvoicePartyCurrency jdbc() {
        return new InvoicePartyCurrency(PartyCurrencies.jdbc());
    }

    /** Translates nothing - for the tests of the save that are not about a currency. */
    public static InvoicePartyCurrency none() {
        return new InvoicePartyCurrency(null);
    }

    /**
     * The rate this document is translated at.
     *
     * @param existingNumber the document being edited, or {@code 0} for a new one
     * @param sourceNumber   the invoice a return names, or {@code 0}
     */
    public Rate rateFor(DocumentType type, int partyId, LocalDate date, int existingNumber, int sourceNumber)
            throws DaoException {
        if (currencies == null) {
            return Rate.NONE;
        }
        Currency currency = currencies.ofParty(type.partyKind(), partyId);
        PartyCurrencies.StoredDocument stored = existingNumber > 0
                ? currencies.storedDocument(type, existingNumber) : null;
        if (currency == null) {
            return stored != null && stored.rate() != null ? Rate.CLEAR : Rate.NONE;
        }
        if (stored != null && stored.rate() != null && stored.partyId() == partyId
                && Objects.equals(stored.date(), date)) {
            return new Rate(currency, stored.rate(), false);
        }
        if (type.isReturn() && sourceNumber > 0) {
            PartyCurrencies.StoredDocument source = currencies.storedDocument(type.reverses(), sourceNumber);
            if (source != null && source.rate() != null && source.partyId() == partyId) {
                return new Rate(currency, source.rate(), false);
            }
        }
        BigDecimal rate = currencies.rateOn(currency.id(), date);
        if (rate == null || rate.signum() <= 0) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.DATE,
                    LanguageManager.getInstance().getString("party.currency.error.document.rate",
                            currency.name(), String.valueOf(date)));
        }
        return new Rate(currency, rate, false);
    }

    /** Writes the translation of what was just stored under {@code number}, or clears an old one. */
    public void write(DocumentType type, int number, Rate rate) throws DaoException {
        if (currencies == null || rate == null) {
            return;
        }
        if (!rate.isForeign()) {
            if (rate.clearStored()) {
                currencies.writeDocument(type, number, null);
            }
            return;
        }
        PartyCurrencies.DocumentAmounts amounts = currencies.documentAmounts(type, number);
        currencies.writeDocument(type, number, DocumentTranslation.of(amounts.total(), amounts.discount(),
                amounts.paid(), rate.rate(), rate.currency()));
    }
}
