package com.hamza.account.features.invoice;

import com.hamza.account.features.pricing.PriceTiers;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoice;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.UnitsModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceTierRepricingTest {

    private static final UnitsModel PIECE = new UnitsModel(1, "قطعة", 1);

    private static ItemsModel item(int id, double retail, double wholesale) {
        ItemsModel item = new ItemsModel(id, "B" + id, "صنف " + id);
        item.setSelPrice1(retail);
        item.setSelPrice3(wholesale);
        return item;
    }

    private static Sales line(ItemsModel item, double price, Double listPrice) {
        Sales line = new SalesInvoice().object_TableData(0, 0, item.getId(), price, 2, 0, price * 2, PIECE, item, null);
        line.setListPrice(listPrice == null ? null : BigDecimal.valueOf(listPrice).setScale(2));
        return line;
    }

    @Test
    @DisplayName("a line at its list price moves to the new tier's; a typed one stays and is counted")
    void repricesOnlyListPricedLines() {
        Sales listed = line(item(1, 10, 9), 10, 10.0);
        Sales typed = line(item(2, 20, 18), 17, 20.0);
        Sales reopened = line(item(3, 30, 27), 30, null);

        PriceTierRepricing.Result result = PriceTierRepricing.restate(List.of(listed, typed, reopened), 3,
                PriceTiers::itemPrice, DocumentPricing.BASE);

        assertEquals(new PriceTierRepricing.Result(1, 2), result);
        assertEquals(9, listed.getPrice());
        assertEquals(new BigDecimal("9.00"), listed.getListPrice());
        assertEquals(18, listed.getTotal(), "the total follows the price");
        assertEquals(17, typed.getPrice());
        assertEquals(30, reopened.getPrice());
    }

    @Test
    @DisplayName("a line picked from a source invoice keeps that invoice's price")
    void sourceLinesStay() {
        Sales picked = line(item(1, 10, 9), 10, 10.0);
        picked.setSourceLineId(77);
        assertEquals(new PriceTierRepricing.Result(0, 1), PriceTierRepricing.restate(List.of(picked), 3,
                PriceTiers::itemPrice, DocumentPricing.BASE));
        assertEquals(10, picked.getPrice());
    }

    @Test
    @DisplayName("a tier with no price for the item: tier 1's, and the line is marked")
    void marksTheFirstTierFallback() {
        Sales line = line(item(1, 10, 0), 10, 10.0);
        PriceTierRepricing.restate(List.of(line), 3, PriceTiers::itemPrice, DocumentPricing.BASE);
        assertEquals(10, line.getPrice());
        assertTrue(line.isFromFirstTier());

        PriceTierRepricing.restate(List.of(line), 1, PriceTiers::itemPrice, DocumentPricing.BASE);
        assertFalse(line.isFromFirstTier(), "back at tier 1 it is simply tier 1's price");
    }

    @Test
    @DisplayName("the quick screen's entry row is not a line")
    void placeholderIgnored() {
        Sales entryRow = new SalesInvoice().object_TableData(0, 0, 0, 0, 0, 0, 0, new UnitsModel(),
                new ItemsModel(), null);
        assertFalse(PriceTierRepricing.restate(List.of(entryRow), 3, PriceTiers::itemPrice,
                DocumentPricing.BASE).touchedAnything());
    }
}
