package com.hamza.account.features.offers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The two offer reminders' rules: which offer is about to end, and which item on offer is running out. */
class OfferAlertsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

    private static Offer offer(int id, OfferStatus status, LocalDate endsOn, OfferTarget... targets) {
        return new Offer(id, "عرض " + id, OfferKind.PERCENT, status, TODAY.minusDays(20), endsOn, null, 0,
                BigDecimal.TEN, null, null, null, null, List.of(targets), Set.of(), null);
    }

    @Test
    @DisplayName("the switched-on offers ending today or tomorrow, the soonest first - not the day after, not a stopped one")
    void ending() {
        List<OfferAlerts.Ending> ending = OfferAlerts.ending(List.of(
                offer(1, OfferStatus.ACTIVE, TODAY.plusDays(1), OfferTarget.everything()),
                offer(2, OfferStatus.ACTIVE, TODAY, OfferTarget.everything()),
                offer(3, OfferStatus.ACTIVE, TODAY.plusDays(2), OfferTarget.everything()),
                offer(4, OfferStatus.STOPPED, TODAY, OfferTarget.everything()),
                offer(5, OfferStatus.ACTIVE, null, OfferTarget.everything()),
                offer(6, OfferStatus.ACTIVE, TODAY.minusDays(1), OfferTarget.everything())), TODAY);

        assertEquals(List.of(2, 1), ending.stream().map(end -> end.offer().id()).toList());
        assertEquals(0, ending.get(0).daysLeft());
        assertEquals(1, ending.get(1).daysLeft());
    }

    @Test
    @DisplayName("the items an offer names by itself - earned, given or in a bundle - never through a group or left out")
    void namedItems() {
        Offer gift = new Offer(7, "هدية", OfferKind.BUY_GET, OfferStatus.ACTIVE, TODAY, null, null, 0, null, null,
                null, null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(100), null, null, null,
                List.of(OfferTarget.item(10), OfferTarget.reward(11)), Set.of(), null);
        Offer group = offer(8, OfferStatus.ACTIVE, null, OfferTarget.subGroup(3), OfferTarget.item(12).except());
        Offer ended = offer(9, OfferStatus.ACTIVE, TODAY.minusDays(1), OfferTarget.item(13));
        Offer bundle = OfferEngineBundleTest.bundle(10, "50", OfferTarget.component(14, null, BigDecimal.ONE),
                OfferTarget.component(15, 2, BigDecimal.ONE));

        assertEquals(Set.of(10, 11, 14, 15), OfferAlerts.namedItems(List.of(gift, group, ended, bundle), TODAY));
    }

    @Test
    @DisplayName("gone, or at or under a minimum that was set: the emptiest first, each with the offers naming it")
    void shortOfStock() {
        Offer first = offer(1, OfferStatus.ACTIVE, null, OfferTarget.item(10), OfferTarget.item(11));
        Offer second = offer(2, OfferStatus.ACTIVE, null, OfferTarget.item(10));
        List<OfferAlerts.ItemBalance> balances = List.of(
                new OfferAlerts.ItemBalance(10, "شاي", new BigDecimal("5"), new BigDecimal("5")),
                new OfferAlerts.ItemBalance(11, "سكر", BigDecimal.ZERO, new BigDecimal("-2")),
                new OfferAlerts.ItemBalance(12, "أرز", new BigDecimal("5"), new BigDecimal("2")));

        List<OfferAlerts.ShortItem> shortItems = OfferAlerts.shortOfStock(List.of(first, second), balances, TODAY);

        assertEquals(List.of(11, 10), shortItems.stream().map(item -> item.item().itemId()).toList(),
                "the rice is short too, and no offer names it");
        assertEquals(List.of(1, 2), shortItems.get(1).offers().stream().map(Offer::id).toList());
    }

    @Test
    @DisplayName("a minimum of zero is none set: an item with some left is not short")
    void noMinimum() {
        assertFalse(new OfferAlerts.ItemBalance(1, "x", BigDecimal.ZERO, BigDecimal.ONE).isShort());
        assertTrue(new OfferAlerts.ItemBalance(1, "x", BigDecimal.ZERO, BigDecimal.ZERO).isShort());
        assertTrue(new OfferAlerts.ItemBalance(1, "x", BigDecimal.TEN, BigDecimal.TEN).isShort());
    }
}
