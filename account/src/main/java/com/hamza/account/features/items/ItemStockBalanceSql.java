package com.hamza.account.features.items;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;

import java.util.List;
import java.util.StringJoiner;

/**
 * The rows of {@code quantity_items_table} for named items only, read without building
 * the view.
 * <p>
 * <b>Why this exists.</b> The view aggregates every line ever written - each of its seven
 * CTEs is a {@code GROUP BY} over a whole line table - and MySQL materializes all of them
 * before it can hand back a single (item, stock) row, whatever the outer query filters on.
 * So finding the one item a barcode names cost a pass over the entire sales history:
 * measured at 1.4 seconds per scan on 106,606 sales lines, on the JavaFX thread, growing
 * with every invoice saved. Here each column is a correlated subquery that reaches its lines
 * through the item's index, so its cost is the lines of that item alone.
 * <p>
 * <b>It is the same definition, not a second one.</b> A stock balance has one meaning in
 * this system, and a second query computing it is exactly the defect the treasury's three
 * balances were. So each {@link Movement} names the source, the stock and item columns and
 * the quantity expression the view's CTE uses, and {@code ItemStockBalanceSqlTest} rebuilds
 * the view's CTEs, joins and columns from this list and finds each of them in
 * {@code R__views.sql} - a movement changed on one side, or added to the view alone, fails
 * the build. The document sources come from {@link DocumentTableSpec#lineView()} and
 * {@link DocumentTableSpec#lineItem()}, which are the views the CTEs read.
 * <p>
 * The catalogue-wide queries keep reading the view: when every item is wanted, aggregating
 * everything once is the right plan, and a correlated subquery per item would be the slower
 * one.
 */
public final class ItemStockBalanceSql {

    /**
     * One term of the balance - one CTE of the view.
     *
     * @param column   the view's column, and this query's
     * @param cte      the CTE's name in the view
     * @param alias    the CTE's alias where the view joins it
     * @param from     what the CTE aggregates, joins included
     * @param stock    the column that says which warehouse
     * @param item     the column that says which item
     * @param quantity what is summed
     * @param where    the CTE's own condition, or {@code null}
     */
    public record Movement(String column, String cte, String alias, String from,
                           String stock, String item, String quantity, String where) {
    }

    /** Lines are stored in the unit they were entered in, with the factor beside them. */
    private static final String LINE_QUANTITY = "quantity * type_value";

    /** In the order the view declares them. */
    public static final List<Movement> MOVEMENTS = List.of(
            document("quantityPurchase", "purchase_agg", "pa", DocumentType.PURCHASE),
            document("quantitySales", "sales_agg", "sa", DocumentType.SALES),
            document("quantityPurchaseRe", "purchase_re_agg", "pra", DocumentType.PURCHASE_RETURN),
            document("quantitySalesRe", "sales_re_agg", "sra", DocumentType.SALES_RETURN),
            new Movement("fromStock", "transfer_from_agg", "tfa", "stock_transfer_view",
                    "stock_from", "item_id", LINE_QUANTITY, null),
            new Movement("toStock", "transfer_to_agg", "tta", "stock_transfer_view",
                    "stock_to", "item_id", LINE_QUANTITY, null),
            new Movement("adjustment", "adjustment_agg", "ada",
                    "stock_count_lines scl JOIN stock_count sc ON sc.id = scl.count_id",
                    "sc.stock_id", "scl.item_id",
                    "scl.counted_qty * scl.type_value - scl.system_qty", "sc.status = 'POSTED'"));

    /** The row every movement is correlated with, exactly as the view drives it. */
    public static final String BASE = "FROM items_stock ist JOIN items i ON i.id = ist.item_id";

    private ItemStockBalanceSql() {
    }

    /**
     * The view's columns for {@code itemCount} items in one warehouse. Binds the stock id,
     * then each item id. An item with no {@code items_stock} row for that warehouse has no
     * row here, as it has none in the view.
     */
    public static String forItems(int itemCount) {
        if (itemCount < 1) {
            throw new IllegalArgumentException("itemCount must be at least 1");
        }
        StringJoiner marks = new StringJoiner(", ", "(", ")");
        for (int i = 0; i < itemCount; i++) {
            marks.add("?");
        }
        return rows() + " WHERE ist.stock_id = ? AND ist.item_id IN " + marks;
    }

    private static String rows() {
        StringBuilder sql = new StringBuilder("SELECT ist.item_id, ist.stock_id, ist.first_balance");
        for (Movement movement : MOVEMENTS) {
            sql.append(", COALESCE((").append(correlated(movement)).append("), 0) AS ")
                    .append(movement.column());
        }
        return sql.append(' ').append(BASE).toString();
    }

    /**
     * The CTE's sum, restricted to the outer row's item and warehouse. A source the view
     * reads unaliased gets the alias {@code m}, and its columns are qualified with it: an
     * unqualified name that the source lacks would silently resolve to {@code ist}'s column
     * of the same name and match every row.
     */
    private static String correlated(Movement movement) {
        boolean aliased = movement.stock().contains(".");
        String from = aliased ? movement.from() : movement.from() + " m";
        String stock = aliased ? movement.stock() : "m." + movement.stock();
        String item = aliased ? movement.item() : "m." + movement.item();
        return "SELECT SUM(" + movement.quantity() + ") FROM " + from + " WHERE "
                + (movement.where() == null ? "" : movement.where() + " AND ")
                + stock + " = ist.stock_id AND " + item + " = ist.item_id";
    }

    private static Movement document(String column, String cte, String alias, DocumentType type) {
        DocumentTableSpec spec = DocumentTableSpec.of(type);
        return new Movement(column, cte, alias, spec.lineView(), "stock_id", spec.lineItem(),
                LINE_QUANTITY, null);
    }
}
