package com.hamza.account.features.itemreports;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * The Pareto arithmetic over a list of items - pure, so each rule has a test.
 *
 * <p>The rules, decided in {@code docs/reports-plan.md} §13.2:</p>
 * <ul>
 *     <li>The items are ranked by the figure, largest first; equal figures in name order, then id.</li>
 *     <li>A share is the item's part of the <b>positive</b> total. An item that sold nothing net or
 *     lost money has no share and no class: dividing it into a total it takes away from would give
 *     the items above it more than a hundred percent between them.</li>
 *     <li>The class is read off the cumulative share <b>before</b> the item: under 80% is A, under 95%
 *     is B, the rest C. So the first item is always A, however much of the total it holds alone.</li>
 * </ul>
 */
public final class ParetoRanking {

    public static final double A_LIMIT = 80;
    public static final double B_LIMIT = 95;

    public enum ParetoClass {
        A("itemreport.pareto.class.a"),
        B("itemreport.pareto.class.b"),
        C("itemreport.pareto.class.c"),
        /** A figure of zero or less: nothing to rank it by. */
        NONE("itemreport.pareto.class.none");

        private final String labelKey;

        ParetoClass(String labelKey) {
            this.labelKey = labelKey;
        }

        public String labelKey() {
            return labelKey;
        }
    }

    /**
     * One ranked item.
     *
     * @param share      the item's percentage of the positive total, or {@code null} for none
     * @param cumulative the running percentage up to and including this item, or {@code null}
     */
    public record Ranked(int rank, ItemSalesFact fact, double value, Double share, Double cumulative,
                         ParetoClass paretoClass) {
    }

    /** What the ranking comes to: how few items make the A class, and how much they carry. */
    public record Summary(int ranked, int classA, double classAShare, double positiveTotal) {

        /** Class A's items as a percentage of the items with something to rank, or zero for none. */
        public double classAItemsPercent() {
            return ranked == 0 ? 0 : classA * 100.0 / ranked;
        }
    }

    private ParetoRanking() {
    }

    public static List<Ranked> rank(List<ItemSalesFact> facts, ToDoubleFunction<ItemSalesFact> value) {
        List<ItemSalesFact> ordered = facts.stream()
                .sorted(Comparator.comparingDouble(value).reversed()
                        .thenComparing(ItemSalesFact::name, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(ItemSalesFact::itemId))
                .toList();
        double positiveTotal = ordered.stream().mapToDouble(value).filter(figure -> figure > 0).sum();
        List<Ranked> ranked = new ArrayList<>(ordered.size());
        double cumulative = 0;
        int rank = 0;
        for (ItemSalesFact fact : ordered) {
            double figure = value.applyAsDouble(fact);
            rank++;
            if (figure <= 0 || positiveTotal <= 0) {
                ranked.add(new Ranked(rank, fact, figure, null, null, ParetoClass.NONE));
                continue;
            }
            double share = figure * 100 / positiveTotal;
            ParetoClass paretoClass = cumulative < A_LIMIT ? ParetoClass.A
                    : cumulative < B_LIMIT ? ParetoClass.B : ParetoClass.C;
            cumulative += share;
            ranked.add(new Ranked(rank, fact, figure, share, cumulative, paretoClass));
        }
        return List.copyOf(ranked);
    }

    public static Summary summary(List<Ranked> ranked) {
        int withShare = 0;
        int classA = 0;
        double classAShare = 0;
        double positiveTotal = 0;
        for (Ranked row : ranked) {
            if (row.share() == null) {
                continue;
            }
            withShare++;
            positiveTotal += row.value();
            if (row.paretoClass() == ParetoClass.A) {
                classA++;
                classAShare += row.share();
            }
        }
        return new Summary(withShare, classA, classAShare, positiveTotal);
    }
}
