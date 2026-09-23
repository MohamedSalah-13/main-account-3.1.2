package com.hamza.account.features.invoice;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.party.currency.PartyCurrencyFixtures;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.UnitsModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * What an invoice screen's figures are typed in, and what becomes of the lines on it
 * (docs/currency-plan.md §15 ق-د١, ق-د٧ and ق-د٨).
 */
class InvoiceScreenCurrencyTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 23);
    private static final BigDecimal DOLLAR_RATE = new BigDecimal("48.37");

    private final InvoiceScreenCurrency screen = new InvoiceScreenCurrency(new InvoiceScreenCurrency.Catalogue() {
        private final Map<Integer, Currency> currencies = Map.of(
                1, PartyCurrencyFixtures.EGP, 3, PartyCurrencyFixtures.USD, 4, PartyCurrencyFixtures.KWD,
                2, PartyCurrencyFixtures.SAR);

        @Override
        public Currency find(int currencyId) {
            return currencies.get(currencyId);
        }

        @Override
        public BigDecimal rateOn(int currencyId, LocalDate day) {
            return currencyId == 3 && !day.isBefore(DAY) ? DOLLAR_RATE : null;
        }
    });

    @Test
    @DisplayName("a party in the base is priced in the base")
    void aBaseParty() throws Exception {
        assertSame(DocumentPricing.BASE, screen.forParty(null, DAY));
        assertSame(DocumentPricing.BASE, screen.forParty(1, DAY), "a party naming the base itself");
        assertNull(screen.translatedCurrency(null));
        assertNull(screen.translatedCurrency(1));
    }

    @Test
    @DisplayName("a dollar party is priced in dollars at the rate in force on the document's day")
    void aDollarParty() throws Exception {
        DocumentPricing pricing = screen.forParty(3, DAY);
        assertEquals(PartyCurrencyFixtures.USD, pricing.currency());
        assertEquals(DOLLAR_RATE, pricing.rate());
        assertNull(screen.translatedCurrency(3));
    }

    @Test
    @DisplayName("with no rate for the day the screen is still in dollars, and says there is none")
    void aDollarPartyWithNoRate() throws Exception {
        DocumentPricing pricing = screen.forParty(3, DAY.minusDays(1));
        assertEquals(PartyCurrencyFixtures.USD, pricing.currency());
        assertEquals(false, pricing.hasRate());
    }

    @Test
    @DisplayName("a currency of three places is kept translated: the screen is in the base and names it")
    void aTranslatedParty() throws Exception {
        assertSame(DocumentPricing.BASE, screen.forParty(4, DAY));
        assertEquals(PartyCurrencyFixtures.KWD, screen.translatedCurrency(4));
    }

    @Test
    @DisplayName("no catalogue prices everybody in the base")
    void noCatalogue() throws Exception {
        InvoiceScreenCurrency none = new InvoiceScreenCurrency(InvoiceScreenCurrency.Catalogue.NONE);
        assertSame(DocumentPricing.BASE, none.forParty(3, DAY));
        assertNull(none.translatedCurrency(3));
    }

    @Test
    @DisplayName("a document reopened is in the currency it was written in, at its own rate")
    void aStoredDocument() throws Exception {
        DocumentPricing stored = screen.forStored(3, new BigDecimal("47.90"));
        assertEquals(PartyCurrencyFixtures.USD, stored.currency());
        assertEquals(new BigDecimal("47.90"), stored.rate(), "its own rate, not today's");
        assertSame(DocumentPricing.BASE, screen.forStored(null, new BigDecimal("47.90")),
                "translated or in the base - written in the base");
    }

    @Test
    @DisplayName("a reopened line shows what was typed on it, and a line written in the base is left alone")
    void showTyped() {
        Sales typed = line(100.13, 3, 4.84);
        typed.setPriceForeign(new BigDecimal("2.07"));
        typed.setDiscountForeign(new BigDecimal("0.10"));
        Sales base = line(50, 2, 0);

        InvoiceScreenCurrency.showTyped(List.of(typed, base));

        assertEquals(2.07, typed.getPrice(), 0.0);
        assertEquals(0.10, typed.getDiscount(), 0.0);
        assertEquals(6.21, typed.getTotal(), 0.0001);
        assertEquals(6.11, typed.getTotal_after_discount(), 0.0001);
        assertEquals(50, base.getPrice(), 0.0);
        assertEquals(100, base.getTotal(), 0.0);
    }

    @Test
    @DisplayName("changing the party restates each line, and leaves the entry row and a picked return line alone")
    void restate() {
        Sales typed = line(100.00, 3, 4.84);
        Sales entryRow = new Sales();
        entryRow.setPrice(7);
        Sales picked = line(100.00, 1, 0);
        picked.setSourceLineId(900);
        DocumentPricing dollars = new DocumentPricing(PartyCurrencyFixtures.USD, DOLLAR_RATE);

        InvoiceScreenCurrency.restate(List.of(typed, entryRow, picked), DocumentPricing.BASE, dollars);

        assertEquals(2.07, typed.getPrice(), 0.0);
        assertEquals(0.10, typed.getDiscount(), 0.0, "4.84 / 48.37");
        assertEquals(6.21, typed.getTotal(), 0.0001, "the total follows the price");
        assertEquals(7, entryRow.getPrice(), 0.0);
        assertEquals(100.00, picked.getPrice(), 0.0);
    }

    private static Sales line(double price, double quantity, double discount) {
        ItemsModel item = new ItemsModel();
        item.setId(12);
        Sales line = new Sales();
        line.setItems(item);
        line.setUnitsType(new UnitsModel(1, "قطعة", 1));
        line.setPrice(price);
        line.setQuantity(quantity);
        line.setDiscount(discount);
        InvoiceLineService.recalculate(line);
        return line;
    }
}
