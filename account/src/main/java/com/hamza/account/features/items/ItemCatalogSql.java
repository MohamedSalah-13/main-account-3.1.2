package com.hamza.account.features.items;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns an {@link ItemCatalogFilter} into the {@code WHERE} and {@code ORDER BY} the
 * items list is read with.
 * <p>
 * It is a pure function of the filter and holds no connection, which is the whole point:
 * every rule about what a filter <em>means</em> is checkable by a plain unit test, and the
 * page query and its count are assembled from one object so the pagination control can
 * never advertise rows the query will not return.
 * <p>
 * <b>The parameter order is part of the contract.</b> A statement binds
 * {@link Statement#whereParameters()} and then {@link Statement#orderParameters()},
 * because that is the order the placeholders appear in; anything appended by the caller
 * (a {@code LIMIT}) comes last. A value bound to the wrong placeholder is not an error,
 * it is a search that silently answers a different question - which is why
 * {@code ItemsCatalogQueryTest} counts the placeholders against the values.
 */
public final class ItemCatalogSql {

    private ItemCatalogSql() {
    }

    /**
     * What an item has on hand, in SQL, aliased exactly as {@code ItemsDao.applyBalances}
     * computes it in Java: the opening balance plus everything in, less everything out.
     * <p>
     * {@code ip.stock_first_balance} is the sum of the opening balances stored per
     * warehouse in {@code items_stock}. The legacy {@code items.first_balance} mirrors
     * warehouse 1 only; mixing it with movements aggregated across every warehouse would
     * make both the displayed balance and these filters answer two different scopes.
     */
    public static final String BALANCE = """
            (ip.stock_first_balance + ip.quantityPurchase + ip.quantitySalesRe + ip.toStock + ip.adjustment
              - ip.quantitySales - ip.quantityPurchaseRe - ip.fromStock)""";

    /**
     * An item nothing has ever been written against, on any of the four documents.
     * <p>
     * All four, not sales alone: an item bought and never sold is idle capital and is the
     * point of the report, while an item that has only ever been returned has still been
     * handled. The column is {@code num} on the two invoices and {@code item_id} on the
     * two returns - four names for one thing, which is what {@code ItemReferenceRegistry}
     * exists to remember.
     */
    public static final String NEVER_MOVED = """
            (NOT EXISTS (SELECT 1 FROM sales       WHERE sales.num = items.id)
             AND NOT EXISTS (SELECT 1 FROM purchase    WHERE purchase.num = items.id)
             AND NOT EXISTS (SELECT 1 FROM sales_re    WHERE sales_re.item_id = items.id)
             AND NOT EXISTS (SELECT 1 FROM purchase_re WHERE purchase_re.item_id = items.id))""";

    /** Never on a sales invoice. It may well have been bought - that is the distinction. */
    public static final String NEVER_SOLD =
            "NOT EXISTS (SELECT 1 FROM sales WHERE sales.num = items.id)";

    /** An item answers to a code in three tables; a search that knows one of them cannot find it. */
    private static final String SEARCH_ANY_WHERE = """
            (items.nameItem LIKE ?
              OR items.barcode LIKE ?
              OR items.id IN (SELECT item_id FROM item_barcodes WHERE barcode LIKE ?)
              OR items.id IN (SELECT items_id FROM items_units WHERE items_barcode LIKE ?))""";
    private static final String SEARCH_ANY_ORDER = """
            CASE
                WHEN items.barcode = ? THEN 0
                WHEN items.id IN (SELECT item_id FROM item_barcodes WHERE barcode = ?) THEN 1
                WHEN items.id IN (SELECT items_id FROM items_units WHERE items_barcode = ?) THEN 1
                WHEN items.nameItem LIKE ? THEN 2
                WHEN items.barcode LIKE ? THEN 3
                WHEN items.id IN (SELECT item_id FROM item_barcodes WHERE barcode LIKE ?) THEN 3
                WHEN items.id IN (SELECT items_id FROM items_units WHERE items_barcode LIKE ?) THEN 3
                ELSE 4
            END,
            items.id DESC""";
    /** Digits alone are an id or a barcode, and are matched exactly - never as a fragment of a name. */
    private static final String SEARCH_NUMERIC_WHERE = """
            (items.id = ?
              OR items.barcode = ?
              OR items.id IN (SELECT item_id FROM item_barcodes WHERE barcode = ?)
              OR items.id IN (SELECT items_id FROM items_units WHERE items_barcode = ?))""";
    private static final String SEARCH_NUMERIC_ORDER = """
            CASE
                WHEN items.id = ? THEN 0
                WHEN items.barcode = ? THEN 1
                WHEN items.id IN (SELECT item_id FROM item_barcodes WHERE barcode = ?) THEN 1
                WHEN items.id IN (SELECT items_id FROM items_units WHERE items_barcode = ?) THEN 1
                ELSE 2
            END,
            items.id DESC""";
    /** Every code the item answers to, for a search the operator has scoped to barcodes. */
    private static final String SEARCH_BARCODE_WHERE = """
            (items.barcode LIKE ?
              OR items.id IN (SELECT item_id FROM item_barcodes WHERE barcode LIKE ?)
              OR items.id IN (SELECT items_id FROM items_units WHERE items_barcode LIKE ?))""";

    /**
     * The same four places a code or a name can live, with the comparison left open.
     * <p>
     * One template rather than four hand-written variants: "exact", "contains", "starts
     * with" and "ends with" differ only in the operator and the pattern bound to it, and
     * writing each one out is four chances to leave a table off the list.
     */
    private static final String SEARCH_ANY_TEMPLATE = """
            (items.nameItem %1$s ?
              OR items.barcode %1$s ?
              OR items.id IN (SELECT item_id FROM item_barcodes WHERE barcode %1$s ?)
              OR items.id IN (SELECT items_id FROM items_units WHERE items_barcode %1$s ?))""";
    private static final String SEARCH_CODES_TEMPLATE = """
            (items.barcode %1$s ?
              OR items.id IN (SELECT item_id FROM item_barcodes WHERE barcode %1$s ?)
              OR items.id IN (SELECT items_id FROM items_units WHERE items_barcode %1$s ?))""";

    private static final String DEFAULT_ORDER = "items.id DESC";

    /**
     * A statement's two halves and the values each binds.
     *
     * @param where           begins with {@code " WHERE "}, or is empty when nothing filters
     * @param whereParameters bound before {@link #orderParameters()}, which the placeholder order requires
     */
    public record Statement(String where, List<Object> whereParameters,
                            String order, List<Object> orderParameters) {
    }

    /**
     * The one place a filter becomes SQL.
     * <p>
     * Conditions are joined with {@code AND} in a fixed order so the statement is stable
     * enough to pin, and every one of them is a condition on {@code items} or on the
     * pre-aggregated movement row joined as {@code ip} - never on a column the count
     * query would have to join a second table to see.
     */
    public static Statement build(ItemCatalogFilter filter) {
        ItemCatalogFilter safe = filter == null ? ItemCatalogFilter.EMPTY : filter;
        List<String> conditions = new ArrayList<>();
        List<Object> whereParameters = new ArrayList<>();
        String order = DEFAULT_ORDER;
        List<Object> orderParameters = List.of();

        String text = safe.searchText();
        if (!text.isEmpty()) {
            Statement search = searchStatement(text, safe.searchScope(), safe.matchMode());
            conditions.add(search.where());
            whereParameters.addAll(search.whereParameters());
            order = search.order();
            orderParameters = search.orderParameters();
        }

        // A sub group is the narrower of the two and wins; asking for both would return
        // the sub group's items either way, and naming only the main group would widen it.
        if (safe.subGroupId() != null) {
            conditions.add("items.sub_num = ?");
            whereParameters.add(safe.subGroupId());
        } else if (safe.mainGroupId() != null) {
            conditions.add("items.sub_num IN (SELECT id FROM sub_group WHERE main_id = ?)");
            whereParameters.add(safe.mainGroupId());
        }

        flag(conditions, "items.item_active", safe.active());
        flag(conditions, "items.item_has_validity", safe.tracksExpiry());
        // A barcode column holding an empty string is as codeless as one holding NULL,
        // and both occur: the item screen writes "" where the field was left blank.
        switch (safe.hasBarcode()) {
            case YES -> conditions.add("(items.barcode IS NOT NULL AND items.barcode <> '')");
            case NO -> conditions.add("(items.barcode IS NULL OR items.barcode = '')");
            case ANY -> {
            }
        }

        switch (safe.balance()) {
            // A minimum of zero means "none set", not "everything is low" - the same rule
            // StockLevel.of applies, said in SQL.
            case BELOW_MINIMUM -> conditions.add("(items.mini_quantity > 0 AND " + BALANCE + " <= items.mini_quantity)");
            case OUT_OF_STOCK -> conditions.add(BALANCE + " = 0");
            case NEGATIVE -> conditions.add(BALANCE + " < 0");
            case IN_STOCK -> conditions.add(BALANCE + " > 0");
            case ANY -> {
            }
        }

        switch (safe.usage()) {
            case NEVER_MOVED -> conditions.add(NEVER_MOVED);
            case NEVER_SOLD -> conditions.add(NEVER_SOLD);
            case ANY -> {
            }
        }

        if (safe.minSellPrice() != null) {
            conditions.add("items.sel_price1 >= ?");
            whereParameters.add(safe.minSellPrice());
        }
        if (safe.maxSellPrice() != null) {
            conditions.add("items.sel_price1 <= ?");
            whereParameters.add(safe.maxSellPrice());
        }

        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return new Statement(where, whereParameters, order, orderParameters);
    }

    /**
     * Whether the {@code WHERE} this filter produces names the joined movement row, and
     * so whether a count of it has to join that row too.
     * <p>
     * The count is deliberately over {@code items} alone whenever it can be: the movement
     * aggregate is a {@code GROUP BY} over every row of {@code quantity_items_table}, and
     * counting matches does not need a single balance out of it. Only a balance condition
     * changes that - and a count taken over the wrong {@code FROM} is a pagination control
     * that promises pages the query cannot fill.
     */
    public static boolean requiresMovementJoin(ItemCatalogFilter filter) {
        return filter != null && filter.balance() != ItemCatalogFilter.BalanceRule.ANY;
    }

    private static void flag(List<String> conditions, String column, ItemCatalogFilter.Tristate value) {
        switch (value) {
            case YES -> conditions.add(column + " = 1");
            case NO -> conditions.add(column + " = 0");
            case ANY -> {
            }
        }
    }

    /**
     * The text condition and the ranking that goes with it.
     * <p>
     * The ranking is in the {@code ORDER BY} rather than in two Java passes, which is what
     * lets a search be counted and paged: an exact code first, then a name that starts with
     * the text, then one that merely contains it. Two phases deduplicated in Java have no
     * page boundary and no total.
     */
    private static Statement searchStatement(String text, ItemCatalogFilter.SearchScope scope,
                                             ItemCatalogFilter.MatchMode mode) {
        if (mode != ItemCatalogFilter.MatchMode.AUTO) {
            return explicitMatch(text, scope, mode);
        }
        String contains = "%" + text + "%";
        String starts = text + "%";
        return switch (scope) {
            case CODE -> {
                // An id is a number or it is nothing; -1 matches no row rather than
                // throwing, which is what a code search for letters should do.
                int id = parseId(text);
                yield new Statement("items.id = ?", List.of(id), DEFAULT_ORDER, List.of());
            }
            case BARCODE -> new Statement(SEARCH_BARCODE_WHERE, List.of(contains, contains, contains),
                    DEFAULT_ORDER, List.of());
            case NAME -> new Statement("items.nameItem LIKE ?", List.of(contains),
                    "CASE WHEN items.nameItem LIKE ? THEN 0 ELSE 1 END,\nitems.id DESC", List.of(starts));
            case ANY -> {
                if (text.matches("\\d+")) {
                    int id = parseId(text);
                    yield new Statement(SEARCH_NUMERIC_WHERE, List.of(id, text, text, text),
                            SEARCH_NUMERIC_ORDER, List.of(id, text, text, text));
                }
                yield new Statement(SEARCH_ANY_WHERE, List.of(contains, contains, contains, contains),
                        SEARCH_ANY_ORDER, List.of(text, text, text, starts, starts, starts, starts));
            }
        };
    }

    /**
     * A search the operator has told us how to compare.
     * <p>
     * No ranking: they asked a precise question, so the answer is the plain catalogue order.
     * The elaborate {@code CASE} that ranks an exact code above a prefix above a fragment
     * exists to guess at what an ambiguous search meant, and there is nothing left to guess.
     * <p>
     * The id is deliberately not among the columns compared. An id is a number and these are
     * string comparisons; "starts with 12" over an id column is a question with no useful
     * answer, and the code scope already matches an id exactly.
     */
    private static Statement explicitMatch(String text, ItemCatalogFilter.SearchScope scope,
                                           ItemCatalogFilter.MatchMode mode) {
        if (scope == ItemCatalogFilter.SearchScope.CODE) {
            // A code is an id, and an id is matched by being that id whatever the mode says.
            return new Statement("items.id = ?", List.of(parseId(text)), DEFAULT_ORDER, List.of());
        }
        boolean exact = mode == ItemCatalogFilter.MatchMode.EXACT;
        String operator = exact ? "=" : "LIKE";
        String pattern = switch (mode) {
            case EXACT -> text;
            case CONTAINS -> "%" + text + "%";
            case STARTS_WITH -> text + "%";
            case ENDS_WITH -> "%" + text;
            case AUTO -> text;   // unreachable: AUTO never arrives here
        };
        String where = switch (scope) {
            case NAME -> "items.nameItem " + operator + " ?";
            case BARCODE -> SEARCH_CODES_TEMPLATE.formatted(operator);
            default -> SEARCH_ANY_TEMPLATE.formatted(operator);
        };
        int placeholders = (int) where.chars().filter(character -> character == '?').count();
        return new Statement(where, java.util.Collections.nCopies(placeholders, pattern),
                DEFAULT_ORDER, List.of());
    }

    /** A barcode too long to be an id is still a barcode; it just is not this id. */
    private static int parseId(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException notAnId) {
            return -1;
        }
    }
}
