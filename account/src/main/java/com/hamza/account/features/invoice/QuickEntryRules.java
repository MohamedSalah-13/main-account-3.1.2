package com.hamza.account.features.invoice;

import com.hamza.account.model.base.BasePurchasesAndSales;

import java.util.List;

/**
 * The quick invoice's decisions about its trailing entry row, with no JavaFX in them.
 *
 * <p>{@code QuickInvoiceTable} owns the table, the cell editors and the focus; what it
 * cannot own is a test, since every one of those needs a running toolkit. The questions
 * it asks about the rows are ordinary ones - is this row the entry row, may it be
 * deleted, which cell should Enter open, where does the caret land after a line is
 * added - and each was written inline where nothing could check it. They live here
 * instead, over a plain list, and {@code QuickEntryRulesTest} pins them.
 *
 * <p>The entry row is defined in exactly one place, {@link InvoiceLineTotals#isPlaceholder}:
 * a row that names no item. Every rule below is a consequence of that, so a change to
 * what counts as a placeholder moves the whole screen at once instead of moving five
 * of its six behaviours.
 */
public final class QuickEntryRules {

    /** The caret belongs on the entry row rather than on any existing cell. */
    public static final int ENTRY_ROW = -1;

    private QuickEntryRules() {
    }

    /** Whether this row is the trailing entry row rather than a line of the invoice. */
    public static boolean isEntryRow(BasePurchasesAndSales line) {
        return InvoiceLineTotals.isPlaceholder(line);
    }

    /**
     * Whether an entry row has to be added back. The table is never left without one -
     * an operator whose scan has nowhere to land is an operator with a dead screen -
     * so this is asked after every change to the rows, not only after a delete.
     */
    public static boolean needsEntryRow(List<? extends BasePurchasesAndSales> rows) {
        return rows == null || rows.isEmpty() || !isEntryRow(rows.get(rows.size() - 1));
    }

    /**
     * Where the entry row is, or {@link #ENTRY_ROW} when there is none. It is always
     * the last row: a line is appended before it, never after.
     */
    public static int entryRowIndex(List<? extends BasePurchasesAndSales> rows) {
        return needsEntryRow(rows) ? ENTRY_ROW : rows.size() - 1;
    }

    /** The focused row, or {@link #ENTRY_ROW} when the focus is outside the rows. */
    public static int focusedRow(List<? extends BasePurchasesAndSales> rows, int focused) {
        if (rows == null || focused < 0 || focused >= rows.size()) {
            return ENTRY_ROW;
        }
        return focused;
    }

    /**
     * Delete removes the focused line. The entry row is not a line and survives it -
     * deleting it would take away the surface the next scan is typed into.
     */
    public static boolean canDelete(List<? extends BasePurchasesAndSales> rows, int focused) {
        int row = focusedRow(rows, focused);
        return row != ENTRY_ROW && !isEntryRow(rows.get(row));
    }

    /**
     * Ctrl + and Ctrl - nudge a line's quantity. On the entry row they are swallowed:
     * the row names no item, so nudging it would leave a row reading "1" that still
     * cannot be saved and reads like a line that is somehow wrong.
     */
    public static boolean swallowsQuantityNudge(List<? extends BasePurchasesAndSales> rows, int focused) {
        int row = focusedRow(rows, focused);
        return row != ENTRY_ROW && isEntryRow(rows.get(row));
    }

    /**
     * Which column Enter opens. A column that can be typed into is opened as it is;
     * anything else falls back to the column that row is there for - the barcode on
     * the entry row, since that is what the next scan fills, and the quantity on a
     * line, which is the only field of a saved line the quick screen edits in place.
     *
     * @param focusedColumn the focused column's index, or negative when none is
     * @param columnEditable whether that column accepts an edit at all
     */
    public static int columnToEditOnEnter(List<? extends BasePurchasesAndSales> rows, int focused,
                                          int focusedColumn, boolean columnEditable,
                                          int barcodeColumn, int quantityColumn) {
        int row = focusedRow(rows, focused);
        if (row == ENTRY_ROW) {
            return barcodeColumn;
        }
        if (focusedColumn >= 0 && columnEditable) {
            return focusedColumn;
        }
        return isEntryRow(rows.get(row)) ? barcodeColumn : quantityColumn;
    }

    /**
     * Where the caret goes once a line has been added: onto the new line's quantity,
     * ready to be typed over. {@link #ENTRY_ROW} when there is no such line - the user
     * cancelled the expiry dialog, or the pipeline merged the scan into a row this
     * table is not holding - and the next scan is then what the screen waits for.
     */
    public static int rowToEditAfterAdd(List<? extends BasePurchasesAndSales> rows,
                                        BasePurchasesAndSales added) {
        if (rows == null || added == null) {
            return ENTRY_ROW;
        }
        int row = rows.indexOf(added);
        return row < 0 ? ENTRY_ROW : row;
    }
}
