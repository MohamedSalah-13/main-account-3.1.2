package com.hamza.account.features.report.itemsales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A period's item sales, the rows the most sold by value first.
 *
 * <p><b>By value, not by quantity</b>: the report it replaced ranked items by quantity, which set twelve
 * pieces of soap above ten kilos of rice as though the two were counted in one unit. A quantity is the
 * item's own; a value is comparable between any two.</p>
 *
 * <p>The totals are the rows' sums of money, never of quantities. <b>The invoices' own discounts are a
 * figure of their own</b>, shown only when nothing narrows the rows: they belong to no item, and set against
 * a handful of items found by a search they would make a net nobody's invoices add up to. With them, the
 * items' net less those discounts is the period's net sales on the profit and loss.</p>
 *
 * @param headerDiscounts the sales' own discounts less the returns', or empty when the rows are narrowed
 * @param marginVisible   whether the cost was read - the margin columns and card exist only then
 */
public record ItemSalesReport(ItemSalesFilter filter, List<ItemSalesRow> rows, Optional<BigDecimal> headerDiscounts,
                              boolean marginVisible) {

    /** The most sold by value first; a tie by the larger quantity, then by name, so the order holds between runs. */
    static final Comparator<ItemSalesRow> MOST_SOLD_FIRST = Comparator.comparing(ItemSalesRow::net).reversed()
            .thenComparing(Comparator.comparing(ItemSalesRow::netQuantity).reversed())
            .thenComparing(ItemSalesRow::name)
            .thenComparingInt(ItemSalesRow::itemId);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public ItemSalesReport {
        Objects.requireNonNull(filter, "filter");
        rows = rows == null ? List.of() : rows.stream().sorted(MOST_SOLD_FIRST).toList();
        headerDiscounts = headerDiscounts == null ? Optional.empty() : headerDiscounts;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public BigDecimal sold() {
        return sum(ItemSalesRow::sold);
    }

    public BigDecimal returned() {
        return sum(ItemSalesRow::returned);
    }

    /** The items' net: what they sold for less what was refunded, before the invoices' own discounts. */
    public BigDecimal net() {
        return sum(ItemSalesRow::net);
    }

    /** The items' margin, or empty when the cost was not read. */
    public Optional<BigDecimal> margin() {
        if (!marginVisible) {
            return Optional.empty();
        }
        return Optional.of(rows.stream().map(row -> row.margin().orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** The margin as a percentage of the net; empty without a cost or with nothing sold. */
    public Optional<BigDecimal> marginPercent() {
        BigDecimal net = net();
        return net.signum() <= 0 ? Optional.empty()
                : margin().map(margin -> margin.multiply(HUNDRED).divide(net, 2, RoundingMode.HALF_UP));
    }

    /** What came back as a percentage of what was sold; empty with nothing sold. */
    public Optional<BigDecimal> returnRate() {
        BigDecimal sold = sold();
        return sold.signum() <= 0 ? Optional.empty()
                : Optional.of(returned().multiply(HUNDRED).divide(sold, 2, RoundingMode.HALF_UP));
    }

    /**
     * The items' net less the invoices' own discounts - the invoices' net sales, which is the profit and
     * loss's figure for the period. Empty when the rows are narrowed, since the discounts are then left out.
     */
    public Optional<BigDecimal> invoicesNet() {
        return headerDiscounts.map(discounts -> net().subtract(discounts));
    }

    /** A row's net as a share of the items' net; empty when the total is nothing or less. */
    public Optional<BigDecimal> share(ItemSalesRow row) {
        BigDecimal total = net();
        return total.signum() <= 0 ? Optional.empty()
                : Optional.of(row.net().multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP));
    }

    /** Where a row stands, the first being 1 - or 0 for a row that is not in the report. */
    public int rank(ItemSalesRow row) {
        return rows.indexOf(row) + 1;
    }

    /** The first {@code count} rows that sold anything - the chart's bars. */
    public List<ItemSalesRow> top(int count) {
        return rows.stream().filter(row -> row.net().signum() > 0).limit(count).toList();
    }

    private BigDecimal sum(Function<ItemSalesRow, BigDecimal> figure) {
        return rows.stream().map(figure).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
