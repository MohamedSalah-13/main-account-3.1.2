package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.currency.DocumentTranslation;
import com.hamza.account.features.party.currency.PartyCurrencyFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.DAY;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.USD;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A document of a party in a foreign currency, translated beside its base figures
 * (docs/currency-plan.md §14 ق-ج٣): which rate, when it is refused, and what is written.
 */
class InvoicePartyCurrencyTest {

    private static final int DOLLAR_CUSTOMER = 7;
    private static final int POUND_CUSTOMER = 8;

    private final PartyCurrencyFixtures.Memory memory = new PartyCurrencyFixtures.Memory()
            .party(PartyKind.CUSTOMER, DOLLAR_CUSTOMER, USD)
            .party(PartyKind.CUSTOMER, POUND_CUSTOMER, null)
            .rate(USD, DAY.minusDays(5), "48")
            .rate(USD, DAY, "48.5");
    private final InvoicePartyCurrency translator = new InvoicePartyCurrency(memory);

    @Test
    @DisplayName("a customer in the base: nothing to translate, and nothing asked about a rate")
    void aCustomerInTheBase() throws Exception {
        InvoicePartyCurrency.Rate rate = translator.rateFor(DocumentType.SALES, POUND_CUSTOMER, DAY, 0, 0);
        assertFalse(rate.isForeign());
        assertFalse(rate.clearStored());
        translator.write(DocumentType.SALES, 100, rate);
        assertTrue(memory.written.isEmpty());
        assertTrue(memory.calls.stream().noneMatch(call -> call.startsWith("rateOn")));
    }

    @Test
    @DisplayName("a new invoice for a dollar customer takes the rate in force on its day")
    void aNewInvoiceTakesTheDaysRate() throws Exception {
        assertEquals(bd("48.5"), translator.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY, 0, 0).rate());
        assertEquals(bd("48"),
                translator.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY.minusDays(1), 0, 0).rate(),
                "the latest dated on the day or before it");
    }

    @Test
    @DisplayName("no rate on the invoice's day is a refusal - asked before the number is allocated")
    void noRateIsARefusal() {
        InvoiceValidationException refused = assertThrows(InvoiceValidationException.class,
                () -> translator.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY.minusDays(30), 0, 0));
        assertEquals(InvoiceSaveValidator.Target.DATE, refused.target());
    }

    @Test
    @DisplayName("an edit keeps its rate while its day and its party are unchanged")
    void anEditKeepsItsRate() throws Exception {
        memory.document(DocumentType.SALES, 100, DOLLAR_CUSTOMER, DAY, "47", "4700", "0", "0");
        assertEquals(bd("47"), translator.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY, 100, 0).rate(),
                "a note fixed on an old invoice must not translate it again at a rate corrected since");
        assertEquals(bd("48"),
                translator.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY.minusDays(2), 100, 0).rate(),
                "a new day is a new rate");
    }

    @Test
    @DisplayName("an invoice moved from a dollar customer to one in the base has its translation cleared")
    void movedToTheBase() throws Exception {
        memory.document(DocumentType.SALES, 100, DOLLAR_CUSTOMER, DAY, "47", "4700", "0", "0");
        InvoicePartyCurrency.Rate rate = translator.rateFor(DocumentType.SALES, POUND_CUSTOMER, DAY, 100, 0);
        assertTrue(rate.clearStored());
        translator.write(DocumentType.SALES, 100, rate);
        assertTrue(memory.written.containsKey("SALES:100"));
        assertNull(memory.written.get("SALES:100"));
    }

    @Test
    @DisplayName("a return naming its invoice takes that invoice's rate; a free return its own day's")
    void aReturnTakesItsSourcesRate() throws Exception {
        memory.document(DocumentType.SALES, 55, DOLLAR_CUSTOMER, DAY.minusDays(5), "48", "4800", "0", "0");
        assertEquals(bd("48"),
                translator.rateFor(DocumentType.SALES_RETURN, DOLLAR_CUSTOMER, DAY, 0, 55).rate());
        assertEquals(bd("48.5"),
                translator.rateFor(DocumentType.SALES_RETURN, DOLLAR_CUSTOMER, DAY, 0, 0).rate());
    }

    @Test
    @DisplayName("what is written is the stored header translated, the cash leaving nothing behind")
    void whatIsWritten() throws Exception {
        memory.document(DocumentType.SALES, 100, DOLLAR_CUSTOMER, DAY, null, "1000.00", "10.00", "990.00");
        InvoicePartyCurrency.Rate rate = translator.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY, 0, 0);
        translator.write(DocumentType.SALES, 100, rate);

        DocumentTranslation written = memory.written.get("SALES:100");
        assertEquals(bd("48.5"), written.rate());
        assertEquals(bd("20.62"), written.total());
        assertEquals(0, written.remainder().signum());
    }

    @Test
    @DisplayName("none() translates nothing")
    void noneTranslatesNothing() throws Exception {
        InvoicePartyCurrency none = InvoicePartyCurrency.none();
        InvoicePartyCurrency.Rate rate = none.rateFor(DocumentType.SALES, DOLLAR_CUSTOMER, DAY, 0, 0);
        assertFalse(rate.isForeign());
        none.write(DocumentType.SALES, 1, rate);
    }
}
