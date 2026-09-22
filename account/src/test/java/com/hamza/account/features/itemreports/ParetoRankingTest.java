package com.hamza.account.features.itemreports;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ParetoRankingTest {

    /**
     * Net 500, 300, 150, 40, 10, a return of -20 and one of 0: a positive total of 1,000. Before each
     * item the running share is 0, 50, 80, 95, 99 - so A, A, B, C, C, and the last two have no share.
     */
    private static final List<ItemSalesFact> SEVEN = List.of(
            fact(1, "juice", 150), fact(2, "rice", 500), fact(3, "oil", -20), fact(4, "tea", 10),
            fact(5, "sugar", 300), fact(6, "salt", 40), fact(7, "soap", 0));

    @Test
    void theItemsAreRankedLargestFirstWithSharesOfThePositiveTotal() {
        List<ParetoRanking.Ranked> ranked = ParetoRanking.rank(SEVEN, ItemSalesFact::net);

        assertEquals(List.of("rice", "sugar", "juice", "salt", "tea", "soap", "oil"),
                ranked.stream().map(row -> row.fact().name()).toList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), ranked.stream().map(ParetoRanking.Ranked::rank).toList());
        assertEquals(50.0, ranked.get(0).share(), 1e-9);
        assertEquals(80.0, ranked.get(1).cumulative(), 1e-9);
        assertEquals(100.0, ranked.get(4).cumulative(), 1e-9);
    }

    /** The class is read before the item, so the item that crosses 80% is still A. */
    @Test
    void theClassIsReadOffTheShareBeforeTheItem() {
        List<ParetoRanking.ParetoClass> classes = ParetoRanking.rank(SEVEN, ItemSalesFact::net).stream()
                .map(ParetoRanking.Ranked::paretoClass).toList();

        assertEquals(List.of(ParetoRanking.ParetoClass.A, ParetoRanking.ParetoClass.A, ParetoRanking.ParetoClass.B,
                ParetoRanking.ParetoClass.C, ParetoRanking.ParetoClass.C, ParetoRanking.ParetoClass.NONE,
                ParetoRanking.ParetoClass.NONE), classes);
    }

    @Test
    void anItemAloneAboveEightyPercentIsStillA() {
        List<ParetoRanking.Ranked> ranked = ParetoRanking.rank(List.of(fact(1, "a", 900), fact(2, "b", 100)),
                ItemSalesFact::net);
        assertEquals(ParetoRanking.ParetoClass.A, ranked.get(0).paretoClass());
        assertEquals(ParetoRanking.ParetoClass.B, ranked.get(1).paretoClass(), "90% came before it");
    }

    /** Nothing sold net, or a loss, takes no share: it would give the others more than all of it. */
    @Test
    void zeroAndNegativeFiguresHaveNoShareAndNoClass() {
        ParetoRanking.Ranked oil = ParetoRanking.rank(SEVEN, ItemSalesFact::net).getLast();
        assertNull(oil.share());
        assertNull(oil.cumulative());
        assertEquals(ParetoRanking.ParetoClass.NONE, oil.paretoClass());
    }

    @Test
    void equalFiguresComeInNameOrderThenById() {
        List<ParetoRanking.Ranked> ranked = ParetoRanking.rank(
                List.of(fact(9, "b", 100), fact(3, "a", 100), fact(1, "a", 100)), ItemSalesFact::net);
        assertEquals(List.of(1, 3, 9), ranked.stream().map(row -> row.fact().itemId()).toList());
    }

    @Test
    void theSummaryCountsClassAAndWhatItCarries() {
        ParetoRanking.Summary summary = ParetoRanking.summary(ParetoRanking.rank(SEVEN, ItemSalesFact::net));
        assertEquals(5, summary.ranked());
        assertEquals(2, summary.classA());
        assertEquals(80.0, summary.classAShare(), 1e-9);
        assertEquals(40.0, summary.classAItemsPercent(), 1e-9);
        assertEquals(1000.0, summary.positiveTotal(), 1e-9);
    }

    @Test
    void theMarginIsRankedByItsOwnFigure() {
        List<ParetoRanking.Ranked> ranked = ParetoRanking.rank(
                List.of(new ItemSalesFact(1, "cheap", null, "p", 1, 1000, 950),
                        new ItemSalesFact(2, "dear", null, "p", 1, 400, 100)), ItemSalesFact::margin);
        assertEquals("dear", ranked.getFirst().fact().name());
        assertEquals(300.0, ranked.getFirst().value(), 1e-9);
    }

    @Test
    void anEmptyPeriodRanksNothing() {
        assertEquals(0, ParetoRanking.rank(List.of(), ItemSalesFact::net).size());
        assertEquals(0.0, ParetoRanking.summary(List.of()).classAItemsPercent(), 1e-9);
    }

    private static ItemSalesFact fact(int id, String name, double net) {
        return new ItemSalesFact(id, name, "g", "piece", 1, net, 0);
    }
}
