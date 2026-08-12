package com.hamza.account.features.inventory;

import com.hamza.account.features.notification.StockLevel;

/**
 * Which items the sheet is asking about: everything, or one state of the stock.
 * <p>
 * Each constant carries both halves of its own definition - the SQL that narrows the
 * query, and the test a loaded row answers - and {@link #agreesWithStockLevel()}
 * ties the second to {@link StockLevel}, which is what the sales screens use to
 * decide the same question. That is the guard against the failure this shape exists
 * to prevent: the header saying thirty-seven items are under their minimum while the
 * alert fires for thirty-nine, because two pieces of code drew the boundary
 * differently. The boundaries are the part that goes wrong by one - exactly at the
 * minimum, exactly at zero - and {@code StockLevelTest} already pins them down.
 * <p>
 * A new state is a constant here. It reaches the rows, the totals and the row count
 * together, because {@code InventoryDao} builds all three from the same
 * {@link InventoryQuery}.
 */
public enum StockFilter {

    /** No narrowing at all. */
    ALL("الكل", null),

    /** Anything still on the shelf, however little. */
    IN_STOCK("متاح", "%1$s > 0"),

    /**
     * At or under the minimum the item was configured with, and not yet gone. A
     * minimum of zero means none was set, so it is excluded - otherwise every item
     * without one would report itself as low.
     */
    LOW("تحت حد الطلب", "%1$s > 0 AND i.mini_quantity > 0 AND %1$s <= i.mini_quantity"),

    /** Nothing left. */
    OUT_OF_STOCK("منتهي", "%1$s = 0"),

    /**
     * Sold past what the stock says exists. Not merely empty: the recorded balance is
     * wrong, and on a stock sheet that is the line worth looking at first.
     */
    NEGATIVE("رصيد سالب", "%1$s < 0");

    private final String title;
    private final String condition;

    StockFilter(String title, String condition) {
        this.title = title;
        this.condition = condition;
    }

    /** How the option reads in the picker. */
    public String title() {
        return title;
    }

    public boolean narrows() {
        return condition != null;
    }

    /**
     * The {@code WHERE} fragment, with {@code balanceExpression} substituted wherever
     * the balance is needed. Empty for {@link #ALL}.
     * <p>
     * The expression is passed in rather than repeated here because there is one
     * definition of what an item's balance is, and it lives in {@code InventoryDao}.
     */
    public String condition(String balanceExpression) {
        return narrows() ? condition.formatted(balanceExpression) : "";
    }

    /**
     * Whether a row already in hand belongs to this filter - for anything working on
     * loaded rows rather than issuing a query.
     */
    public boolean matches(InventoryRow row) {
        return matches(StockLevel.of(row.balance(), row.miniQuantity()));
    }

    public boolean matches(StockLevel level) {
        return switch (this) {
            case ALL -> true;
            case IN_STOCK -> level == StockLevel.OK || level == StockLevel.AT_MINIMUM;
            case LOW -> level == StockLevel.AT_MINIMUM;
            case OUT_OF_STOCK -> level == StockLevel.OUT_OF_STOCK;
            case NEGATIVE -> level == StockLevel.NEGATIVE;
        };
    }

    /**
     * The {@link StockLevel} this filter is the counterpart of, or null for the ones
     * that cover more than one state. Exists so a test can assert the pairing rather
     * than trusting the switch above to stay honest.
     */
    public StockLevel agreesWithStockLevel() {
        return switch (this) {
            case LOW -> StockLevel.AT_MINIMUM;
            case OUT_OF_STOCK -> StockLevel.OUT_OF_STOCK;
            case NEGATIVE -> StockLevel.NEGATIVE;
            case ALL, IN_STOCK -> null;
        };
    }
}
