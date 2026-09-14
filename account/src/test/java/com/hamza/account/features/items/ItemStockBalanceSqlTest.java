package com.hamza.account.features.items;

import com.hamza.account.features.items.ItemStockBalanceSql.Movement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ItemStockBalanceSql} restates {@code quantity_items_table} for named items, and a
 * restated balance is only safe while it says what the view says. So the view's text is
 * rebuilt here from {@link ItemStockBalanceSql#MOVEMENTS} - every CTE, every join, every
 * column - and each piece must be found in {@code R__views.sql}; and the view may carry no
 * term the list lacks. Change a CTE, add a movement to the view, or edit the list alone,
 * and this fails.
 * <p>
 * It checks the text, not the figures. That the two agree row for row was measured on a
 * copy of a real database with a second warehouse, transfers both ways and posted and draft
 * counts seeded in (2,504 rows, none different); a text that matches cannot drift from
 * that without this test noticing.
 */
class ItemStockBalanceSqlTest {

    @Test
    @DisplayName("every movement is a CTE of quantity_items_table, word for word")
    void everyMovementIsACteOfTheView() {
        String view = view();
        for (Movement movement : ItemStockBalanceSql.MOVEMENTS) {
            String cte = movement.cte() + " AS (SELECT " + selected(movement.stock(), "stock_id") + ", "
                    + selected(movement.item(), "item_id") + ", SUM(" + movement.quantity() + ") AS qty FROM "
                    + movement.from() + (movement.where() == null ? "" : " WHERE " + movement.where())
                    + " GROUP BY " + movement.stock() + ", " + movement.item() + ")";
            assertTrue(view.contains(cte), "the view no longer declares: " + cte);
        }
    }

    @Test
    @DisplayName("each movement is joined on the same row and read into the same column")
    void everyMovementIsJoinedAndReadTheSameWay() {
        String view = view();
        for (Movement movement : ItemStockBalanceSql.MOVEMENTS) {
            String alias = movement.alias();
            String join = "LEFT JOIN " + movement.cte() + " " + alias + " ON " + alias + ".stock_id = ist.stock_id AND "
                    + alias + ".item_id = ist.item_id";
            String column = "COALESCE(" + alias + ".qty, 0) AS " + movement.column();
            assertTrue(view.contains(join), "the view no longer joins: " + join);
            assertTrue(view.contains(column), "the view no longer reads: " + column);
        }
        assertTrue(view.contains("ist.first_balance,"), "the opening balance is items_stock's");
        assertTrue(view.contains(ItemStockBalanceSql.BASE), "the view is driven by " + ItemStockBalanceSql.BASE);
    }

    @Test
    @DisplayName("the view has no movement the list does not know about")
    void theViewHasNoOtherMovement() {
        String view = view();
        int size = ItemStockBalanceSql.MOVEMENTS.size();
        assertEquals(size, count(view, "LEFT JOIN "), "a join in the view that the list lacks");
        assertEquals(size, count(view, "COALESCE("), "a column in the view that the list lacks");
        assertEquals(size, count(view, " AS qty FROM "), "a CTE in the view that the list lacks");
    }

    @Test
    @DisplayName("a query for named items is correlated on the item and never reads the view")
    void aQueryForNamedItemsIsCorrelated() {
        String sql = ItemStockBalanceSql.forItems(3);

        assertFalse(sql.contains("quantity_items_table"), "reading the view builds every item's history");
        assertFalse(sql.contains("GROUP BY"), "an aggregate over a whole line table is the cost being removed");
        assertTrue(sql.endsWith(" WHERE ist.stock_id = ? AND ist.item_id IN (?, ?, ?)"));
        assertEquals(4, count(sql, "?"));
        for (Movement movement : ItemStockBalanceSql.MOVEMENTS) {
            assertTrue(sql.contains(" AS " + movement.column()), movement.column());
        }
        // Each subquery is restricted to the outer row on qualified columns: an unqualified
        // name the source lacked would resolve to ist's column and match every line.
        // Two per movement, and the join to items in the base.
        assertEquals(2 * ItemStockBalanceSql.MOVEMENTS.size() + 1, count(sql, " = ist."));
        assertTrue(sql.contains("FROM sales_names_table m WHERE m.stock_id = ist.stock_id AND m.num = ist.item_id"));
        assertTrue(sql.contains("FROM sales_return_names_table m WHERE m.stock_id = ist.stock_id AND m.item_id = ist.item_id"));
        assertTrue(sql.contains("FROM stock_transfer_view m WHERE m.stock_from = ist.stock_id AND m.item_id = ist.item_id"));
        assertTrue(sql.contains("WHERE sc.status = 'POSTED' AND sc.stock_id = ist.stock_id AND scl.item_id = ist.item_id"));
    }

    @Test
    void refusesAnEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> ItemStockBalanceSql.forItems(0));
    }

    /** How the view selects a column: bare when it already carries the name, aliased when not. */
    private static String selected(String column, String name) {
        String bare = column.substring(column.indexOf('.') + 1);
        return bare.equals(name) ? column : column + " AS " + name;
    }

    /** The view's definition with its comments removed and its whitespace collapsed. */
    private static String view() {
        String views = read("db/migration/R__views.sql").replaceAll("--[^\\n]*", " ");
        int start = views.indexOf("CREATE VIEW quantity_items_table AS");
        assertTrue(start > 0, "quantity_items_table is not in R__views.sql");
        return views.substring(start, views.indexOf(';', start))
                .replaceAll("\\s+", " ")
                .replace("( ", "(")
                .replace(" )", ")");
    }

    private static int count(String text, String fragment) {
        Matcher matcher = Pattern.compile(Pattern.quote(fragment)).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String read(String resource) {
        try (InputStream in = ItemStockBalanceSqlTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
