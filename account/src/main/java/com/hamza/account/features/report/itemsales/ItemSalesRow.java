package com.hamza.account.features.report.itemsales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * What one item sold over a period, and what came back of it.
 *
 * <p><b>Quantities are the item's base unit</b> - a carton of twelve is twelve - so the figure on a row is
 * one item's own and is never added to another's: pieces, kilos and metres make no total. <b>An amount is
 * a line's after its own discount and before the document's</b> ({@code ItemNetLines.lineAmount}); a
 * discount on a whole invoice belongs to no item. A return counts in the period it is dated in, whatever
 * the date of the invoice it reverses.</p>
 *
 * @param unitName       the item's base unit, or blank
 * @param soldQuantity   base units on the period's sales
 * @param returnedQuantity base units on the period's sales returns, positive
 * @param invoices       how many of the period's sales named the item
 * @param sold           the sales' lines, each after its own discount
 * @param returned       the returns' lines, each after its own discount, positive
 * @param cost           what the lines recorded as their cost, less the returned lines' - {@code null} when the
 *                       reader may not see a profit, and then it was never read
 */
public record ItemSalesRow(int itemId, String name, String groupName, String unitName,
                           BigDecimal soldQuantity, BigDecimal returnedQuantity, int invoices,
                           BigDecimal sold, BigDecimal returned, BigDecimal cost) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public ItemSalesRow {
        name = name == null ? "" : name;
        unitName = unitName == null ? "" : unitName;
        soldQuantity = orZero(soldQuantity);
        returnedQuantity = orZero(returnedQuantity);
        sold = orZero(sold);
        returned = orZero(returned);
    }

    /** Base units that stayed sold. */
    public BigDecimal netQuantity() {
        return soldQuantity.subtract(returnedQuantity);
    }

    /** What the item sold for, less what was refunded for it. */
    public BigDecimal net() {
        return sold.subtract(returned);
    }

    /** The net less the cost - before the invoices' own discounts; empty when the cost was not read. */
    public Optional<BigDecimal> margin() {
        return cost == null ? Optional.empty() : Optional.of(net().subtract(cost));
    }

    /** The margin as a percentage of the net; empty with no cost, or with nothing sold to divide by. */
    public Optional<BigDecimal> marginPercent() {
        BigDecimal net = net();
        if (net.signum() <= 0) {
            return Optional.empty();
        }
        return margin().map(margin -> margin.multiply(HUNDRED).divide(net, 2, RoundingMode.HALF_UP));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
