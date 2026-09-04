package com.hamza.account.features.itemreports;

import com.hamza.account.features.itemreports.PriceAnomalyReport.Fault;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each report says, over facts built by hand.
 * <p>
 * This is what {@link CatalogFact} was separated for: the reading is one query and the
 * reporting is a pure function, so every rule below - which items are idle, which balance
 * is an error rather than a shortage, which price is a loss - is exercised without a
 * database, a toolkit or a fixture to clean up afterwards.
 */
class ItemReportsTest {

    private static CatalogFact fact(int id, String name, double buy, double sell,
                                    double minimum, double balance) {
        return new CatalogFact(id, "BC" + id, name, 1, "مشروبات", 2, "عصائر", "قطعة",
                buy, sell, minimum, balance, true, false, null);
    }

    private static CatalogFact movedOn(CatalogFact fact, LocalDate when) {
        return new CatalogFact(fact.id(), fact.barcode(), fact.name(), fact.mainGroupId(),
                fact.mainGroupName(), fact.subGroupId(), fact.subGroupName(), fact.unitName(),
                fact.buyPrice(), fact.sellPrice(), fact.minimum(), fact.balance(),
                fact.active(), fact.tracksExpiry(), when);
    }

    private static CatalogFact ungrouped(CatalogFact fact) {
        return new CatalogFact(fact.id(), fact.barcode(), fact.name(), null, null, null, null,
                fact.unitName(), fact.buyPrice(), fact.sellPrice(), fact.minimum(), fact.balance(),
                fact.active(), fact.tracksExpiry(), fact.lastMovement());
    }

    @Nested
    @DisplayName("unused items")
    class Unused {

        @Test
        @DisplayName("an item that has never moved is idle whether or not a date was given")
        void neverMovedIsAlwaysIdle() {
            CatalogFact never = fact(1, "أ", 10, 15, 0, 5);

            assertEquals(1, UnusedItemsReport.build(List.of(never), null).itemRowCount());
            assertEquals(1, UnusedItemsReport.build(List.of(never), LocalDate.of(2026, 1, 1)).itemRowCount());
        }

        @Test
        @DisplayName("without a date the report is only about what has never moved at all")
        void withoutADateOnlyTheUntouched() {
            CatalogFact sold = movedOn(fact(1, "أ", 10, 15, 0, 5), LocalDate.of(2020, 1, 1));

            assertEquals(0, UnusedItemsReport.build(List.of(sold), null).itemRowCount(),
                    "an item bought years ago has still been handled; without a date it is not idle");
        }

        @Test
        @DisplayName("with a date it is about what has not moved since - the boundary date itself counts as moved")
        void sinceADateIsExclusiveOfTheDayItself() {
            LocalDate since = LocalDate.of(2026, 1, 1);
            CatalogFact onTheDay = movedOn(fact(1, "أ", 10, 15, 0, 5), since);
            CatalogFact theDayBefore = movedOn(fact(2, "ب", 10, 15, 0, 5), since.minusDays(1));

            assertEquals(0, UnusedItemsReport.build(List.of(onTheDay), since).itemRowCount());
            assertEquals(1, UnusedItemsReport.build(List.of(theDayBefore), since).itemRowCount());
        }

        @Test
        @DisplayName("the most valuable idle item is first - that is what the report is read for")
        void orderedByMoneyStandingStill() {
            CatalogFact cheap = fact(1, "رخيص", 1, 2, 0, 10);
            CatalogFact dear = fact(2, "غالي", 100, 150, 0, 10);

            ItemReportResult result = UnusedItemsReport.build(List.of(cheap, dear), null);

            assertEquals(2, result.rows().getFirst().itemId());
        }

        @Test
        void totalsTheCostOfWhatIsStandingStill() {
            ItemReportResult result = UnusedItemsReport.build(
                    List.of(fact(1, "أ", 10, 15, 0, 3), fact(2, "ب", 20, 30, 0, 2)), null);

            assertTrue(result.totals().stream()
                    .anyMatch(total -> total.value().contains("70")), "30 + 40 at cost");
        }
    }

    @Nested
    @DisplayName("stock levels")
    class Levels {

        @Test
        @DisplayName("a negative balance ranks above being out of stock, which ranks above being low")
        void orderedBySeverity() {
            CatalogFact low = fact(1, "منخفض", 10, 15, 5, 3);
            CatalogFact out = fact(2, "منتهي", 10, 15, 0, 0);
            CatalogFact negative = fact(3, "سالب", 10, 15, 0, -2);

            ItemReportResult result = StockLevelReport.build(List.of(low, out, negative));

            assertEquals(List.of(3, 2, 1), result.rows().stream().map(ItemReportRow::itemId).toList());
        }

        @Test
        @DisplayName("an item with no minimum set is not reported as below it")
        void aMinimumOfZeroIsNotAMinimum() {
            assertEquals(0, StockLevelReport.build(List.of(fact(1, "أ", 10, 15, 0, 7))).itemRowCount());
        }

        @Test
        @DisplayName("exactly at the minimum counts as reached, which is the boundary that is easy to lose")
        void exactlyAtTheMinimumCounts() {
            assertEquals(1, StockLevelReport.build(List.of(fact(1, "أ", 10, 15, 5, 5))).itemRowCount());
            assertEquals(0, StockLevelReport.build(List.of(fact(2, "ب", 10, 15, 5, 6))).itemRowCount());
        }

        @Test
        @DisplayName("the shortfall says what to buy, and covers the whole gap on a negative balance")
        void shortfallCoversTheWholeGap() {
            assertEquals(7, StockLevelReport.shortfall(fact(1, "أ", 10, 15, 5, -2)));
            assertEquals(2, StockLevelReport.shortfall(fact(2, "ب", 10, 15, 5, 3)));
            assertEquals(0, StockLevelReport.shortfall(fact(3, "ج", 10, 15, 0, 0)),
                    "no minimum set means there is no shortfall to state");
        }
    }

    @Nested
    @DisplayName("price anomalies")
    class Anomalies {

        @Test
        @DisplayName("a healthy item is not reported")
        void healthyItemsAreSilent() {
            assertNull(PriceAnomalyReport.faultOf(fact(1, "أ", 10, 15, 0, 5)));
        }

        @Test
        @DisplayName("no sale price outranks no margin - the item would ring up free")
        void theWorstFaultWins() {
            assertEquals(Fault.NO_SELL_PRICE, PriceAnomalyReport.faultOf(fact(1, "أ", 0, 0, 0, 5)));
            assertEquals(Fault.NO_SELL_PRICE, PriceAnomalyReport.faultOf(fact(2, "ب", 10, 0, 0, 5)));
        }

        @Test
        void sellingBelowCostIsFound() {
            assertEquals(Fault.SELLING_AT_A_LOSS, PriceAnomalyReport.faultOf(fact(1, "أ", 20, 15, 0, 5)));
        }

        @Test
        @DisplayName("selling at exactly cost is not a loss, and is not a business either")
        void sellingAtCostIsFound() {
            assertEquals(Fault.NO_MARGIN, PriceAnomalyReport.faultOf(fact(1, "أ", 15, 15, 0, 5)));
        }

        @Test
        @DisplayName("a priced item with no recorded cost reports its whole price as profit")
        void aMissingCostIsFound() {
            assertEquals(Fault.NO_BUY_PRICE, PriceAnomalyReport.faultOf(fact(1, "أ", 0, 15, 0, 5)));
        }

        @Test
        @DisplayName("the most stock first inside a fault - the same mistake costs more where there is more of it")
        void orderedByExposure() {
            CatalogFact few = new CatalogFact(1, "A", "قليل", 1, "g", 2, "s", "قطعة",
                    20, 15, 0, 1, true, false, null);
            CatalogFact many = new CatalogFact(2, "B", "كثير", 1, "g", 2, "s", "قطعة",
                    20, 15, 0, 400, true, false, null);

            ItemReportResult result = PriceAnomalyReport.build(List.of(few, many));

            assertEquals(2, result.rows().getFirst().itemId());
        }
    }

    @Nested
    @DisplayName("groups and valuation")
    class Grouping {

        @Test
        @DisplayName("an item whose group was deleted is listed under its own heading, never dropped")
        void ungroupedItemsAreStillListed() {
            CatalogFact orphan = ungrouped(fact(9, "يتيم", 10, 15, 0, 2));

            ItemReportResult result = GroupBreakdownReport.build(List.of(fact(1, "أ", 10, 15, 0, 1), orphan));

            assertEquals(2, result.itemRowCount(), "the orphan is one of the two items");
            assertTrue(result.rows().stream()
                            .anyMatch(row -> row.kind() == ItemReportRow.Kind.ITEM && row.itemId() == 9),
                    "the item with no group has to appear somewhere");
        }

        @Test
        @DisplayName("the ungrouped heading sorts last; it is not a group anybody can edit")
        void ungroupedComesLast() {
            ItemReportResult result = GroupBreakdownReport.build(
                    List.of(ungrouped(fact(9, "يتيم", 10, 15, 0, 2)), fact(1, "أ", 10, 15, 0, 1)));

            ItemReportRow lastHeading = result.rows().stream()
                    .filter(row -> row.kind() == ItemReportRow.Kind.GROUP && row.depth() == 0)
                    .reduce((first, second) -> second).orElseThrow();

            assertEquals(GroupBreakdownReport.label(GroupBreakdownReport.UNGROUPED), lastHeading.value(0));
        }

        @Test
        @DisplayName("a heading states its count and its value, and leaves the per-item columns empty")
        void headingsFillOnlyTheirOwnColumns() {
            ItemReportResult result = GroupBreakdownReport.build(List.of(fact(1, "أ", 10, 15, 0, 3)));

            ItemReportRow heading = result.rows().getFirst();
            assertEquals(ItemReportRow.Kind.GROUP, heading.kind());
            assertEquals(1.0, heading.value(4), "the item count");
            assertEquals(30.0, heading.value(7), "3 at a cost of 10");
            assertNull(heading.value(6), "a group has no balance; units do not add up across items");
        }

        @Test
        @DisplayName("valuation states cost, sale and the difference - printing only the sale value overstates the business")
        void valuationKeepsCostAndSaleApart() {
            ItemReportResult result = ValuationReport.build(
                    List.of(fact(1, "أ", 10, 15, 0, 4), fact(2, "ب", 20, 25, 0, 2)));

            ItemReportRow total = result.rows().getLast();
            assertEquals(ItemReportRow.Kind.TOTAL, total.kind());
            assertEquals(80.0, total.value(2), "4x10 + 2x20 at cost");
            assertEquals(110.0, total.value(3), "4x15 + 2x25 at sale");
            assertEquals(30.0, total.value(4), "the profit not yet earned");
        }

        @Test
        @DisplayName("a group holding nothing values at zero rather than dividing by it")
        void emptyStockDoesNotDivideByZero() {
            assertEquals(0.0, ValuationReport.marginPercent(0, 0));
            assertFalse(Double.isNaN(ValuationReport.marginPercent(0, 0)));
        }
    }
}
