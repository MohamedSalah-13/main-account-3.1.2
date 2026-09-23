package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.party.currency.DocumentTranslation;
import com.hamza.account.features.party.currency.PartyCurrencies;
import com.hamza.account.features.returns.JdbcReturnableRepository;
import com.hamza.account.features.returns.ReturnHeaderDiscount;
import com.hamza.account.features.returns.ReturnableRepository;
import com.hamza.account.finance.MoneyMath;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A document for a party in a foreign currency (V82 and V83, docs/currency-plan.md §14 and §15).
 * <p>
 * There are three ways a document can stand, and the header's {@code currency_id} and
 * {@code exchange_rate} say which:
 * <ul>
 *   <li>{@link Mode#BASE} - a party in the base; nothing is written beside the base figures.</li>
 *   <li>{@link Mode#TRANSLATED} - written in the base and translated into the party's currency at the
 *       day's rate (ق-ج٣). What V82 did for every foreign party, and what is left of it: a document saved
 *       that way keeps being saved that way, and a party whose currency has other than two places is
 *       still served that way (ق-د٨).</li>
 *   <li>{@link Mode#WRITTEN} - typed in the party's currency (ق-د١): its lines, its discount and its cash
 *       are exact in that currency, and the base is derived from them at the rate.</li>
 * </ul>
 * The screen says which currency its figures are in, and a save whose screen disagrees with the
 * document - a party whose currency changed while the screen was open - is refused rather than
 * converted: nothing here guesses what somebody meant a figure to be.
 * <p>
 * Two moments, because the save has two: the rate is chosen <b>before the number is allocated</b> - the
 * counter does not roll back, so a refusal for want of a rate must come before it - and the figures are
 * written <b>after the header is</b>, in the save's own transaction.
 * <p>
 * Which rate:
 * <ul>
 *   <li>an edit keeps the rate it was saved with while its party and its day are unchanged - fixing a
 *       note on an old invoice must not revalue it at a rate corrected since (ق-٤);</li>
 *   <li>a return that names its invoice takes that invoice's rate - it gives back exactly what the
 *       invoice took, in the party's currency as in the base;</li>
 *   <li>anything else takes the rate in force on the document's day, and there being none is a
 *       refusal, never a zero or a one (ق-٣).</li>
 * </ul>
 */
public final class InvoicePartyCurrency {

    /** How a document stands against its party's currency - see the class comment. */
    public enum Mode {
        BASE,
        TRANSLATED,
        WRITTEN
    }

    /**
     * What a save will do: the mode, the party's currency and the rate - or neither for a party in the
     * base, in which case {@code clearStored} says whether an old translation has to go.
     */
    public record Rate(Mode mode, Currency currency, BigDecimal rate, boolean clearStored) {

        static final Rate NONE = new Rate(Mode.BASE, null, null, false);
        static final Rate CLEAR = new Rate(Mode.BASE, null, null, true);

        public boolean isForeign() {
            return rate != null;
        }

        /** Whether the document's figures were typed in the party's currency (V83). */
        public boolean written() {
            return mode == Mode.WRITTEN;
        }
    }

    private final PartyCurrencies currencies;
    private final ReturnableRepository returns;

    InvoicePartyCurrency(PartyCurrencies currencies) {
        this(currencies, null);
    }

    InvoicePartyCurrency(PartyCurrencies currencies, ReturnableRepository returns) {
        this.currencies = currencies;
        this.returns = returns;
    }

    public static InvoicePartyCurrency jdbc() {
        return new InvoicePartyCurrency(PartyCurrencies.jdbc(), new JdbcReturnableRepository());
    }

    /** Translates nothing - for the tests of the save that are not about a currency. */
    public static InvoicePartyCurrency none() {
        return new InvoicePartyCurrency(null);
    }

    /**
     * Whether a new document for a party in {@code currency} is typed in it (ق-د١) or written in the base
     * and translated. A document is worked out to two places, as every document is, so a currency with
     * any other number of places keeps the translation rather than having a place rounded away (ق-د٨).
     */
    public static boolean writesIn(Currency currency) {
        return currency != null && !currency.base() && currency.decimalPlaces() == 2;
    }

    /** Compatibility: a screen whose figures are in the base. */
    public Rate rateFor(DocumentType type, int partyId, LocalDate date, int existingNumber, int sourceNumber)
            throws DaoException {
        return rateFor(type, partyId, date, existingNumber, sourceNumber, null);
    }

    /**
     * How this document stands and the rate it is valued at.
     *
     * @param existingNumber  the document being edited, or {@code 0} for a new one
     * @param sourceNumber    the invoice a return names, or {@code 0}
     * @param typedCurrencyId the currency the screen says its figures are in, or {@code null} for the base
     */
    public Rate rateFor(DocumentType type, int partyId, LocalDate date, int existingNumber, int sourceNumber,
                        Integer typedCurrencyId) throws DaoException {
        if (currencies == null) {
            return Rate.NONE;
        }
        Currency currency = currencies.ofParty(type.partyKind(), partyId);
        PartyCurrencies.StoredDocument stored = existingNumber > 0
                ? currencies.storedDocument(type, existingNumber) : null;
        if (currency == null) {
            requireTypedIn(typedCurrencyId, null);
            return stored != null && stored.rate() != null ? Rate.CLEAR : Rate.NONE;
        }
        boolean sameParty = stored != null && stored.partyId() == partyId && stored.rate() != null;
        Mode mode = sameParty
                ? (stored.currencyId() != null ? Mode.WRITTEN : Mode.TRANSLATED)
                : (writesIn(currency) ? Mode.WRITTEN : Mode.TRANSLATED);
        requireTypedIn(typedCurrencyId, mode == Mode.WRITTEN ? currency : null);

        if (sameParty && Objects.equals(stored.date(), date)) {
            return new Rate(mode, currency, stored.rate(), false);
        }
        if (type.isReturn() && sourceNumber > 0) {
            PartyCurrencies.StoredDocument source = currencies.storedDocument(type.reverses(), sourceNumber);
            if (source != null && source.rate() != null && source.partyId() == partyId) {
                return new Rate(mode, currency, source.rate(), false);
            }
        }
        BigDecimal rate = currencies.rateOn(currency.id(), date);
        if (rate == null || rate.signum() <= 0) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.DATE,
                    LanguageManager.getInstance().getString("party.currency.error.document.rate",
                            currency.name(), String.valueOf(date)));
        }
        return new Rate(mode, currency, rate, false);
    }

    /**
     * The base figures of the lines of the invoice a return names, for {@link ForeignDocumentLines}:
     * a picked line is stored at its source line's own base price and discount share (ق-د٥). {@code null}
     * for anything but a return naming one.
     */
    public ForeignDocumentLines.SourceLines sourceLines(DocumentType type, int sourceNumber) {
        if (returns == null || !type.isReturn() || sourceNumber <= 0) {
            return null;
        }
        return lineId -> returns.lineById(type.reverses(), sourceNumber, lineId);
    }

    /**
     * For a return naming its invoice, the base share of that invoice's own discount its lines take -
     * the figure {@code ReturnGuard.validateDiscount} holds it to (ق-د٥). {@code null} for anything else.
     */
    public BigDecimal returnShare(DocumentType type, int sourceNumber, BigDecimal baseSubtotal)
            throws DaoException {
        if (returns == null || !type.isReturn() || sourceNumber <= 0) {
            return null;
        }
        ReturnableRepository.SourceAmounts source = returns.sourceAmounts(type.reverses(), sourceNumber)
                .orElse(null);
        if (source == null) {
            return null;
        }
        return MoneyMath.decimal(ReturnHeaderDiscount.shareFor(source.total(), source.discount(),
                MoneyMath.asDouble(baseSubtotal)));
    }

    /**
     * Writes what was just stored under {@code number} in the party's currency, or clears an old
     * translation.
     *
     * @param typed the header as the screen typed it - read only for a document written in the party's
     *              currency, whose figures in that currency are these exactly
     */
    public void write(DocumentType type, int number, Rate rate, InvoicePaymentTerms typed) throws DaoException {
        if (currencies == null || rate == null) {
            return;
        }
        if (!rate.isForeign()) {
            if (rate.clearStored()) {
                currencies.writeDocument(type, number, null, null);
            }
            return;
        }
        if (rate.written()) {
            Objects.requireNonNull(typed, "typed");
            currencies.writeDocument(type, number, rate.currency().id(), new DocumentTranslation(rate.rate(),
                    typed.subtotalAmount(), typed.discountAmount(), typed.paidAmount()));
            return;
        }
        PartyCurrencies.DocumentAmounts amounts = currencies.documentAmounts(type, number);
        currencies.writeDocument(type, number, null, DocumentTranslation.of(amounts.total(), amounts.discount(),
                amounts.paid(), rate.rate(), rate.currency()));
    }

    /** Compatibility: a document translated from the base, as V82 wrote every one. */
    public void write(DocumentType type, int number, Rate rate) throws DaoException {
        write(type, number, rate, null);
    }

    /**
     * The screen's figures have to be in the currency the document is in: a party whose currency moved
     * while the screen was open, or a document reopened in the wrong currency, is refused and reopened,
     * never converted on the way past.
     */
    private static void requireTypedIn(Integer typedCurrencyId, Currency documentCurrency)
            throws InvoiceValidationException {
        Integer expected = documentCurrency == null ? null : documentCurrency.id();
        if (!Objects.equals(typedCurrencyId, expected)) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.ACCOUNT,
                    LanguageManager.getInstance().getString("invoice.currency.error.mismatch"));
        }
    }
}
