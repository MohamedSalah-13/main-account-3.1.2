package com.hamza.account.features.itemmerge;

import java.util.List;

/**
 * Every place in the schema that points at an item, declared once.
 * <p>
 * A merge is only as correct as this list. What makes that dangerous is that the
 * schema does not say "item" anywhere near half of these: the column is
 * {@code num} on {@code sales} and {@code purchase}, {@code item_id} on their
 * returns, {@code items_id} on {@code items_units}, and it appears twice on
 * {@code items_package}. The eleven are spread over four migrations (V1, V3, V5, V8).
 * <p>
 * Miss one and nothing fails: four of these carry {@code ON DELETE CASCADE}, so the
 * rows the merge forgot are quietly destroyed with the source item, and the rest
 * would refuse the delete - which is the good case, and the reason the merge ends by
 * going through {@code DeletionService} rather than deleting the row itself.
 * <p>
 * {@code ItemReferenceRegistryTest} reads the foreign keys straight out of the
 * migration files and fails the build when the schema names an item somewhere this
 * list does not - the same guarantee {@code WipeCatalogTest} gives the wipe.
 */
public final class ItemReferenceRegistry {

    private ItemReferenceRegistry() {
    }

    // ---- the four documents --------------------------------------------------
    //
    // The line carries its own price, buy price, profit and unit factor, so moving it
    // changes nothing but which item the figures are filed under. `num` on the two
    // invoices and `item_id` on the two returns is the same asymmetry DocumentTableSpec
    // already describes.

    public static final ItemReference SALES = new ItemReference("sales", "num", MergeAction.MOVE);
    public static final ItemReference SALES_RETURN = new ItemReference("sales_re", "item_id", MergeAction.MOVE);
    public static final ItemReference PURCHASE = new ItemReference("purchase", "num", MergeAction.MOVE);
    public static final ItemReference PURCHASE_RETURN = new ItemReference("purchase_re", "item_id", MergeAction.MOVE);

    /** The stock ledger. Nothing pairs an item with anything here, so a plain move. */
    public static final ItemReference STOCK_MOVEMENTS = new ItemReference("stock_movements", "item_id", MergeAction.MOVE);

    /**
     * The warehouse-transfer lines. The screens were removed with commit {@code 0853cf4}
     * and nothing writes the table now, but rows written before that still point at
     * items and would refuse the delete.
     */
    public static final ItemReference STOCK_TRANSFER_LINES = new ItemReference("stock_transfer_list", "item_id", MergeAction.MOVE);

    /**
     * A count sheet. {@code UNIQUE(count_id, item_id, unit_id)} means both items may
     * appear on the same sheet in the same unit, so the two rows are added together:
     * the view reads {@code counted_qty * type_value - system_qty}, and summing both
     * halves of that leaves the difference the counter found exactly as it was.
     */
    public static final ItemReference STOCK_COUNT_LINES = new ItemReference("stock_count_lines", "item_id", MergeAction.MERGE_ROW);

    /** {@code UNIQUE(item_id, stock_id)}: the target gets a row for any warehouse it lacks. */
    public static final ItemReference ITEMS_STOCK = new ItemReference("items_stock", "item_id", MergeAction.MERGE_ROW);

    /**
     * The item's own units. {@code UNIQUE(items_id, unit)} stops a unit the target
     * already has, and {@code UNIQUE(items_barcode)} is global - which is why the row
     * is moved rather than copied: moving takes its barcode with it.
     */
    public static final ItemReference ITEMS_UNITS = new ItemReference("items_units", "items_id", MergeAction.MOVE_IF_ABSENT);

    /** The extra barcodes (V3). {@code UNIQUE(barcode)} is global, so a code the target already holds stays behind. */
    public static final ItemReference ITEM_BARCODES = new ItemReference("item_barcodes", "item_id", MergeAction.MOVE_IF_ABSENT);

    /**
     * Compositions, and the only reference that appears twice: an item is both a thing
     * that has a package and a thing that can be one. There is no unique key on the
     * pair, so repointing can produce duplicates and - where the two merged items were
     * a composition of each other - a row pointing at itself.
     */
    public static final ItemReference ITEMS_PACKAGE_ITEM = new ItemReference("items_package", "item_id", MergeAction.MOVE_DEDUPE);
    public static final ItemReference ITEMS_PACKAGE_PACKAGE = new ItemReference("items_package", "package_id", MergeAction.MOVE_DEDUPE);

    public static final List<ItemReference> ALL = List.of(
            SALES, SALES_RETURN, PURCHASE, PURCHASE_RETURN,
            STOCK_MOVEMENTS, STOCK_TRANSFER_LINES,
            STOCK_COUNT_LINES, ITEMS_STOCK,
            ITEMS_UNITS, ITEM_BARCODES,
            ITEMS_PACKAGE_ITEM, ITEMS_PACKAGE_PACKAGE);

    /** The references a plain {@code UPDATE} moves, in the order the merge moves them. */
    public static final List<ItemReference> MOVABLE = ALL.stream()
            .filter(reference -> reference.action() == MergeAction.MOVE)
            .toList();

    /** The four documents - what "the operations that were carried out on it" means to a user. */
    public static final List<ItemReference> DOCUMENTS = List.of(SALES, SALES_RETURN, PURCHASE, PURCHASE_RETURN);

    public static boolean isDeclared(String table, String column) {
        return ALL.stream().anyMatch(reference ->
                reference.table().equalsIgnoreCase(table) && reference.column().equalsIgnoreCase(column));
    }
}
