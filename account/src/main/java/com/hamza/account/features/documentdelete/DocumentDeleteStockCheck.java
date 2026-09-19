package com.hamza.account.features.documentdelete;

import java.util.ArrayList;
import java.util.List;

/**
 * What deleting a document would do to the shelf.
 * <p>
 * Deleting reverses a document's stock effect, so a document that brought goods <em>in</em> takes
 * them back out when it goes: a purchase, and a sales return. If they have since been sold, the
 * balance lands below zero - stock nobody can explain, on an item nobody was warned about, found
 * weeks later at a count. A sale or a purchase return puts goods back on deletion and can never
 * cause it, which is why the caller asks this only of the two directions that can.
 * <p>
 * <b>A warning, never a refusal</b>, the same answer {@code ExpenseBalanceCheck} gives and for the
 * same reason: the balance is derived from what has been entered, and a shop that sells before it
 * enters the supplier's bill is already below zero on paper - refusing would block exactly the
 * correction that puts it right. The screen says what will go negative and the person decides.
 * <p>
 * Pure, so the arithmetic is testable: the balances and the quantities are the caller's to fetch.
 */
public final class DocumentDeleteStockCheck {

    /** A millionth of a unit - below this two quantities are the same quantity. */
    private static final double EPSILON = 0.000_001;

    private DocumentDeleteStockCheck() {
    }

    /**
     * @param lines what the documents being deleted moved, one entry per item and warehouse
     * @return the entries that would end below zero, in the order given; empty when none would
     */
    public static List<Shortfall> shortfalls(List<StockLine> lines) {
        List<Shortfall> found = new ArrayList<>();
        for (StockLine line : lines) {
            if (line == null) {
                continue;
            }
            double after = line.currentBase() - line.removedBase();
            if (after < -EPSILON) {
                found.add(new Shortfall(line.itemName(), line.stockName(), after));
            }
        }
        return List.copyOf(found);
    }

    /**
     * One item in one warehouse, as the documents being deleted touch it.
     *
     * @param currentBase what the warehouse holds of it today, in base units
     * @param removedBase what deleting the documents would take back out, in base units
     */
    public record StockLine(String itemName, String stockName,
                            double currentBase, double removedBase) {
    }

    /** @param remainingBase what the balance would come to - always negative */
    public record Shortfall(String itemName, String stockName, double remainingBase) {
    }
}
