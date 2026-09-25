package com.hamza.account.features.offers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ق-ع٩: the items an offer sells below their cost, asked once when it goes live. */
class OfferCostCheckTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    /** Rice costs 30 and sells at 40 retail, 34 wholesale; soap costs 10 and sells at 40, 36, 32. */
    private static final List<OfferCostCheck.Candidate> CANDIDATES = List.of(
            new OfferCostCheck.Candidate(20, "أرز", 1, "كيس", 7, 3, BigDecimal.ONE, d("30"),
                    List.of(d("40"), d("0"), d("34"))),
            new OfferCostCheck.Candidate(11, "صابون", 1, "قطعة", 5, 9, BigDecimal.ONE, d("10"),
                    List.of(d("40"), d("36"), d("32"))));

    private static Offer percent(String value, Set<Integer> tiers) {
        return new Offer(1, "خصم", OfferKind.PERCENT, OfferStatus.ACTIVE, LocalDate.of(2026, 9, 24), null, null, 0,
                d(value), null, null, null, null, List.of(OfferTarget.everything()), tiers, null);
    }

    @Test
    @DisplayName("20% leaves rice at 32 retail and 27.20 wholesale - listed at its worst tier")
    void listedAtItsWorstTier() {
        List<OfferCostCheck.BelowCost> below = OfferCostCheck.below(percent("20", Set.of()), CANDIDATES, Set.of(1, 2, 3));
        assertEquals(List.of(new OfferCostCheck.BelowCost(20, "أرز", "كيس", 3, d("34"), d("27.20"), d("30.00"))), below);
    }

    @Test
    @DisplayName("only the tiers the offer reaches, and a tier with no price is not a price of zero")
    void onlyItsTiers() {
        // retail alone: rice at 32 is above its 30
        assertTrue(OfferCostCheck.below(percent("20", Set.of(1)), CANDIDATES, Set.of(1, 2, 3)).isEmpty());
        // tier 2 prices no rice at all, so nothing is judged there
        assertTrue(OfferCostCheck.below(percent("50", Set.of(2)), CANDIDATES, Set.of(1, 2, 3)).isEmpty());
    }

    @Test
    @DisplayName("3 for 100 leaves a soap at 33.33 against a cost of 35 - judged on a whole group")
    void aQuantityOfferIsJudgedOnAGroup() {
        List<OfferCostCheck.Candidate> soap = List.of(new OfferCostCheck.Candidate(11, "صابون", 1, "قطعة", 5, 9,
                BigDecimal.ONE, d("35"), List.of(d("40"), d("0"), d("0"))));
        Offer threeFor100 = new Offer(1, "3 بـ 100", OfferKind.QUANTITY_PRICE, OfferStatus.ACTIVE,
                LocalDate.of(2026, 9, 24), null, null, 0, null, null, d("100"), null, d("3"), null, null, null, null,
                null, List.of(OfferTarget.item(11)), Set.of(), null);
        assertEquals(List.of(new OfferCostCheck.BelowCost(11, "صابون", "قطعة", 1, d("40"), d("33.33"), d("35.00"))),
                OfferCostCheck.below(threeFor100, soap, Set.of(1)));
    }

    @Test
    @DisplayName("example 4 at a cost of 45 and 30: the shampoo nets 36 and the gift 24 - both listed, the gift once")
    void aGiftIsJudgedBesideWhatEarnsIt() {
        List<OfferCostCheck.Candidate> shampoo = List.of(new OfferCostCheck.Candidate(30, "شامبو", 1, "قطعة", 8, 4,
                BigDecimal.ONE, d("45"), List.of(d("60"), d("0"), d("0"))));
        OfferCostCheck.Candidate conditioner = new OfferCostCheck.Candidate(31, "بلسم", 1, "قطعة", 8, 4,
                BigDecimal.ONE, d("30"), List.of(d("40"), d("0"), d("0")));
        Offer gift = new Offer(1, "بلسم هدية", OfferKind.BUY_GET, OfferStatus.ACTIVE, LocalDate.of(2026, 9, 24), null,
                null, 0, null, null, null, null, d("1"), d("1"), d("100"), null, null, null,
                List.of(OfferTarget.item(30), OfferTarget.reward(31)), Set.of(), null);

        List<OfferCostCheck.BelowCost> below = OfferCostCheck.below(gift, shampoo, conditioner, Set.of(1));
        assertEquals(List.of(
                new OfferCostCheck.BelowCost(31, "بلسم", "قطعة", 1, d("40"), d("24.00"), d("30.00")),
                new OfferCostCheck.BelowCost(30, "شامبو", "قطعة", 1, d("60"), d("36.00"), d("45.00"))), below);
    }

    @Test
    @DisplayName("a bundle at 130 of oil 80, two sugars 30 and rice 25: 35 shared by value - oil nets 63.03 against"
            + " 75, rice 19.70 against 22, sugar 23.64 a kilo above its 20")
    void aBundleIsJudgedWhole() {
        List<OfferCostCheck.Candidate> components = List.of(
                new OfferCostCheck.Candidate(40, "زيت", 1, "زجاجة", 7, 3, BigDecimal.ONE, d("75"),
                        List.of(d("80"), d("0"), d("0"))),
                new OfferCostCheck.Candidate(41, "سكر", 1, "كيلو", 7, 3, BigDecimal.ONE, d("20"),
                        List.of(d("30"), d("0"), d("0"))),
                new OfferCostCheck.Candidate(20, "أرز", 1, "كيس", 7, 3, BigDecimal.ONE, d("22"),
                        List.of(d("25"), d("0"), d("0"))));
        Offer bundle = new Offer(1, "طقم", OfferKind.BUNDLE, OfferStatus.ACTIVE, LocalDate.of(2026, 9, 24), null,
                null, 0, null, null, d("130"), null, null, null, null, null, null, null, null, null,
                List.of(OfferTarget.component(40, null, d("1")), OfferTarget.component(41, null, d("2")),
                        OfferTarget.component(20, null, d("1"))), Set.of(), null);

        assertEquals(List.of(
                new OfferCostCheck.BelowCost(20, "أرز", "كيس", 1, d("25"), d("19.70"), d("22.00")),
                new OfferCostCheck.BelowCost(40, "زيت", "زجاجة", 1, d("80"), d("63.03"), d("75.00"))),
                OfferCostCheck.below(bundle, components, Set.of(1, 2, 3)),
                "tiers 2 and 3 price no component, and are passed over");
        assertTrue(OfferCostCheck.below(bundle, components.subList(0, 2), Set.of(1)).isEmpty(),
                "a component missing from the catalogue judges nothing");
    }

    @Test
    @DisplayName("125 off from 500 is at worst a quarter off: rice at 34 wholesale nets 25.50 against 30")
    void anInvoiceOfferIsJudgedAsItsPercentage() {
        Offer quarter = new Offer(1, "فاتورة", OfferKind.INVOICE, OfferStatus.ACTIVE, LocalDate.of(2026, 9, 24), null,
                null, 0, null, d("125"), null, null, null, null, null, null, null, d("500"), null, null,
                List.of(OfferTarget.everything()), Set.of(), null);
        assertEquals(List.of(new OfferCostCheck.BelowCost(20, "أرز", "كيس", 3, d("34"), d("25.50"), d("30.00"))),
                OfferCostCheck.below(quarter, CANDIDATES, Set.of(1, 2, 3)));
    }

    @Test
    @DisplayName("an offer that does not reach an item says nothing of it")
    void notReached() {
        Offer soapOnly = new Offer(1, "صابون", OfferKind.PRICE, OfferStatus.ACTIVE, LocalDate.of(2026, 9, 24), null,
                null, 0, null, null, d("9"), null, null, List.of(OfferTarget.item(11)), Set.of(), null);
        List<OfferCostCheck.BelowCost> below = OfferCostCheck.below(soapOnly, CANDIDATES, Set.of(1, 2, 3));
        assertEquals(1, below.size());
        assertEquals(11, below.get(0).itemId());
        assertEquals(d("9.00"), below.get(0).net());
    }
}
