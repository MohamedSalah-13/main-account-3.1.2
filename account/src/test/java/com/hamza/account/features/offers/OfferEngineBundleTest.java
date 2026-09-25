package com.hamza.account.features.offers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hamza.account.features.offers.OfferEngineTest.DAY;
import static com.hamza.account.features.offers.OfferEngineTest.DETERGENTS;
import static com.hamza.account.features.offers.OfferEngineTest.PIECE;
import static com.hamza.account.features.offers.OfferEngineTest.d;
import static com.hamza.account.features.offers.OfferEngineTest.offer;
import static com.hamza.account.features.offers.OfferEngineTest.rice;
import static com.hamza.account.features.offers.OfferEngineTest.run;
import static com.hamza.account.features.offers.OfferEngineTest.soap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** docs/pricing-and-offers-plan.md §5 examples 5 and 6, worked by hand, and the rest of phase D (V87). */
class OfferEngineBundleTest {

    static final int OIL = 40;
    static final int SUGAR = 41;
    static final int SUGAR_SACK = 7;

    static OfferEngine.Line oil(int index, String quantity, String price) {
        return new OfferEngine.Line(index, OIL, PIECE, 7, 3, BigDecimal.ONE, d(quantity), d(price));
    }

    static OfferEngine.Line sugar(int index, String quantity, String price) {
        return new OfferEngine.Line(index, SUGAR, PIECE, 7, 3, BigDecimal.ONE, d(quantity), d(price));
    }

    /** A sack of twelve kilos of sugar. */
    static OfferEngine.Line sugarSacks(int index, String quantity, String price) {
        return new OfferEngine.Line(index, SUGAR, SUGAR_SACK, 7, 3, d("12"), d(quantity), d(price));
    }

    /** "The Ramadan bundle": one oil, two sugar, one rice (item 20), at {@code price}. */
    static Offer ramadan(int id, String price) {
        return bundle(id, price, OfferTarget.component(OIL, null, d("1")),
                OfferTarget.component(SUGAR, null, d("2")), OfferTarget.component(20, null, d("1")));
    }

    static Offer bundle(int id, String price, OfferTarget... components) {
        return new Offer(id, "طقم " + id, OfferKind.BUNDLE, OfferStatus.ACTIVE, DAY.minusDays(10), null, null, 0,
                null, null, d(price), null, null, null, null, null, null, null, "62" + id, null, List.of(components),
                Set.of(), null);
    }

    /** "{@code percent}% from {@code threshold}". */
    static Offer invoicePercent(int id, String threshold, String percent, OfferTarget... targets) {
        return new Offer(id, "فاتورة " + id, OfferKind.INVOICE, OfferStatus.ACTIVE, DAY.minusDays(10), null, null, 0,
                d(percent), null, null, null, null, null, null, null, null, d(threshold), null, null,
                List.of(targets), Set.of(), null);
    }

    /** "{@code amount} off from {@code threshold}". */
    static Offer invoiceAmount(int id, String threshold, String amount, OfferTarget... targets) {
        return new Offer(id, "فاتورة " + id, OfferKind.INVOICE, OfferStatus.ACTIVE, DAY.minusDays(10), null, null, 0,
                null, d(amount), null, null, null, null, null, null, null, d(threshold), null, null,
                List.of(targets), Set.of(), null);
    }

    static Offer limited(Offer offer, String total) {
        return new Offer(offer.id(), offer.name(), offer.kind(), offer.status(), offer.startsOn(), offer.endsOn(),
                offer.weekdays(), offer.priority(), offer.percent(), offer.amount(), offer.offerPrice(), offer.unitId(),
                offer.buyQuantity(), offer.getQuantity(), offer.getPercent(), offer.maxPerInvoice(), d(total),
                offer.threshold(), offer.barcode(), offer.notes(), offer.targets(), offer.priceTierIds(),
                offer.version());
    }

    @Nested
    @DisplayName("example 5: the Ramadan bundle at 150")
    class Bundle {

        @Test
        @DisplayName("oil 80, sugar 2 x 30, rice 25 come to 165: 15 off, 7.28 / 5.45 / 2.27 - the piastre on the oil")
        void exampleFive() {
            OfferEngine.Result result = run(List.of(oil(0, "1", "80"), sugar(1, "2", "30"), rice(2, "1", "25")),
                    ramadan(1, "150"));

            assertEquals(d("7.28"), result.forLine(0).orElseThrow().discount(), "80 / 165 x 15 = 7.2727, and the piastre");
            assertEquals(d("5.45"), result.forLine(1).orElseThrow().discount(), "60 / 165 x 15 = 5.4545");
            assertEquals(d("2.27"), result.forLine(2).orElseThrow().discount(), "25 / 165 x 15 = 2.2727");
            assertEquals(d("15.00"), result.discount());
            assertEquals(d("2.000"), result.forLine(1).orElseThrow().quantity(), "the two sugars the bundle holds");
            assertTrue(result.hints().isEmpty(), "one bundle, and nothing towards a second");
        }

        @Test
        @DisplayName("more than one bundle holds: one bundle, the whole of each line taken, and the next asked for")
        void extraUnitsAndTheNextBundle() {
            OfferEngine.Result result = run(List.of(oil(0, "2", "80"), sugar(1, "3", "30"), rice(2, "1", "25")),
                    ramadan(1, "150"));

            assertEquals(d("15.00"), result.discount(), "one bundle: rice holds one");
            assertEquals(d("1.000"), result.forLine(0).orElseThrow().quantity(), "one of the two oils is in it");
            assertEquals(d("7.28"), result.forLine(0).orElseThrow().discount());
            // A second bundle would need four sugars and two rice: the oil is there for it already.
            assertEquals(List.of(OfferEngine.HintKind.BUNDLE, OfferEngine.HintKind.BUNDLE),
                    result.hints().stream().map(OfferEngine.Hint::kind).toList());
            assertEquals(d("1.000"), result.hints().get(0).missing(), "a fourth sugar");
            assertEquals(SUGAR, result.hints().get(0).itemId());
            assertEquals(20, result.hints().get(1).itemId(), "and a second rice");
        }

        @Test
        @DisplayName("an oil alone is no bundle, and says what is missing")
        void aComponentAlone() {
            OfferEngine.Result result = run(List.of(oil(0, "1", "80")), ramadan(1, "150"));

            assertTrue(result.isEmpty());
            assertEquals(2, result.hints().size(), "two sugars and a rice");
            assertEquals(d("2.000"), result.hints().get(0).missing());
        }

        @Test
        @DisplayName("a wholesale customer whose components already come under the bundle's price takes nothing")
        void neverRaisesAPrice() {
            OfferEngine.Result result = run(List.of(oil(0, "1", "70"), sugar(1, "2", "25"), rice(2, "1", "20")),
                    ramadan(1, "150"));

            assertTrue(result.isEmpty(), "70 + 50 + 20 = 140, already under 150");
            assertTrue(result.hints().isEmpty());
        }

        @Test
        @DisplayName("a component counted in base units takes them from a sack of twelve")
        void aSackIsItsKilos() {
            OfferEngine.Result result = run(List.of(oil(0, "1", "80"), sugarSacks(1, "1", "360"), rice(2, "1", "25")),
                    ramadan(1, "150"));

            // Two kilos out of the sack at 360 / 12 = 30: the same 165, the same 15.
            assertEquals(d("15.00"), result.discount());
            assertEquals(d("5.45"), result.forLine(1).orElseThrow().discount());
            assertEquals(d("2.000"), result.forLine(1).orElseThrow().quantity(), "two of the sack's kilos");
        }

        @Test
        @DisplayName("the bundle is judged first: a percentage on the rice finds its line taken (ق-ع٥)")
        void bundlesComeFirst() {
            Offer riceOff = offer(9, OfferKind.PERCENT, "50", null, OfferTarget.item(20));
            OfferEngine.Result result = run(List.of(oil(0, "1", "80"), sugar(1, "2", "30"), rice(2, "1", "25")),
                    riceOff, ramadan(1, "150"));

            assertEquals(1, result.forLine(2).orElseThrow().offer().id(), "the bundle holds the rice");
            assertEquals(d("15.00"), result.discount());
        }

        @Test
        @DisplayName("a global limit used up gives no bundle, and asks for none")
        void theLimit() {
            Offer used = limited(ramadan(1, "150"), "3");
            OfferEngine.Result result = OfferEngine.apply(
                    List.of(oil(0, "1", "80"), sugar(1, "2", "30"), rice(2, "1", "25")),
                    new OfferEngine.Context(DAY, 1, Set.of(), Map.of(1, BigDecimal.ZERO)), List.of(used));

            assertTrue(result.isEmpty());
            assertTrue(result.hints().isEmpty());
        }

        @Test
        @DisplayName("a bundle's group is its components' units, which the global limit counts in")
        void groupSize() {
            assertEquals(0, d("4").compareTo(ramadan(1, "150").groupSize()), "one oil, two sugars, one rice");
        }
    }

    @Nested
    @DisplayName("example 6: 5% from 1,000")
    class Invoice {

        @Test
        @DisplayName("1,200 of lines reach it: 60 shared by value - 20 and 40 - and each line a share of one time")
        void exampleSix() {
            OfferEngine.Result result = run(List.of(soap(0, "10", "40"), rice(1, "8", "100")),
                    invoicePercent(1, "1000", "5", OfferTarget.everything()));

            assertEquals(d("20.00"), result.forLine(0).orElseThrow().discount());
            assertEquals(d("40.00"), result.forLine(1).orElseThrow().discount());
            assertEquals(d("0.333"), result.forLine(0).orElseThrow().quantity());
            assertEquals(d("0.667"), result.forLine(1).orElseThrow().quantity(), "the shares come to one time");
        }

        @Test
        @DisplayName("the threshold counts a line at its net after its own offer, and the percentage goes on the rest")
        void afterTheLinesOwnOffers() {
            Offer tenOnSoap = offer(2, OfferKind.PERCENT, "10", null, OfferTarget.subGroup(DETERGENTS));
            // Soap 400 less its 40 is 360, and rice 800: 1,160 reaches 1,000. The soap keeps its own offer - one a
            // line - and the 5% is the rice's alone: 40.
            OfferEngine.Result result = run(List.of(soap(0, "10", "40"), rice(1, "8", "100")),
                    tenOnSoap, invoicePercent(1, "1000", "5", OfferTarget.everything()));

            assertEquals(2, result.forLine(0).orElseThrow().offer().id());
            assertEquals(d("40.00"), result.forLine(0).orElseThrow().discount());
            assertEquals(1, result.forLine(1).orElseThrow().offer().id());
            assertEquals(d("40.00"), result.forLine(1).orElseThrow().discount());
            assertEquals(d("1.000"), result.forLine(1).orElseThrow().quantity());
        }

        @Test
        @DisplayName("under the threshold nothing - past half of it, what is left to spend is said")
        void theSpendHint() {
            Offer fivePercent = invoicePercent(1, "1000", "5", OfferTarget.everything());

            OfferEngine.Result eight = run(List.of(rice(0, "8", "100")), fivePercent);
            assertTrue(eight.isEmpty());
            assertEquals(1, eight.hints().size());
            assertEquals(OfferEngine.HintKind.SPEND, eight.hints().get(0).kind());
            assertEquals(d("200.00"), eight.hints().get(0).missing());

            assertTrue(run(List.of(rice(0, "4", "100")), fivePercent).hints().isEmpty(), "400 is not half way");
        }

        @Test
        @DisplayName("an amount off is shared by value, and never more than the free lines are worth")
        void anAmount() {
            OfferEngine.Result result = run(List.of(soap(0, "5", "40"), rice(1, "4", "100")),
                    invoiceAmount(1, "500", "60", OfferTarget.everything()));

            assertEquals(d("60.00"), result.discount());
            assertEquals(d("20.00"), result.forLine(0).orElseThrow().discount(), "200 / 600 x 60");
            assertEquals(d("40.00"), result.forLine(1).orElseThrow().discount(), "400 / 600 x 60");

            // Rice 800 less its own 10% reaches 500 with the soap; the soap alone is free, and worth 40.
            Offer tenOnRice = offer(2, OfferKind.PERCENT, "10", null, OfferTarget.item(20));
            OfferEngine.Result capped = run(List.of(soap(0, "1", "40"), rice(1, "8", "100")), tenOnRice,
                    invoiceAmount(1, "500", "60", OfferTarget.everything()));
            assertEquals(d("40.00"), capped.forLine(0).orElseThrow().discount(), "the whole of the soap, not 60");
        }

        @Test
        @DisplayName("every reached line taken by another offer leaves the invoice offer nothing to give")
        void nothingFree() {
            Offer everythingTen = offer(2, OfferKind.PERCENT, "10", null, OfferTarget.everything());
            OfferEngine.Result result = run(List.of(rice(0, "8", "100")), everythingTen,
                    invoiceAmount(1, "500", "50", OfferTarget.everything()));

            assertEquals(2, result.forLine(0).orElseThrow().offer().id());
            assertEquals(d("80.00"), result.discount(), "the percentage's alone");
        }

        @Test
        @DisplayName("what the targets leave out counts toward nothing: 'everything but the soap'")
        void exclusions() {
            Offer butSoap = invoicePercent(1, "1000", "5", OfferTarget.everything(), OfferTarget.item(11).except());
            OfferEngine.Result result = run(List.of(soap(0, "10", "40"), rice(1, "8", "100")), butSoap);

            assertTrue(result.forLine(0).isEmpty());
            assertTrue(result.forLine(1).isEmpty(), "800 of rice does not reach 1,000 on its own");
        }

        @Test
        @DisplayName("three equal lines share one time as 0.334, 0.333 and 0.333")
        void sharesOfOne() {
            OfferEngine.Result result = run(List.of(rice(0, "1", "100"), rice(1, "1", "100"), rice(2, "1", "100")),
                    invoicePercent(1, "300", "10", OfferTarget.everything()));

            assertEquals(d("0.334"), result.forLine(0).orElseThrow().quantity());
            assertEquals(d("0.333"), result.forLine(1).orElseThrow().quantity());
            assertEquals(d("0.333"), result.forLine(2).orElseThrow().quantity());
            assertEquals(d("30.00"), result.discount());
        }

        @Test
        @DisplayName("a global limit used up gives nothing")
        void theLimit() {
            Offer used = limited(invoicePercent(1, "1000", "5", OfferTarget.everything()), "10");
            OfferEngine.Result result = OfferEngine.apply(List.of(rice(0, "12", "100")),
                    new OfferEngine.Context(DAY, 1, Set.of(), Map.of(1, d("0.4"))), List.of(used));

            assertTrue(result.isEmpty());
        }
    }
}
