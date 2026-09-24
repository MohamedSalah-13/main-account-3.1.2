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
