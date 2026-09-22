package com.hamza.account.model.dao;

import com.hamza.account.features.itemreports.ItemSalesFact;
import com.hamza.account.features.itemreports.ItemSalesRepository;
import com.hamza.account.features.itemreports.JdbcItemSalesRepository;
import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.model.domain.ItemSalesRank;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * The item movement report ("تقرير حركة الأصناف"): what each item sold in a month or a year, most first.
 *
 * <p><b>It reads the Pareto reports' figures, and there is no view behind it any more.</b> It read
 * {@code view_item_sales_rank}, which summed {@code quantity} across units - a carton of twelve and a
 * piece counted as two of one thing - never subtracted a return, and took the line's amount before
 * its own discount. {@link JdbcItemSalesRepository} answers the same question the way every other item
 * report does ({@code ItemNetLines}): base units, sales less the returns dated in the period, a line
 * after its own discount and before the invoice's, and the cost the lines recorded -
 * {@code ParetoDatabaseAcceptanceTest} works it out by hand on MySQL. So the profit column is the
 * items' margin before the invoices' own discounts, the question the view's comment said it asked.</p>
 */
public class ItemSalesRankDao {

    /** Most sold first; a tie by the larger net, then by name, so the order does not move between runs. */
    static final Comparator<ItemSalesRank> MOST_SOLD_FIRST =
            Comparator.comparingDouble(ItemSalesRank::getTotalQty).reversed()
                    .thenComparing(Comparator.comparingDouble(ItemSalesRank::getTotalAmount).reversed())
                    .thenComparing(ItemSalesRank::getItemName, Comparator.nullsLast(Comparator.naturalOrder()));

    private final ItemSalesRepository sales;

    public ItemSalesRankDao() {
        this(new JdbcItemSalesRepository());
    }

    ItemSalesRankDao(ItemSalesRepository sales) {
        this.sales = sales;
    }

    /** One month of one year - {@code month} is 1 for January. */
    public List<ItemSalesRank> getBestSellersByMonth(int year, int month) throws DaoException {
        YearMonth period = YearMonth.of(year, month);
        return between(period.atDay(1), period.atEndOfMonth());
    }

    public List<ItemSalesRank> getBestSellersByYear(int year) throws DaoException {
        return between(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    private List<ItemSalesRank> between(LocalDate from, LocalDate to) throws DaoException {
        return rank(sales.sales(ItemCatalogFilter.EMPTY, from, to));
    }

    /** The facts as the screen's rows, most sold first. */
    static List<ItemSalesRank> rank(List<ItemSalesFact> facts) {
        return facts.stream().map(ItemSalesRankDao::row).sorted(MOST_SOLD_FIRST).toList();
    }

    private static ItemSalesRank row(ItemSalesFact fact) {
        ItemSalesRank row = new ItemSalesRank();
        row.setItemId(fact.itemId());
        row.setItemName(fact.name());
        row.setTotalQty(fact.quantity());
        row.setTotalAmount(fact.net());
        row.setTotalProfit(fact.margin());
        return row;
    }
}
