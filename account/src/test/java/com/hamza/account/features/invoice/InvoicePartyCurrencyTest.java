package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.currency.DocumentTranslation;
import com.hamza.account.features.party.currency.PartyCurrencyFixtures;
import com.hamza.account.features.returns.ReturnableRepository;
import com.hamza.account.type.InvoiceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.DAY;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.EGP;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.JPY;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.KWD;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.USD;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A document of a party in a foreign currency (docs/currency-plan.md §14 ق-ج٣ and §15): how it stands -
 * in the base, translated, or typed in the party's currency - which rate, when it is refused, and what is
 * written beside the base figures.
 */
class InvoicePartyCurrencyTest {

    private static final int DOLLAR_CUSTOMER = 7;
    private static final int POUND_CUSTOMER = 8;
    private static final int DINAR_CUSTOMER = 9;

    private final PartyCurrencyFixtures.Memory memory = new PartyCurrencyFixtures.Memory()
            .party(PartyKind.CUSTOMER, DOLLAR_CUSTOMER, USD)
            .party(PartyKind.CUSTOMER, POUND_CUSTOMER, null)
            .party(PartyKind.CUSTOMER, DINAR_CUSTOMER, KWD)
            .rate(USD, DAY.minusDays(5), "48")
            .rate(USD, DAY, "48.5")
            .rate(KWD, DAY, "160");
    private final ReturnableRepository returns = mock(ReturnableRepository.class);
    private final InvoicePartyCurrency currency = new InvoicePartyCurrency(memory, returns);

    @Test
    @DisplayName("a customer in the base: nothing written beside the figures, and nothing asked about a rate")
    void aCustomerInTheBase() throws Exception {
        InvoicePartyCurrency.Rate rate = currency.rateFor(DocumentType.SALES, POUND_CUSTOMER, DAY, 0, 0, null);
        assertEquals(InvoicePartyCurrency.Mode.BASE, rate.mode());
        assertFalse(rate.isForeign());
        assertFalse(rate.clearStored());
        currency.write(DocumentType.SALES, 100, rate, typed("100", "0", "100"));
        assertTrue(memory.written.isEmpty());
        assertTrue(memory.calls.stream().noneMatch(call -> call.startsWith("rateOn")));
    }

    @Test
    @DisplayName("a new invoice for a dollar customer is typed in dollars, at the rate in force on its day")
    void aNewInvoiceIsTypedInTheCustomersCurrency() throws Exception {
        InvoicePartyCurrency.Rate rate = currency.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY, 0, 0, USD.id());
        assertEquals(InvoicePartyCurrency.Mode.WRITTEN, rate.mode());
        assertTrue(rate.written());
        assertEquals(bd("48.5"), rate.rate());
        assertEquals(bd("48"),
                currency.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY.minusDays(1), 0, 0, USD.id()).rate(),
                "the latest dated on the day or before it");
    }

    @Test
    @DisplayName("a screen whose figures are in another currency than the document's is refused, not converted")
    void aScreenInTheWrongCurrencyIsRefused() {
        InvoiceValidationException inTheBase = assertThrows(InvoiceValidationException.class,
                () -> currency.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY, 0, 0, null));
        assertEquals(InvoiceSaveValidator.Target.ACCOUNT, inTheBase.target());
        assertThrows(InvoiceValidationException.class,
                () -> currency.rateFor(DocumentType.SALES, POUND_CUSTOMER, DAY, 0, 0, USD.id()),
                "a customer in the base, a screen still in dollars");
    }

    @Test
    @DisplayName("a currency with other than two places keeps the translation of V82 (ق-د٨)")
    void aCurrencyWithThreePlacesIsTranslated() throws Exception {
        assertTrue(InvoicePartyCurrency.writesIn(USD));
        assertFalse(InvoicePartyCurrency.writesIn(KWD), "a third place would be rounded away");
        assertFalse(InvoicePartyCurrency.writesIn(JPY));
        assertFalse(InvoicePartyCurrency.writesIn(EGP), "the base is never a document's foreign currency");
        assertFalse(InvoicePartyCurrency.writesIn(null));

        InvoicePartyCurrency.Rate rate = currency.rateFor(DocumentType.SALES, DINAR_CUSTOMER, DAY, 0, 0, null);
        assertEquals(InvoicePartyCurrency.Mode.TRANSLATED, rate.mode());
        assertEquals(bd("160"), rate.rate());
    }

    @Test
    @DisplayName("no rate on the invoice's day is a refusal - asked before the number is allocated")
    void noRateIsARefusal() {
        InvoiceValidationException refused = assertThrows(InvoiceValidationException.class,
                () -> currency.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY.minusDays(30), 0, 0, USD.id()));
        assertEquals(InvoiceSaveValidator.Target.DATE, refused.target());
    }

    @Test
    @DisplayName("an edit keeps its rate while its day and its party are unchanged")
    void anEditKeepsItsRate() throws Exception {
        memory.document(DocumentType.SALES, 100, DOLLAR_CUSTOMER, DAY, "47", USD, "100", "0", "0");
        InvoicePartyCurrency.Rate kept = currency.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY, 100, 0, USD.id());
        assertEquals(bd("47"), kept.rate(),
                "a note fixed on an old invoice must not revalue it at a rate corrected since");
        assertTrue(kept.written());
        assertEquals(bd("48"),
                currency.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY.minusDays(2), 100, 0, USD.id()).rate(),
                "a new day is a new rate");
    }

    @Test
    @DisplayName("a document translated in V82 stays translated when it is edited: it keeps the currency it was written in")
    void aTranslatedDocumentStaysTranslated() throws Exception {
        memory.document(DocumentType.SALES, 100, DOLLAR_CUSTOMER, DAY, "47", "4700", "0", "0");
        InvoicePartyCurrency.Rate rate = currency.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY, 100, 0, null);
        assertEquals(InvoicePartyCurrency.Mode.TRANSLATED, rate.mode());
        assertEquals(bd("47"), rate.rate());
        assertThrows(InvoiceValidationException.class,
                () -> currency.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY, 100, 0, USD.id()),
                "reopened in the base, it is saved in the base");
    }

    @Test
    @DisplayName("an invoice moved from a dollar customer to one in the base has its figures cleared")
    void movedToTheBase() throws Exception {
        memory.document(DocumentType.SALES, 100, DOLLAR_CUSTOMER, DAY, "47", USD, "100", "0", "0");
        InvoicePartyCurrency.Rate rate = currency.rateFor(DocumentType.SALES, POUND_CUSTOMER, DAY, 100, 0, null);
        assertTrue(rate.clearStored());
        currency.write(DocumentType.SALES, 100, rate, typed("4700", "0", "0"));
        assertTrue(memory.written.containsKey("SALES:100"));
        assertNull(memory.written.get("SALES:100"));
        assertNull(memory.writtenCurrency.get("SALES:100"));
    }

    @Test
    @DisplayName("a return naming its invoice takes that invoice's rate; a free return its own day's")
    void aReturnTakesItsSourcesRate() throws Exception {
        memory.document(DocumentType.SALES, 55, DOLLAR_CUSTOMER, DAY.minusDays(5), "48", USD, "100", "0", "0");
        assertEquals(bd("48"),
                currency.rateFor(DocumentType.SALES_RETURN, DOLLAR_CUSTOMER, DAY, 0, 55, USD.id()).rate());
        assertEquals(bd("48.5"),
                currency.rateFor(DocumentType.SALES_RETURN, DOLLAR_CUSTOMER, DAY, 0, 0, USD.id()).rate());
    }

    @Test
    @DisplayName("a document typed in dollars writes exactly what was typed, and the currency it was typed in")
    void aWrittenDocumentWritesWhatWasTyped() throws Exception {
        InvoicePartyCurrency.Rate rate = currency.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY, 0, 0, USD.id());
        currency.write(DocumentType.SALES, 100, rate, typed("20.61", "0.61", "20.00"));

        DocumentTranslation written = memory.written.get("SALES:100");
        assertEquals(bd("48.5"), written.rate());
        assertEquals(bd("20.61"), written.total());
        assertEquals(bd("0.61"), written.discount());
        assertEquals(bd("20.00"), written.paid());
        assertEquals(USD.id(), memory.writtenCurrency.get("SALES:100"));
    }

    @Test
    @DisplayName("a translated document writes the stored header translated, the cash leaving nothing behind")
    void aTranslatedDocumentWritesItsTranslation() throws Exception {
        memory.document(DocumentType.SALES, 100, DINAR_CUSTOMER, DAY, null, "1000.00", "10.00", "990.00");
        InvoicePartyCurrency.Rate rate = currency.rateFor(DocumentType.SALES, DINAR_CUSTOMER, DAY, 0, 0, null);
        currency.write(DocumentType.SALES, 100, rate, null);

        DocumentTranslation written = memory.written.get("SALES:100");
        assertEquals(bd("160"), written.rate());
        assertEquals(bd("6.250"), written.total());
        assertEquals(0, written.remainder().signum());
        assertNull(memory.writtenCurrency.get("SALES:100"), "translated: written in the base");
    }

    @Test
    @DisplayName("a return's source lines and its share of the source's discount come from the source's base figures")
    void aReturnReadsItsSourceInTheBase() throws Exception {
        var line = new ReturnableRepository.SourceLine(31, 10, 97.00, 4.85, 60, 1, 1, null);
        when(returns.lineById(DocumentType.SALES, 55, 900)).thenReturn(Optional.of(line));
        when(returns.sourceAmounts(DocumentType.SALES, 55))
                .thenReturn(Optional.of(new ReturnableRepository.SourceAmounts(1000, 100)));

        assertEquals(line, currency.sourceLines(DocumentType.SALES_RETURN, 55).lineById(900).orElseThrow());
        assertEquals(0, bd("25.00").compareTo(
                currency.returnShare(DocumentType.SALES_RETURN, 55, bd("250.00"))),
                "a quarter of the invoice back is a quarter of its discount");
        assertNull(currency.sourceLines(DocumentType.SALES, 55), "an invoice names no source");
        assertNull(currency.sourceLines(DocumentType.SALES_RETURN, 0), "a free return names none");
        assertNull(currency.returnShare(DocumentType.SALES_RETURN, 0, BigDecimal.TEN));
    }

    @Test
    @DisplayName("none() writes nothing")
    void noneWritesNothing() throws Exception {
        InvoicePartyCurrency none = InvoicePartyCurrency.none();
        InvoicePartyCurrency.Rate rate = none.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY, 0, 0, USD.id());
        assertFalse(rate.isForeign());
        none.write(DocumentType.SALES, 1, rate, null);
        assertNull(none.sourceLines(DocumentType.SALES_RETURN, 55));
    }

    private static InvoicePaymentTerms typed(String subtotal, String discount, String paid) {
        BigDecimal net = bd(subtotal).subtract(bd(discount));
        return new InvoicePaymentTerms(InvoiceType.DEFER, bd(subtotal), bd(discount), net, bd(paid),
                net.subtract(bd(paid)));
    }
}
