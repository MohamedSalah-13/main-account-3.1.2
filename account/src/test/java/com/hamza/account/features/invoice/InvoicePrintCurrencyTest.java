package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.party.currency.PartyCurrencies;
import com.hamza.account.features.party.currency.PartyCurrencyFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a saved document stands in its party's currency, for its paper (docs/currency-plan.md §15 ق-د٩). */
class InvoicePrintCurrencyTest {

    private static final BigDecimal RATE = new BigDecimal("48.37");

    private final PartyCurrencyFixtures.Memory currencies = new PartyCurrencyFixtures.Memory();
    private final InvoicePrintCurrency.Catalogue catalogue = new InvoicePrintCurrency.Catalogue() {
        @Override
        public Currency find(int currencyId) {
            return currencies.currencies.get(currencyId);
        }

        @Override
        public Currency base() {
            return PartyCurrencyFixtures.EGP;
        }
    };

    @Test
    @DisplayName("a document typed in dollars is read in dollars, with what was typed on its header")
    void aWrittenDocument() throws Exception {
        currencies.foreignHeaders.put("SALES:7", new PartyCurrencies.ForeignHeader(3, RATE,
                new BigDecimal("20.67"), new BigDecimal("2.07"), new BigDecimal("5.00")));

        InvoicePrintCurrency.Figures figures = InvoicePrintCurrency.read(currencies, catalogue,
                DocumentType.SALES, 7, 5);

        assertEquals(PartyCurrencyFixtures.USD, figures.currency());
        assertEquals(PartyCurrencyFixtures.EGP, figures.base());
        assertTrue(figures.written());
        assertEquals(RATE, figures.rate());
        assertEquals(new BigDecimal("20.67"), figures.total());
    }

    @Test
    @DisplayName("a document written in the base and translated is in its party's currency")
    void aTranslatedDocument() throws Exception {
        currencies.party(com.hamza.account.features.events.PartyKind.CUSTOMER, 5, PartyCurrencyFixtures.KWD);
        currencies.foreignHeaders.put("SALES:7", new PartyCurrencies.ForeignHeader(null, new BigDecimal("158.2"),
                new BigDecimal("6.321"), new BigDecimal("0.632"), null));

        InvoicePrintCurrency.Figures figures = InvoicePrintCurrency.read(currencies, catalogue,
                DocumentType.SALES, 7, 5);

        assertEquals(PartyCurrencyFixtures.KWD, figures.currency());
        assertFalse(figures.written());
        assertEquals(0, figures.paid().signum(), "a figure missing from the header is none");
    }

    @Test
    @DisplayName("a document in the base, or with no rate, has nothing to say")
    void nothing() throws Exception {
        assertNull(InvoicePrintCurrency.read(currencies, catalogue, DocumentType.SALES, 7, 5));
        currencies.foreignHeaders.put("SALES:8", new PartyCurrencies.ForeignHeader(3, null,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO));
        assertNull(InvoicePrintCurrency.read(currencies, catalogue, DocumentType.SALES, 8, 5));
        assertNull(InvoicePrintCurrency.read(currencies, catalogue, DocumentType.SALES, 0, 5));
        assertNull(InvoicePrintCurrency.read(null, catalogue, DocumentType.SALES, 7, 5));
    }
}
