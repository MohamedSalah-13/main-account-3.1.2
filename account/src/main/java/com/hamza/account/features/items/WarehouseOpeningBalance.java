package com.hamza.account.features.items;

import com.hamza.account.delete.Reference;
import com.hamza.account.opening.OpeningBalanceGuard.Verdict;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The opening balance of one item in one warehouse, and whether it may still change.
 * <p>
 * <b>The same rule as {@code OpeningBalanceGuard}, asked of the figure that is actually read.</b>
 * An opening balance is the one number with no date on it: a warehouse's balance is
 * {@code items_stock.first_balance} plus everything that came in minus everything that went out,
 * so changing it changes what that shelf held at every moment of its history. It is a closed entry
 * once anything has moved the item <em>in that warehouse</em>, and a correction after that is a
 * dated count.
 * <p>
 * It replaced the item-wide rule ({@code OpeningBalanceRegistry.ITEMS}), which asked the right
 * question of the wrong place: it counted an item's lines in every warehouse, and compared the
 * value against {@code items.first_balance} - a copy of warehouse 1 that a trigger kept, and that
 * V78 drops. So an item sold only in a branch could not have its main warehouse's opening entered,
 * while nothing guarded a second warehouse's opening at all. Only a warehouse's own movements are
 * counted against its own opening, which is what makes entering one for a new warehouse possible
 * after the main one has traded for years.
 * <p>
 * <b>What moves an item is the balance's own list</b>, {@link ItemStockBalanceSql#MOVEMENTS} -
 * the terms of {@code quantity_items_table}, held to the view by {@code ItemStockBalanceSqlTest}.
 * A movement added to the view is therefore a movement that locks the opening, with nothing to
 * remember. One difference, and it is deliberate: a <b>draft</b> count locks it as much as a posted
 * one. The draft holds the book balance the counter was shown, and moving the opening under it
 * makes the sheet post a difference nobody measured - the reason the item-wide rule gave.
 */
public final class WarehouseOpeningBalance {

    /** {@code DECIMAL(14,3)}: half a thousandth is below anything the column can hold. */
    static final double TOLERANCE = 0.0005;

    /** What the rule needs to know - {@link WarehouseStockDao} in the application. */
    public interface Reader {

        /** How many rows of each movement name the item in the warehouse, keyed by the movement's column. */
        Map<String, Integer> openingMovementCounts(int itemId, int stockId) throws DaoException;

        /** {@code items_stock.first_balance} for the pair, zero when it has no row. */
        double openingBalance(int itemId, int stockId) throws DaoException;
    }

    private final Reader reader;

    public WarehouseOpeningBalance(@NotNull Reader reader) {
        this.reader = reader;
    }

    /**
     * What has moved the item in the warehouse, one entry per kind of line, empty if nothing has.
     * An item not saved yet has moved nothing.
     */
    public List<Reference> movements(int itemId, int stockId) throws DaoException {
        if (itemId <= 0) {
            return List.of();
        }
        Map<String, Integer> byLabel = new LinkedHashMap<>();
        Map<String, Integer> counts = reader.openingMovementCounts(itemId, stockId);
        for (ItemStockBalanceSql.Movement movement : ItemStockBalanceSql.MOVEMENTS) {
            int count = counts.getOrDefault(movement.column(), 0);
            if (count > 0) {
                // The two halves of a transfer are one kind of line to the person reading the refusal.
                byLabel.merge(labelKey(movement), count, Integer::sum);
            }
        }
        return byLabel.entrySet().stream().map(entry -> new Reference(entry.getKey(), entry.getValue())).toList();
    }

    public boolean isLocked(int itemId, int stockId) throws DaoException {
        return !movements(itemId, stockId).isEmpty();
    }

    /** The stored opening - what every balance of the pair is computed from. */
    public double stored(int itemId, int stockId) throws DaoException {
        return reader.openingBalance(itemId, stockId);
    }

    /**
     * Whether writing {@code incoming} is allowed, without throwing - so a batch can name every
     * item it would refuse rather than stop at the first.
     */
    public Verdict verdict(int itemId, int stockId, double incoming) throws DaoException {
        if (movements(itemId, stockId).isEmpty()) {
            return Verdict.OPEN;
        }
        return Math.abs(stored(itemId, stockId) - incoming) >= TOLERANCE ? Verdict.REFUSED : Verdict.UNCHANGED;
    }

    /**
     * Refuses a changed value once the item has moved in the warehouse.
     * <p>
     * An unchanged value passes, so saving an item's name is not turned into an error about a field
     * nobody touched; a changed one is refused rather than dropped, because the user typed a number
     * and is entitled to know it was not saved.
     *
     * @return whether the value may be written at all. False means the caller leaves it out: the
     *         value is unchanged, but writing it back is still a write of a closed entry
     */
    public boolean mayWrite(int itemId, int stockId, double incoming) throws DaoException {
        List<Reference> movements = movements(itemId, stockId);
        if (movements.isEmpty()) {
            return true;
        }
        double stored = stored(itemId, stockId);
        if (Math.abs(stored - incoming) >= TOLERANCE) {
            LanguageManager language = LanguageManager.getInstance();
            String moved = movements.stream().map(Reference::toString)
                    .collect(Collectors.joining(language.getString("opening.movements.separator")));
            throw new BusinessRuleException(language.getString("opening.error.locked.warehouse",
                    moved, stored, language.getString("opening.correction.items")));
        }
        return false;
    }

    /**
     * The words a refusal counts a movement in. The same keys the item-wide rule used, so the
     * sentence reads as it always did; an unknown movement fails here rather than printing a key.
     */
    public static String labelKey(ItemStockBalanceSql.Movement movement) {
        return switch (movement.column()) {
            case "quantityPurchase" -> "delete.ref.purchase_line";
            case "quantitySales" -> "delete.ref.sales_line";
            case "quantityPurchaseRe" -> "delete.ref.purchase_return_line";
            case "quantitySalesRe" -> "delete.ref.sales_return_line";
            case "fromStock", "toStock" -> "delete.ref.stock_transfer_line";
            case "adjustment" -> "opening.ref.stock_count_line";
            default -> throw new IllegalStateException("A movement with no words for a refusal: " + movement.column());
        };
    }

    /**
     * One row per item and warehouse the movement names, draft counts included - the condition
     * {@link ItemStockBalanceSql} sums over, less its {@code where}. Correlated with {@code ist}
     * when {@code correlated}, otherwise bound: stock id, then item id.
     */
    static String rowsOf(ItemStockBalanceSql.Movement movement, boolean correlated) {
        boolean aliased = movement.stock().contains(".");
        String from = aliased ? movement.from() : movement.from() + " m";
        String stock = aliased ? movement.stock() : "m." + movement.stock();
        String item = aliased ? movement.item() : "m." + movement.item();
        return "FROM " + from + " WHERE " + stock + (correlated ? " = ist.stock_id AND " : " = ? AND ")
                + item + (correlated ? " = ist.item_id" : " = ?");
    }

    /**
     * {@code (stock, item)} once per movement: one row, a count per movement named by its column.
     */
    public static final String MOVEMENT_COUNTS = ItemStockBalanceSql.MOVEMENTS.stream()
            .map(movement -> "(SELECT COUNT(*) " + rowsOf(movement, false) + ") AS " + movement.column())
            .collect(Collectors.joining(", ", "SELECT ", ""));

    /**
     * True for a row of {@code items_stock ist} whose item has moved in its warehouse - the
     * opening-balances screen's lock, the same text {@link #MOVEMENT_COUNTS} counts with.
     */
    public static final String MOVED = ItemStockBalanceSql.MOVEMENTS.stream()
            .map(movement -> "EXISTS (SELECT 1 " + rowsOf(movement, true) + ")")
            .collect(Collectors.joining(" OR ", "(", ")"));
}
