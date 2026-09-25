package com.hamza.account.features.invoice;

import com.hamza.account.features.offers.Offer;
import com.hamza.account.features.offers.OfferKind;
import com.hamza.account.features.offers.OfferStatus;
import com.hamza.account.features.offers.OfferTarget;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A bundle's barcode at the till (V87, ق-ع١٢): found in the snapshot, refused out of its terms, its components as lines. */
class InvoiceBundleEntryTest {

    private static final LocalDate THURSDAY = LocalDate.of(2026, 9, 24);

    private static Offer bundle(String barcode, Integer weekdays, Set<Integer> tiers) {
        return new Offer(3, "طقم رمضان", OfferKind.BUNDLE, OfferStatus.ACTIVE, THURSDAY.minusDays(1), null, weekdays,
                0, null, null, new BigDecimal("150"), null, null, null, null, null, null, null, barcode, null,
                List.of(OfferTarget.component(40, null, new BigDecimal("1")),
                        OfferTarget.component(41, 2, new BigDecimal("2.5"))), tiers, null);
    }

    private static final Offer PERCENT = new Offer(4, "خصم", OfferKind.PERCENT, OfferStatus.ACTIVE, THURSDAY, null,
            null, 0, BigDecimal.TEN, null, null, null, null, List.of(OfferTarget.everything()), Set.of(), null);

    @Test
    @DisplayName("the scanned code finds its bundle, trimmed; any other code, or none, finds nothing")
    void find() {
        Offer ramadan = bundle("6221", null, Set.of());
        assertEquals(ramadan, InvoiceBundleEntry.find(List.of(PERCENT, ramadan), " 6221 ").orElseThrow());
        assertTrue(InvoiceBundleEntry.find(List.of(PERCENT, ramadan), "6222").isEmpty());
        assertTrue(InvoiceBundleEntry.find(List.of(PERCENT, bundle(null, null, Set.of())), "").isEmpty());
        assertTrue(InvoiceBundleEntry.find(List.of(), "6221").isEmpty());
    }

    @Test
    @DisplayName("a bundle outside its days or its tiers is refused by name rather than let in at full price")
    void inForce() throws Exception {
        InvoiceBundleEntry.requireInForce(bundle("6221", null, Set.of()), THURSDAY, 1);
        int fridays = 1 << (DayOfWeek.FRIDAY.getValue() - 1);
        UserValidationException wrongDay = assertThrows(UserValidationException.class,
                () -> InvoiceBundleEntry.requireInForce(bundle("6221", fridays, Set.of()), THURSDAY, 1));
        assertTrue(wrongDay.getMessage().contains("طقم رمضان"), wrongDay.getMessage());
        assertThrows(UserValidationException.class,
                () -> InvoiceBundleEntry.requireInForce(bundle("6221", null, Set.of(3)), THURSDAY, 1));
        assertThrows(UserValidationException.class,
                () -> InvoiceBundleEntry.requireInForce(bundle("6221", null, Set.of()), THURSDAY.minusDays(5), 1));
    }

    @Test
    @DisplayName("each component is a line: its item, its unit - the base when it names none - and its quantity")
    void requests() {
        List<ItemPickRequest> lines = InvoiceBundleEntry.requests(bundle("6221", null, Set.of()));
        assertEquals(2, lines.size());
        assertEquals(40, lines.get(0).itemId());
        assertNull(lines.get(0).unitId());
        assertEquals(1.0, lines.get(0).quantity());
        assertEquals(41, lines.get(1).itemId());
        assertEquals(2, lines.get(1).unitId());
        assertEquals(2.5, lines.get(1).quantity());
    }
}
