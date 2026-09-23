package com.hamza.account.features.invoice;

import com.hamza.account.features.party.currency.PartyCurrencyFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an invoice screen offers a price at and holds a sale to (docs/currency-plan.md §15 ق-د٣ and ق-د٧).
 */
class DocumentPricingTest {

    private static final DocumentPricing DOLLARS =
            new DocumentPricing(PartyCurrencyFixtures.USD, new BigDecimal("48.37"));
    private static final DocumentPricing RIYALS =
            new DocumentPricing(PartyCurrencyFixtures.SAR, new BigDecimal("12.90"));

    @Test
    @DisplayName("the base answers every figure unchanged")
    void theBaseIsUnchanged() {
        assertFalse(DocumentPricing.BASE.foreign());
        assertTrue(DocumentPricing.BASE.hasRate());
        assertNull(DocumentPricing.BASE.currencyId());
        assertEquals(100.13, DocumentPricing.BASE.fromBase(100.13), 0.0);
        assertEquals(100.13, DocumentPricing.BASE.toBase(100.13), 0.0);
    }

    @Test
    @DisplayName("a base price is offered in the document's currency, rounded half up to its places")
    void aPriceIsOffered() {
        assertEquals(2.07, DOLLARS.fromBase(100.00), 0.0, "100 / 48.37 = 2.0674");
        assertEquals(3, DOLLARS.currencyId());
    }

    @Test
    @DisplayName("a typed price is held to the cost by what will be stored: times the rate, rounded to money")
    void aTypedPriceInTheBase() {
        assertEquals(100.13, DOLLARS.toBase(2.07), 0.0, "2.07 x 48.37 = 100.1259");
    }

    @Test
    @DisplayName("with no rate nothing is offered and nothing can be held to a cost")
    void noRate() {
        DocumentPricing unrated = new DocumentPricing(PartyCurrencyFixtures.USD, null);
        assertTrue(unrated.foreign());
        assertFalse(unrated.hasRate());
        assertEquals(0, unrated.fromBase(100), 0.0);
        assertEquals(0, unrated.toBase(2.07), 0.0);
        assertFalse(new DocumentPricing(PartyCurrencyFixtures.USD, BigDecimal.ZERO).hasRate());
    }

    @Test
    @DisplayName("a base document carries no rate even if handed one")
    void aBaseDocumentHasNoRate() {
        assertNull(new DocumentPricing(null, new BigDecimal("48.37")).rate());
    }

    @Test
    @DisplayName("a figure restated into another currency goes through the base once")
    void restating() {
        assertEquals(2.07, DOLLARS.restate(2.07, DOLLARS), 0.0, "one currency - unchanged");
        assertEquals(2.07, DOLLARS.restate(100.00, DocumentPricing.BASE), 0.0);
        assertEquals(100.13, DocumentPricing.BASE.restate(2.07, DOLLARS), 0.0);
        assertEquals(2.07, DOLLARS.restate(100.00, null), 0.0, "no pricing is the base");
        // 7.76 riyals x 12.90 = 100.10 in the base, / 48.37 = 2.0694 dollars.
        assertEquals(2.07, DOLLARS.restate(7.76, RIYALS), 0.0);
    }
}
