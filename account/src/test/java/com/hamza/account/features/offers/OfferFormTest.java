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

    private static void assertKey(String key, org.junit.jupiter.api.function.Executable build) {
        UserValidationException refused = assertThrows(UserValidationException.class, build);
        assertEquals(key, refused.getMessage());
    }
}
