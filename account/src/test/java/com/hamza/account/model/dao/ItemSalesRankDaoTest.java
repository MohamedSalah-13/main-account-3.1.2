package com.hamza.account.model.dao;

import com.hamza.account.features.itemreports.ItemSalesFact;
import com.hamza.account.features.itemreports.ItemSalesRepository;
import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.model.domain.ItemSalesRank;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The item movement report reads the Pareto reports' per-item figures. What is pinned here is what it adds
 * to them: the period a month or a year stands for, the row it makes of a fact, and the order.
 */
class ItemSalesRankDaoTest {

    /** Rice sold ten and had one back; juice two cartons of twelve - the October of the Pareto fixture. */
    private static final List<ItemSalesFact> OCTOBER = List.of(
            new ItemSalesFact(1, "rice", null, "piece", 9, 540, 405),
            new ItemSalesFact(2, "juice", null, "piece", 24, 220, 100),
            new ItemSalesFact(3, "oil", null, "piece", 5, 200, 150));

    @Test
    void aMonthIsItsFirstToItsLastDay() throws Exception {
        Recording sales = new Recording(OCTOBER);
        new ItemSalesRankDao(sales).getBestSellersByMonth(2026, 10);
        assertEquals(List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), sales.asked);

        sales.asked.clear();
        new ItemSalesRankDao(sales).getBestSellersByMonth(2028, 2);
        assertEquals(List.of(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)), sales.asked, "a leap February");
    }

    @Test
    void aYearIsJanuaryToDecember() throws Exception {
        Recording sales = new Recording(OCTOBER);
        new ItemSalesRankDao(sales).getBestSellersByYear(2026);
        assertEquals(List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), sales.asked);
    }

    /**
     * Base units net of returns, most first: the view this replaced put rice first with ten, counting the
     * two cartons of juice as two and the returned rice not at all.
     */
    @Test
    void theMostSoldComesFirstInBaseUnits() throws Exception {
        List<ItemSalesRank> rows = new ItemSalesRankDao(new Recording(OCTOBER)).getBestSellersByMonth(2026, 10);

        assertEquals(List.of("juice", "rice", "oil"), rows.stream().map(ItemSalesRank::getItemName).toList());
        ItemSalesRank rice = rows.get(1);
        assertEquals(1, rice.getItemId());
        assertEquals(9, rice.getTotalQty(), 0.001);
        assertEquals(540, rice.getTotalAmount(), 0.001);
        assertEquals(135, rice.getTotalProfit(), 0.001, "the margin before the invoices' own discounts");
    }

    @Test
    void aTieGoesToTheLargerNetThenTheName() {
        List<ItemSalesRank> rows = ItemSalesRankDao.rank(List.of(
                new ItemSalesFact(1, "b", null, "piece", 5, 100, 0),
                new ItemSalesFact(2, "a", null, "piece", 5, 100, 0),
                new ItemSalesFact(3, "c", null, "piece", 5, 300, 0)));

        assertEquals(List.of("c", "a", "b"), rows.stream().map(ItemSalesRank::getItemName).toList());
    }

    /** Hands back the facts it was given and remembers the period it was asked about. */
    private static final class Recording implements ItemSalesRepository {
        private final List<ItemSalesFact> facts;
        private final List<LocalDate> asked = new ArrayList<>();

        Recording(List<ItemSalesFact> facts) {
            this.facts = facts;
        }

        @Override
        public List<ItemSalesFact> sales(ItemCatalogFilter filter, LocalDate from, LocalDate to) {
            assertEquals(ItemCatalogFilter.EMPTY, filter, "the report is over every item");
            asked.add(from);
            asked.add(to);
            return facts;
        }

        @Override
        public double headerDiscounts(LocalDate from, LocalDate to) {
            throw new AssertionError("the item movement report shows no invoice discount line");
        }
    }
}
