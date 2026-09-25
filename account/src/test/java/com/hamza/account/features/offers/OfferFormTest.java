package com.hamza.account.features.offers;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferFormTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 24);
    private static final Set<DayOfWeek> EVERY_DAY = EnumSet.allOf(DayOfWeek.class);

    private static Offer build(OfferKind kind, String value, Integer unitId, Set<DayOfWeek> days,
                               List<OfferTarget> targets) throws UserValidationException {
        return OfferForm.build(0, " عصير ", kind, null, DAY, null, days, 0,
                value == null ? null : new BigDecimal(value), unitId, " ", targets, Set.of(), null);
    }

    @Test
    @DisplayName("the value goes to its kind's column alone, a unit only with an amount or a price")
    void build() throws Exception {
        Offer percent = build(OfferKind.PERCENT, "10", 2, EVERY_DAY, List.of(OfferTarget.everything()));
        assertEquals("عصير", percent.name());
        assertEquals(OfferStatus.DRAFT, percent.status());
        assertEquals(new BigDecimal("10"), percent.percent());
        assertNull(percent.amount());
        assertNull(percent.unitId(), "a percentage is not written for a unit");
        assertNull(percent.notes());
        assertNull(percent.weekdays(), "every day is no mask");

        Offer amount = build(OfferKind.AMOUNT, "5", 2, Set.of(DayOfWeek.FRIDAY), List.of(OfferTarget.item(3)));
        assertEquals(new BigDecimal("5"), amount.amount());
        assertEquals(2, amount.unitId());
        assertEquals(Weekdays.bit(DayOfWeek.FRIDAY), amount.weekdays());
    }

    @Test
    @DisplayName("each rule refuses with its own key")
    void refusals() {
        List<OfferTarget> all = List.of(OfferTarget.everything());
        assertKey("offer.error.weekdays", () -> build(OfferKind.PERCENT, "10", null, Set.of(), all));
        assertKey("offer.error.percent", () -> build(OfferKind.PERCENT, "100.5", null, EVERY_DAY, all));
        assertKey("offer.error.percent", () -> build(OfferKind.PERCENT, "0", null, EVERY_DAY, all));
        assertKey("offer.error.percent", () -> build(OfferKind.PERCENT, "1.2345", null, EVERY_DAY, all));
        assertKey("offer.error.amount", () -> build(OfferKind.AMOUNT, "5.001", null, EVERY_DAY, all));
        assertKey("offer.error.price", () -> build(OfferKind.PRICE, null, null, EVERY_DAY, all));
        assertKey("offer.error.targets", () -> build(OfferKind.PERCENT, "10", null, EVERY_DAY,
                List.of(OfferTarget.item(3).except())));
        assertKey("offer.error.dates", () -> OfferForm.build(0, "x", OfferKind.PERCENT, null, DAY, DAY.minusDays(1),
                EVERY_DAY, 0, BigDecimal.TEN, null, null, all, Set.of(), null));
        assertKey("offer.error.priority", () -> OfferForm.build(0, "x", OfferKind.PERCENT, null, DAY, null,
                EVERY_DAY, 1000, BigDecimal.TEN, null, null, all, Set.of(), null));
        assertKey("offer.error.tier", () -> OfferForm.build(0, "x", OfferKind.PERCENT, null, DAY, null,
                EVERY_DAY, 0, BigDecimal.TEN, null, null, all, Set.of(4), null));
        assertKey("offer.error.name.required", () -> OfferForm.build(0, " ", OfferKind.PERCENT, null, DAY, null,
                EVERY_DAY, 0, BigDecimal.TEN, null, null, all, Set.of(), null));
        assertKey("offer.error.name.long", () -> OfferForm.build(0, "x".repeat(101), OfferKind.PERCENT, null, DAY,
                null, EVERY_DAY, 0, BigDecimal.TEN, null, null, all, Set.of(), null));
    }

    @Test
    @DisplayName("an offer's terms are everything but its name, its notes and its end")
    void sameTerms() throws Exception {
        Offer offer = build(OfferKind.AMOUNT, "5", 2, EVERY_DAY, List.of(OfferTarget.item(3)));
        Offer renamed = new Offer(0, "آخر", offer.kind(), offer.status(), offer.startsOn(), DAY.plusDays(9),
                offer.weekdays(), offer.priority(), null, new BigDecimal("5.00"), null, offer.unitId(), "note",
                offer.targets(), offer.priceTierIds(), null);
        assertTrue(OfferForm.sameTerms(offer, renamed), "5 and 5.00 are one amount");
        Offer wider = new Offer(0, offer.name(), offer.kind(), offer.status(), offer.startsOn(), null,
                offer.weekdays(), offer.priority(), null, offer.amount(), null, offer.unitId(), null,
                List.of(OfferTarget.item(3), OfferTarget.item(4)), offer.priceTierIds(), null);
        assertFalse(OfferForm.sameTerms(offer, wider));
    }

    @Test
    @DisplayName("a target names what its scope uses and nothing else; everything cannot be excluded")
    void targets() {
        assertThrows(IllegalArgumentException.class,
                () -> new OfferTarget(OfferScope.SUB_GROUP, 3, null, 4, null, false));
        assertThrows(IllegalArgumentException.class, () -> OfferTarget.everything().except());
        assertEquals(Set.of(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), Weekdays.days(1 | 64));
        assertEquals(7, Weekdays.days(null).size());
        assertNull(Weekdays.of(EVERY_DAY));
    }

    private static Offer buildTerms(OfferKind kind, OfferForm.Terms terms, List<OfferTarget> targets)
            throws UserValidationException {
        return OfferForm.build(0, "كمية", kind, null, DAY, null, EVERY_DAY, 0, terms, 2, null, targets, Set.of(),
                null);
    }

    private static BigDecimal d(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    @Test
    @DisplayName("a quantity offer keeps its group and its price; a buy-and-get its three figures and no value")
    void theQuantityKinds() throws Exception {
        Offer threeFor100 = buildTerms(OfferKind.QUANTITY_PRICE,
                new OfferForm.Terms(d("100"), d("3"), d("9"), d("50"), d("2"), null), List.of(OfferTarget.item(3)));
        assertEquals(d("100"), threeFor100.offerPrice());
        assertEquals(d("3"), threeFor100.buyQuantity());
        assertNull(threeFor100.getQuantity(), "what a quantity offer does not use is not kept");
        assertNull(threeFor100.getPercent());
        assertEquals(d("2"), threeFor100.maxPerInvoice());
        assertEquals(d("3"), threeFor100.groupSize());

        Offer twoPlusOne = buildTerms(OfferKind.BUY_GET,
                new OfferForm.Terms(d("7"), d("2"), d("1"), d("100"), null, d("500")),
                List.of(OfferTarget.item(3), OfferTarget.reward(4)));
        assertNull(twoPlusOne.offerPrice(), "a buy-and-get has no value box");
        assertEquals(d("3"), twoPlusOne.groupSize(), "two bought and one given");
        assertEquals(d("500"), twoPlusOne.quantityLimit());
        assertEquals(4, twoPlusOne.rewardTarget().orElseThrow().itemId());
    }

    @Test
    @DisplayName("each phase C rule refuses with its own key")
    void theQuantityRefusals() {
        List<OfferTarget> item = List.of(OfferTarget.item(3));
        assertKey("offer.error.buy.quantity", () -> buildTerms(OfferKind.QUANTITY_PRICE,
                new OfferForm.Terms(d("100"), null, null, null, null, null), item));
        assertKey("offer.error.price", () -> buildTerms(OfferKind.QUANTITY_PRICE,
                new OfferForm.Terms(null, d("3"), null, null, null, null), item));
        assertKey("offer.error.get.quantity", () -> buildTerms(OfferKind.BUY_GET,
                new OfferForm.Terms(null, d("2"), null, d("100"), null, null), item));
        assertKey("offer.error.get.percent", () -> buildTerms(OfferKind.BUY_GET,
                new OfferForm.Terms(null, d("2"), d("1"), d("100.5"), null, null), item));
        assertKey("offer.error.buy.quantity", () -> buildTerms(OfferKind.BUY_GET,
                new OfferForm.Terms(null, d("2.0001"), d("1"), d("100"), null, null), item));
        assertKey("offer.error.limit", () -> buildTerms(OfferKind.PERCENT,
                new OfferForm.Terms(d("10"), null, null, null, d("0"), null), List.of(OfferTarget.everything())));
        assertKey("offer.error.limit", () -> buildTerms(OfferKind.PERCENT,
                new OfferForm.Terms(d("10"), null, null, null, null, d("-5")), List.of(OfferTarget.everything())));
        assertKey("offer.error.reward", () -> buildTerms(OfferKind.PERCENT,
                new OfferForm.Terms(d("10"), null, null, null, null, null),
                List.of(OfferTarget.item(3), OfferTarget.reward(4))));
        assertKey("offer.error.reward", () -> buildTerms(OfferKind.BUY_GET,
                new OfferForm.Terms(null, d("1"), d("1"), d("100"), null, null),
                List.of(OfferTarget.item(3), OfferTarget.reward(4), OfferTarget.reward(5))));
        assertKey("offer.error.targets", () -> buildTerms(OfferKind.BUY_GET,
                new OfferForm.Terms(null, d("1"), d("1"), d("100"), null, null), List.of(OfferTarget.reward(4))));
    }

    @Test
    @DisplayName("the quantities and the limits are terms: a used offer keeps them")
    void theLimitsAreTerms() throws Exception {
        Offer offer = buildTerms(OfferKind.QUANTITY_PRICE,
                new OfferForm.Terms(d("100"), d("3"), null, null, d("2"), null), List.of(OfferTarget.item(3)));
        Offer raised = buildTerms(OfferKind.QUANTITY_PRICE,
                new OfferForm.Terms(d("100"), d("3"), null, null, d("3"), null), List.of(OfferTarget.item(3)));
        Offer same = buildTerms(OfferKind.QUANTITY_PRICE,
                new OfferForm.Terms(d("100.00"), d("3.000"), null, null, d("2"), null), List.of(OfferTarget.item(3)));
        assertFalse(OfferForm.sameTerms(offer, raised));
        assertTrue(OfferForm.sameTerms(offer, same));
    }

    @Test
    @DisplayName("a gift is one item, never excluded")
    void aGiftIsOneItem() {
        assertThrows(IllegalArgumentException.class,
                () -> new OfferTarget(OfferScope.SUB_GROUP, null, null, 4, null, false, OfferRole.REWARD));
        assertThrows(IllegalArgumentException.class, () -> OfferTarget.reward(4).except());
        assertEquals(OfferRole.QUALIFY, OfferTarget.item(3).role(), "every target before V86 earns the offer");
    }

    private static void assertKey(String key, org.junit.jupiter.api.function.Executable build) {
        UserValidationException refused = assertThrows(UserValidationException.class, build);
        assertEquals(key, refused.getMessage());
    }
}
