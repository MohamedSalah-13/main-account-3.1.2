package com.hamza.account.features.offers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** docs/pricing-and-offers-plan.md §5, worked by hand, and the order a line's offer is chosen in. */
class OfferEngineTest {

    /** A Thursday. */
    static final LocalDate DAY = LocalDate.of(2026, 9, 24);
    static final int PIECE = 1;
    static final int CARTON = 2;
    static final int DETERGENTS = 5;
    static final int CLEANING = 9;

    static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    /** Soap (item 11) in the detergents; powder (item 12) beside it. */
    static OfferEngine.Line soap(int index, String quantity, String price) {
        return new OfferEngine.Line(index, 11, PIECE, DETERGENTS, CLEANING, BigDecimal.ONE, d(quantity), d(price));
    }

    static OfferEngine.Line soapCartons(int index, String quantity, String price) {
        return new OfferEngine.Line(index, 11, CARTON, DETERGENTS, CLEANING, d("12"), d(quantity), d(price));
    }

    static OfferEngine.Line powder(int index, String quantity, String price) {
        return new OfferEngine.Line(index, 12, PIECE, DETERGENTS, CLEANING, BigDecimal.ONE, d(quantity), d(price));
    }

    static OfferEngine.Line rice(int index, String quantity, String price) {
        return new OfferEngine.Line(index, 20, PIECE, 7, 3, BigDecimal.ONE, d(quantity), d(price));
    }

    static Offer offer(int id, OfferKind kind, String value, Integer unitId, OfferTarget... targets) {
        return new Offer(id, "عرض " + id, kind, OfferStatus.ACTIVE, DAY.minusDays(10), DAY.plusDays(10), null,
                0, kind == OfferKind.PERCENT ? d(value) : null, kind == OfferKind.AMOUNT ? d(value) : null,
                kind == OfferKind.PRICE ? d(value) : null, unitId, null, List.of(targets), Set.of(), null);
    }

    static Offer withPriority(Offer offer, int priority) {
        return new Offer(offer.id(), offer.name(), offer.kind(), offer.status(), offer.startsOn(), offer.endsOn(),
                offer.weekdays(), priority, offer.percent(), offer.amount(), offer.offerPrice(), offer.unitId(),
                offer.notes(), offer.targets(), offer.priceTierIds(), offer.version());
    }

    static OfferEngine.Result run(List<OfferEngine.Line> lines, Offer... offers) {
        return OfferEngine.apply(lines, new OfferEngine.Context(DAY, 1), List.of(offers));
    }

    @Test
    @DisplayName("example 1: 10% off the detergents - soap 3 x 40 = 120 gives 12.00, a net of 108")
    void percentOnAGroup() {
        Offer tenPercent = offer(1, OfferKind.PERCENT, "10", null, OfferTarget.subGroup(DETERGENTS));
        OfferEngine.Result result = run(List.of(soap(0, "3", "40"), rice(1, "2", "30")), tenPercent);

        assertEquals(d("12.00"), result.forLine(0).orElseThrow().discount());
        assertEquals(d("108.00"), soap(0, "3", "40").value().subtract(result.forLine(0).orElseThrow().discount()));
        assertTrue(result.forLine(1).isEmpty(), "rice is not a detergent");
        assertEquals(List.of(new OfferEngine.OfferTotal(tenPercent, 1, d("12.00"))), result.totals());
    }

    @Test
    @DisplayName("a percentage rounds half up once, on the line")
    void percentRounds() {
        Offer offer = offer(1, OfferKind.PERCENT, "12.5", null, OfferTarget.everything());
        // 3 x 13.30 = 39.90; 12.5% = 4.9875 -> 4.99
        assertEquals(d("4.99"), run(List.of(soap(0, "3", "13.30")), offer).forLine(0).orElseThrow().discount());
    }

    @Nested
    @DisplayName("an amount off")
    class Amount {

        @Test
        @DisplayName("written for the base unit, a carton of twelve takes twelve of it")
        void perBaseUnit() {
            Offer halfOffAPiece = offer(1, OfferKind.AMOUNT, "0.50", null, OfferTarget.item(11));
            OfferEngine.Result result = run(List.of(soap(0, "4", "40"), soapCartons(1, "2", "450")), halfOffAPiece);
            assertEquals(d("2.00"), result.forLine(0).orElseThrow().discount());
            assertEquals(d("12.00"), result.forLine(1).orElseThrow().discount(), "0.50 x 12 x 2");
        }

        @Test
        @DisplayName("written for a carton, it reaches cartons and nothing else")
        void perNamedUnit() {
            Offer fiveOffACarton = offer(1, OfferKind.AMOUNT, "5", CARTON, OfferTarget.item(11));
            OfferEngine.Result result = run(List.of(soap(0, "4", "40"), soapCartons(1, "2", "450")), fiveOffACarton);
            assertTrue(result.forLine(0).isEmpty());
            assertEquals(d("10.00"), result.forLine(1).orElseThrow().discount());
        }

        @Test
        @DisplayName("never more than the line is worth")
        void capped() {
            Offer tooMuch = offer(1, OfferKind.AMOUNT, "50", null, OfferTarget.item(11));
            assertEquals(d("80.00"), run(List.of(soap(0, "2", "40")), tooMuch).forLine(0).orElseThrow().discount());
        }
    }

    @Nested
    @DisplayName("an offer price")
    class Price {

        @Test
        @DisplayName("a carton at 400 on a carton sold at 450: 50 each")
        void downToTheOfferPrice() {
            Offer cartonAt400 = offer(1, OfferKind.PRICE, "400", CARTON, OfferTarget.item(11));
            assertEquals(d("150.00"), run(List.of(soapCartons(0, "3", "450")), cartonAt400)
                    .forLine(0).orElseThrow().discount());
        }

        @Test
        @DisplayName("never up: a wholesale customer already below the offer takes nothing from it")
        void neverUp() {
            Offer pieceAt36 = offer(1, OfferKind.PRICE, "36", null, OfferTarget.item(11));
            assertTrue(run(List.of(soap(0, "3", "32")), pieceAt36).isEmpty());
            assertEquals(d("0.00"), OfferEngine.discountFor(pieceAt36, soap(0, "3", "32")).orElseThrow(),
                    "reached, and gives nothing");
        }

        @Test
        @DisplayName("written for a piece, a carton is held to twelve of it")
        void perBaseUnit() {
            Offer pieceAt36 = offer(1, OfferKind.PRICE, "36", null, OfferTarget.item(11));
            // 450 - 12 x 36 = 18 a carton
            assertEquals(d("36.00"), run(List.of(soapCartons(0, "2", "450")), pieceAt36)
                    .forLine(0).orElseThrow().discount());
        }
    }

    @Nested
    @DisplayName("what an offer reaches")
    class Reach {

        @Test
        @DisplayName("every detergent except the powder")
        void exclusion() {
            Offer offer = offer(1, OfferKind.PERCENT, "10", null,
                    OfferTarget.mainGroup(CLEANING), OfferTarget.item(12).except());
            OfferEngine.Result result = run(List.of(soap(0, "1", "40"), powder(1, "1", "60")), offer);
            assertTrue(result.forLine(0).isPresent());
            assertTrue(result.forLine(1).isEmpty());
        }

        @Test
        @DisplayName("an item in one of its units")
        void itemInUnit() {
            Offer offer = offer(1, OfferKind.PERCENT, "10", null, OfferTarget.itemInUnit(11, CARTON));
            OfferEngine.Result result = run(List.of(soap(0, "1", "40"), soapCartons(1, "1", "450")), offer);
            assertTrue(result.forLine(0).isEmpty());
            assertEquals(d("45.00"), result.forLine(1).orElseThrow().discount());
        }

        @Test
        @DisplayName("only the tiers it names: a consumer offer does not reach the wholesale customer")
        void tiers() {
            Offer retailOnly = new Offer(1, "للمستهلك", OfferKind.PERCENT, OfferStatus.ACTIVE, DAY, null, null, 0,
                    d("10"), null, null, null, null, List.of(OfferTarget.everything()), Set.of(1, 2), null);
            List<OfferEngine.Line> lines = List.of(soap(0, "1", "40"));
            assertFalse(OfferEngine.apply(lines, new OfferEngine.Context(DAY, 1), List.of(retailOnly)).isEmpty());
            assertTrue(OfferEngine.apply(lines, new OfferEngine.Context(DAY, 3), List.of(retailOnly)).isEmpty());
            assertTrue(OfferEngine.apply(lines, new OfferEngine.Context(DAY, null), List.of(retailOnly)).isEmpty());
        }

        @Test
        @DisplayName("its dates and its days, judged by the document's date")
        void datesAndDays() {
            Offer weekend = new Offer(1, "نهاية الأسبوع", OfferKind.PERCENT, OfferStatus.ACTIVE, DAY.minusDays(30),
                    DAY.plusDays(30), Weekdays.of(Set.of(java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.SATURDAY)),
                    0, d("10"), null, null, null, null, List.of(OfferTarget.everything()), Set.of(), null);
            List<OfferEngine.Line> lines = List.of(soap(0, "1", "40"));
            assertTrue(OfferEngine.apply(lines, new OfferEngine.Context(DAY, 1), List.of(weekend)).isEmpty());
            assertFalse(OfferEngine.apply(lines, new OfferEngine.Context(DAY.plusDays(1), 1), List.of(weekend)).isEmpty());
            assertTrue(OfferEngine.apply(lines, new OfferEngine.Context(DAY.plusDays(36), 1), List.of(weekend)).isEmpty(),
                    "after its end, a Friday or not");
        }

        @Test
        @DisplayName("a draft reaches nothing; a stopped offer reaches only a document already carrying it")
        void status() {
            Offer draft = new Offer(1, "مسودة", OfferKind.PERCENT, OfferStatus.DRAFT, DAY, null, null, 0, d("10"),
                    null, null, null, null, List.of(OfferTarget.everything()), Set.of(), null);
            Offer stopped = new Offer(2, "موقوف", OfferKind.PERCENT, OfferStatus.STOPPED, DAY, null, null, 0,
                    d("10"), null, null, null, null, List.of(OfferTarget.everything()), Set.of(), null);
            List<OfferEngine.Line> lines = List.of(soap(0, "1", "40"));
            assertTrue(OfferEngine.apply(lines, new OfferEngine.Context(DAY, 1, Set.of(1)), List.of(draft)).isEmpty());
            assertTrue(OfferEngine.apply(lines, new OfferEngine.Context(DAY, 1), List.of(stopped)).isEmpty());
            assertFalse(OfferEngine.apply(lines, new OfferEngine.Context(DAY, 1, Set.of(2)), List.of(stopped)).isEmpty(),
                    "correcting a note on last month's invoice does not take its offer away (ق-ع٧)");
        }
    }

    @Nested
    @DisplayName("one offer a line, in a written order")
    class Order {

        final Offer tenPercent = offer(1, OfferKind.PERCENT, "10", null, OfferTarget.subGroup(DETERGENTS));
        final Offer fiveOff = offer(2, OfferKind.AMOUNT, "5", null, OfferTarget.item(11));
        final Offer alsoFiveOff = offer(3, OfferKind.AMOUNT, "5", null, OfferTarget.item(11));

        @Test
        @DisplayName("the higher priority wins even when it gives less")
        void priorityFirst() {
            OfferEngine.Result result = run(List.of(soap(0, "3", "40")), withPriority(tenPercent, 1), fiveOff);
            // five off each is 15 and 10% is 12 - but 10% was given the priority
            assertEquals(1, result.forLine(0).orElseThrow().offer().id());
        }

        @Test
        @DisplayName("then the one giving the customer more")
        void moreForTheCustomer() {
            assertEquals(2, run(List.of(soap(0, "3", "40")), tenPercent, fiveOff).forLine(0).orElseThrow().offer().id());
            assertEquals(1, run(List.of(soap(0, "3", "80")), tenPercent, fiveOff).forLine(0).orElseThrow().offer().id());
        }

        @Test
        @DisplayName("then the oldest, whichever order they are handed in")
        void thenTheOldest() {
            assertEquals(2, run(List.of(soap(0, "3", "40")), alsoFiveOff, fiveOff).forLine(0).orElseThrow().offer().id());
        }

        @Test
        @DisplayName("each offer's total counts its lines, in the order they first appear")
        void totals() {
            OfferEngine.Result result = run(List.of(rice(0, "1", "10"), soap(1, "3", "80"), powder(2, "2", "60"),
                    soap(3, "1", "40")), tenPercent, fiveOff);
            assertEquals(List.of(new OfferEngine.OfferTotal(tenPercent, 2, d("36.00")),
                    new OfferEngine.OfferTotal(fiveOff, 1, d("5.00"))), result.totals());
            assertEquals(d("41.00"), result.discount());
        }
    }
}
