package com.hamza.account.features.offers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hamza.account.features.offers.OfferEngineTest.CARTON;
import static com.hamza.account.features.offers.OfferEngineTest.DAY;
import static com.hamza.account.features.offers.OfferEngineTest.DETERGENTS;
import static com.hamza.account.features.offers.OfferEngineTest.d;
import static com.hamza.account.features.offers.OfferEngineTest.offer;
import static com.hamza.account.features.offers.OfferEngineTest.powder;
import static com.hamza.account.features.offers.OfferEngineTest.run;
import static com.hamza.account.features.offers.OfferEngineTest.soap;
import static com.hamza.account.features.offers.OfferEngineTest.soapCartons;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** docs/pricing-and-offers-plan.md §5 examples 2, 3 and 4, worked by hand, and the rest of phase C (V86). */
class OfferEngineQuantityTest {

    static final int SHAMPOO = 30;
    static final int CONDITIONER = 31;

    static OfferEngine.Line shampoo(int index, String quantity, String price) {
        return new OfferEngine.Line(index, SHAMPOO, OfferEngineTest.PIECE, 8, 4, BigDecimal.ONE, d(quantity), d(price));
    }

    static OfferEngine.Line conditioner(int index, String quantity, String price) {
        return new OfferEngine.Line(index, CONDITIONER, OfferEngineTest.PIECE, 8, 4, BigDecimal.ONE, d(quantity),
                d(price));
    }

    /** "{@code size} for {@code price}", counted in {@code unitId} or the base units. */
    static Offer quantityPrice(int id, String size, String price, Integer unitId, OfferTarget... targets) {
        return new Offer(id, "كمية " + id, OfferKind.QUANTITY_PRICE, OfferStatus.ACTIVE, DAY.minusDays(10), null,
                null, 0, null, null, d(price), unitId, d(size), null, null, null, null, null, List.of(targets),
                Set.of(), null);
    }

    /** "Buy {@code buy}, get {@code get} at {@code percent} off". */
    static Offer buyGet(int id, String buy, String get, String percent, OfferTarget... targets) {
        return new Offer(id, "اشترِ " + id, OfferKind.BUY_GET, OfferStatus.ACTIVE, DAY.minusDays(10), null, null, 0,
                null, null, null, null, d(buy), d(get), d(percent), null, null, null, List.of(targets), Set.of(), null);
    }

    static Offer limited(Offer offer, String perInvoice, String total) {
        return new Offer(offer.id(), offer.name(), offer.kind(), offer.status(), offer.startsOn(), offer.endsOn(),
                offer.weekdays(), offer.priority(), offer.percent(), offer.amount(), offer.offerPrice(), offer.unitId(),
                offer.buyQuantity(), offer.getQuantity(), offer.getPercent(), perInvoice == null ? null : d(perInvoice),
                total == null ? null : d(total), offer.notes(), offer.targets(), offer.priceTierIds(), offer.version());
    }

    static Offer withPriority(Offer offer, int priority) {
        return new Offer(offer.id(), offer.name(), offer.kind(), offer.status(), offer.startsOn(), offer.endsOn(),
                offer.weekdays(), priority, offer.percent(), offer.amount(), offer.offerPrice(), offer.unitId(),
                offer.buyQuantity(), offer.getQuantity(), offer.getPercent(), offer.maxPerInvoice(),
                offer.quantityLimit(), offer.notes(), offer.targets(), offer.priceTierIds(), offer.version());
    }

    @Nested
    @DisplayName("example 2: 3 for 100")
    class QuantityPrice {

        @Test
        @DisplayName("seven at 40 are two groups: 2 x (120 - 100) = 40, and the line 280 - 40 = 240 = 200 + 40")
        void twoGroupsOfSeven() {
            Offer threeFor100 = quantityPrice(1, "3", "100", null, OfferTarget.item(11));
            OfferEngine.Result result = run(List.of(soap(0, "7", "40")), threeFor100);

            OfferEngine.Applied applied = result.forLine(0).orElseThrow();
            assertEquals(d("40.00"), applied.discount());
            assertEquals(d("6.000"), applied.quantity(), "six of the seven units are in the two groups");
            assertEquals(d("240.00"), soap(0, "7", "40").value().subtract(applied.discount()));
        }

        @Test
        @DisplayName("a wholesale customer at 32 takes nothing: 3 x 32 = 96 is already under 100 (ق-ع٦)")
        void neverRaisesAPrice() {
            Offer threeFor100 = quantityPrice(1, "3", "100", null, OfferTarget.item(11));
            OfferEngine.Result result = run(List.of(soap(0, "7", "32")), threeFor100);

            assertTrue(result.isEmpty());
            assertTrue(result.hints().isEmpty(), "and no hint to buy more of something that gives nothing");
        }

        @Test
        @DisplayName("a carton of twelve and three pieces are fifteen pieces: five groups, the dearest units first,"
                + " shared by value")
        void cartonsAndPiecesArePooled() {
            Offer threeFor100 = quantityPrice(1, "3", "100", null, OfferTarget.item(11));
            // Pieces at 40 are dearer than the carton's 450 / 12 = 37.50 a piece, so they are counted first:
            // 3 x 40 + 12 x 37.50 = 570 for five groups at 500 - a discount of 70, shared 120 : 450.
            OfferEngine.Result result = run(List.of(soap(0, "3", "40"), soapCartons(1, "1", "450")), threeFor100);

            assertEquals(d("14.74"), result.forLine(0).orElseThrow().discount(), "120 / 570 x 70 = 14.7368");
            assertEquals(d("55.26"), result.forLine(1).orElseThrow().discount(), "450 / 570 x 70 = 55.2632");
            assertEquals(d("70.00"), result.discount());
            assertEquals(d("3.000"), result.forLine(0).orElseThrow().quantity());
            assertEquals(d("12.000"), result.forLine(1).orElseThrow().quantity(), "the carton's twelve base units");
        }

        @Test
        @DisplayName("an offer written for the carton counts cartons, and a piece is not reached")
        void anOfferInAUnitCountsThatUnit() {
            Offer twoCartons = quantityPrice(1, "2", "800", CARTON, OfferTarget.item(11));
            OfferEngine.Result result = run(List.of(soap(0, "30", "40"), soapCartons(1, "2", "450")), twoCartons);

            assertTrue(result.forLine(0).isEmpty());
            assertEquals(d("100.00"), result.forLine(1).orElseThrow().discount(), "2 x 450 - 800");
            assertEquals(d("2.000"), result.forLine(1).orElseThrow().quantity());
        }

        @Test
        @DisplayName("a group across two lines of one item shares by value; the piastre left goes to the largest line")
        void aGroupAcrossTwoLines() {
            Offer threeFor100 = quantityPrice(1, "3", "100", null, OfferTarget.item(11));
            OfferEngine.Result result = run(List.of(soap(0, "2", "40"), soap(1, "2", "40")), threeFor100);

            // One group of the four: two units of the first line and one of the second, 120 - 100 = 20.
            assertEquals(d("13.33"), result.forLine(0).orElseThrow().discount());
            assertEquals(d("6.67"), result.forLine(1).orElseThrow().discount());
            assertEquals(d("1.000"), result.forLine(1).orElseThrow().quantity());
        }

        @Test
        @DisplayName("each item of a group is counted apart: two soaps and one powder are no group of three")
        void itemsAreCountedApart() {
            Offer threeFor100 = quantityPrice(1, "3", "100", null, OfferTarget.subGroup(DETERGENTS));
            OfferEngine.Result result = run(List.of(soap(0, "2", "40"), powder(1, "1", "40")), threeFor100);

            assertTrue(result.isEmpty());
            assertEquals(2, result.hints().size(), "each item says what would complete its own group");
        }

        @Test
        @DisplayName("two units short of a group is a hint: add one more")
        void theHintToComplete() {
            Offer threeFor100 = quantityPrice(1, "3", "100", null, OfferTarget.item(11));
            OfferEngine.Result result = run(List.of(soap(0, "5", "40")), threeFor100);

            OfferEngine.Hint hint = result.hints().get(0);
            assertEquals(OfferEngine.HintKind.COMPLETE, hint.kind());
            assertEquals(11, hint.itemId());
            assertEquals(d("1.000"), hint.missing(), "five is one group and two over: one more is a second");
        }
    }

    @Nested
    @DisplayName("example 3: buy 2, get 1 of the same item")
    class BuyGetTheSame {

        @Test
        @DisplayName("three at 30 give one free: a discount of 30, a net of 60")
        void threeGiveOne() {
            Offer twoPlusOne = buyGet(1, "2", "1", "100", OfferTarget.item(11));
            OfferEngine.Result result = run(List.of(soap(0, "3", "30")), twoPlusOne);

            assertEquals(d("30.00"), result.forLine(0).orElseThrow().discount());
            assertEquals(d("3.000"), result.forLine(0).orElseThrow().quantity());
        }

        @Test
        @DisplayName("five are one group - 30 off, a net of 120 - and a hint: one more and it is free")
        void fiveAreOneGroupAndAHint() {
            Offer twoPlusOne = buyGet(1, "2", "1", "100", OfferTarget.item(11));
            OfferEngine.Result result = run(List.of(soap(0, "5", "30")), twoPlusOne);

            assertEquals(d("30.00"), result.forLine(0).orElseThrow().discount());
            assertEquals(d("120.00"), soap(0, "5", "30").value().subtract(result.discount()));
            OfferEngine.Hint hint = result.hints().get(0);
            assertEquals(OfferEngine.HintKind.FREE, hint.kind());
            assertEquals(d("1.000"), hint.missing());
        }

        @Test
        @DisplayName("four are one group and one over: no hint, the customer has not bought two more")
        void noHintBeforeTheBuyIsMet() {
            Offer twoPlusOne = buyGet(1, "2", "1", "100", OfferTarget.item(11));
            assertTrue(run(List.of(soap(0, "4", "30")), twoPlusOne).hints().isEmpty());
        }

        @Test
        @DisplayName("half off the third: 15 on three at 30")
        void aPercentageOffWhatIsGiven() {
            Offer twoPlusHalf = buyGet(1, "2", "1", "50", OfferTarget.item(11));
            assertEquals(d("15.00"), run(List.of(soap(0, "3", "30")), twoPlusHalf).discount());
        }

        @Test
        @DisplayName("the unit given is the cheapest of the group: a piece at 30 beside two at 40")
        void theCheapestIsGiven() {
            Offer twoPlusOne = buyGet(1, "2", "1", "100", OfferTarget.item(11));
            OfferEngine.Result result = run(List.of(soap(0, "2", "40"), soap(1, "1", "30")), twoPlusOne);

            // 30 off a group worth 110, shared by value: 80 / 110 x 30 = 21.82 and 30 / 110 x 30 = 8.18.
            assertEquals(d("30.00"), result.discount());
            assertEquals(d("21.82"), result.forLine(0).orElseThrow().discount());
            assertEquals(d("8.18"), result.forLine(1).orElseThrow().discount());
        }
    }

    @Nested
    @DisplayName("example 4: the shampoo at 60, the conditioner at 40 a gift")
    class BuyGetAGift {

        static Offer shampooGetsConditioner() {
            return buyGet(1, "1", "1", "100", OfferTarget.item(SHAMPOO), OfferTarget.reward(CONDITIONER));
        }

        @Test
        @DisplayName("100 of goods, the 40 off shared by value: shampoo 24, conditioner 16 - 36 + 24 = 60, the shampoo's price")
        void theGiftIsSharedByValue() {
            OfferEngine.Result result = run(List.of(shampoo(0, "1", "60"), conditioner(1, "1", "40")),
                    shampooGetsConditioner());

            assertEquals(d("24.00"), result.forLine(0).orElseThrow().discount());
            assertEquals(d("16.00"), result.forLine(1).orElseThrow().discount());
            assertEquals(d("60.00"), d("100").subtract(result.discount()));
            assertEquals(d("1.000"), result.forLine(0).orElseThrow().quantity());
            assertEquals(d("1.000"), result.forLine(1).orElseThrow().quantity());
        }

        @Test
        @DisplayName("the gift not on the invoice gives nothing and says so: add one conditioner")
        void aGiftEarnedAndMissingIsAHint() {
            OfferEngine.Result result = run(List.of(shampoo(0, "2", "60")), shampooGetsConditioner());

            assertTrue(result.isEmpty(), "no line at zero: the gift is a line at its price (ق-ع٣)");
            OfferEngine.Hint hint = result.hints().get(0);
            assertEquals(OfferEngine.HintKind.GIFT, hint.kind());
            assertEquals(CONDITIONER, hint.itemId());
            assertEquals(d("2.000"), hint.missing(), "two shampoos earn two");
        }

        @Test
        @DisplayName("two shampoos and one conditioner: one group, and one more gift to come")
        void asManyGroupsAsBothAllow() {
            OfferEngine.Result result = run(List.of(shampoo(0, "2", "60"), conditioner(1, "1", "40")),
                    shampooGetsConditioner());

            // One group: one shampoo and the conditioner, 40 off 100 - the other shampoo is not taken off.
            assertEquals(d("40.00"), result.discount());
            assertEquals(d("1.000"), result.forLine(0).orElseThrow().quantity());
            assertEquals(d("1.000"), result.hints().get(0).missing());
        }
    }

    @Nested
    @DisplayName("the order (ق-ع٥)")
    class Order {

        @Test
        @DisplayName("a buy-and-get takes the line before a percentage, even one that would give more")
        void buyGetBeforeAPercentage() {
            Offer twoPlusOne = buyGet(1, "2", "1", "100", OfferTarget.item(11));
            Offer half = offer(2, OfferKind.PERCENT, "50", null, OfferTarget.item(11));
            OfferEngine.Result result = run(List.of(soap(0, "3", "40")), twoPlusOne, half);

            assertEquals(1, result.forLine(0).orElseThrow().offer().id());
            assertEquals(d("40.00"), result.discount());
        }

        @Test
        @DisplayName("a line the quantity offer does not complete is still the percentage's")
        void whatAQuantityOfferLeavesAPriceOfferTakes() {
            Offer threeFor100 = quantityPrice(1, "3", "100", null, OfferTarget.item(11));
            Offer tenPercent = offer(2, OfferKind.PERCENT, "10", null, OfferTarget.item(11));
            OfferEngine.Result result = run(List.of(soap(0, "2", "40")), threeFor100, tenPercent);

            assertEquals(2, result.forLine(0).orElseThrow().offer().id());
            assertEquals(d("8.00"), result.discount());
        }

        @Test
        @DisplayName("of two quantity offers the one giving more wins; a higher priority wins before that")
        void betweenTwoQuantityOffers() {
            Offer threeFor100 = quantityPrice(1, "3", "100", null, OfferTarget.item(11));
            Offer twoFor70 = quantityPrice(2, "2", "70", null, OfferTarget.item(11));
            List<OfferEngine.Line> three = List.of(soap(0, "3", "40"));

            assertEquals(1, run(three, threeFor100, twoFor70).forLine(0).orElseThrow().offer().id(), "20 beats 10");
            assertEquals(2, run(three, threeFor100, withPriority(twoFor70, 1)).forLine(0).orElseThrow().offer().id());
        }
    }

    @Nested
    @DisplayName("the limits (ق-ع١٠)")
    class Limits {

        @Test
        @DisplayName("a percentage limited to three units a customer gives 10% of three of five")
        void aPriceOfferLimitedPerInvoice() {
            Offer tenPercent = limited(offer(1, OfferKind.PERCENT, "10", null, OfferTarget.item(11)), "3", null);
            OfferEngine.Applied applied = run(List.of(soap(0, "5", "40")), tenPercent).forLine(0).orElseThrow();

            assertEquals(d("12.00"), applied.discount());
            assertEquals(d("3.000"), applied.quantity());
        }

        @Test
        @DisplayName("the limit is shared out line by line, in the lines' order")
        void aLimitAcrossLines() {
            Offer tenPercent = limited(offer(1, OfferKind.PERCENT, "10", null, OfferTarget.item(11)), "3", null);
            OfferEngine.Result result = run(List.of(soap(0, "2", "40"), soap(1, "2", "40")), tenPercent);

            assertEquals(d("8.00"), result.forLine(0).orElseThrow().discount());
            assertEquals(d("4.00"), result.forLine(1).orElseThrow().discount());
            assertEquals(d("1.000"), result.forLine(1).orElseThrow().quantity());
        }

        @Test
        @DisplayName("a quantity offer's limit counts groups: twice a customer is six of seven, then once is three")
        void aQuantityOfferLimitCountsGroups() {
            Offer twice = limited(quantityPrice(1, "3", "100", null, OfferTarget.item(11)), "2", null);
            Offer once = limited(quantityPrice(1, "3", "100", null, OfferTarget.item(11)), "1", null);
            List<OfferEngine.Line> seven = List.of(soap(0, "7", "40"));

            assertEquals(d("40.00"), run(seven, twice).discount());
            assertEquals(d("20.00"), run(seven, once).discount());
            assertEquals(d("3.000"), run(seven, once).forLine(0).orElseThrow().quantity());
        }

        @Test
        @DisplayName("the global limit is what the other documents left: one group left gives one group")
        void theGlobalLimitIsWhatIsLeft() {
            Offer firstHundred = limited(quantityPrice(1, "3", "100", null, OfferTarget.item(11)), null, "100");
            List<OfferEngine.Line> seven = List.of(soap(0, "7", "40"));
            OfferEngine.Result oneLeft = OfferEngine.apply(seven,
                    new OfferEngine.Context(DAY, 1, Set.of(), Map.of(1, d("1"))), List.of(firstHundred));
            OfferEngine.Result noneLeft = OfferEngine.apply(seven,
                    new OfferEngine.Context(DAY, 1, Set.of(), Map.of(1, BigDecimal.ZERO)), List.of(firstHundred));
            OfferEngine.Result halfLeft = OfferEngine.apply(seven,
                    new OfferEngine.Context(DAY, 1, Set.of(), Map.of(1, d("1.5"))), List.of(firstHundred));

            assertEquals(d("20.00"), oneLeft.discount());
            assertTrue(noneLeft.isEmpty());
            assertTrue(noneLeft.hints().isEmpty(), "no hint to complete what cannot be given");
            assertEquals(d("20.00"), halfLeft.discount(), "a group is whole: one and a half left is one");
        }

        @Test
        @DisplayName("the smaller of the two limits holds")
        void theSmallerLimitHolds() {
            Offer both = limited(offer(1, OfferKind.PERCENT, "10", null, OfferTarget.item(11)), "4", "100");
            OfferEngine.Result result = OfferEngine.apply(List.of(soap(0, "5", "40")),
                    new OfferEngine.Context(DAY, 1, Set.of(), Map.of(1, d("2"))), List.of(both));

            assertEquals(d("8.00"), result.discount(), "two left overall is less than four a customer");
        }
    }

    @Test
    @DisplayName("a phase B line records its whole count: three pieces are three, a carton is twelve base units")
    void aPriceOfferCoversTheWholeLine() {
        Offer fivePerPiece = offer(1, OfferKind.AMOUNT, "5", null, OfferTarget.item(11));
        OfferEngine.Result result = run(List.of(soap(0, "3", "40"), soapCartons(1, "1", "450")), fivePerPiece);

        assertEquals(d("3.000"), result.forLine(0).orElseThrow().quantity());
        assertEquals(d("12.000"), result.forLine(1).orElseThrow().quantity());
        assertEquals(d("60.00"), result.forLine(1).orElseThrow().discount(), "twelve pieces at 5 - phase B's figure");
    }
}
