package com.hamza.account.features.invoice;

import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Purchase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.hamza.account.features.invoice.QuickEntryRules.ENTRY_ROW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quick invoice's entry-row rules. {@code QuickInvoiceTable} holds the table and
 * the focus and cannot be tested without a JavaFX toolkit; these are the decisions it
 * makes, and every one of them was a defect at some point in this screen's short life.
 */
class QuickEntryRulesTest {

    /** The two column indexes {@code InvoiceTableCoordinator} declares. */
    private static final int BARCODE = 0;
    private static final int QUANTITY = 3;

    @Nested
    class TheEntryRow {

        @Test
        void isTheRowThatNamesNoItem() {
            assertTrue(QuickEntryRules.isEntryRow(entryRow()));
            assertFalse(QuickEntryRules.isEntryRow(line(1)));
        }

        /**
         * An empty table needs one as much as a table whose last row is a line - the
         * screen opens empty, and the first scan has to land somewhere.
         */
        @Test
        void isNeededWheneverTheLastRowIsNotOne() {
            assertTrue(QuickEntryRules.needsEntryRow(List.of()));
            assertTrue(QuickEntryRules.needsEntryRow(null));
            assertTrue(QuickEntryRules.needsEntryRow(List.of(line(1))));
            assertFalse(QuickEntryRules.needsEntryRow(List.of(line(1), entryRow())));
            assertFalse(QuickEntryRules.needsEntryRow(List.of(entryRow())));
        }

        /**
         * A line is appended before it, never after, so it is the last row or there is
         * none - a placeholder sitting in the middle is a line the pipeline refused and
         * not an entry surface.
         */
        @Test
        void isAlwaysTheLastRow() {
            assertEquals(1, QuickEntryRules.entryRowIndex(List.of(line(1), entryRow())));
            assertEquals(0, QuickEntryRules.entryRowIndex(List.of(entryRow())));
            assertEquals(ENTRY_ROW, QuickEntryRules.entryRowIndex(List.of()));
            assertEquals(ENTRY_ROW,
                    QuickEntryRules.entryRowIndex(List.of(entryRow(), line(1))));
        }
    }

    @Nested
    class Focus {

        @Test
        void isReportedOnlyWhereThereIsARow() {
            List<BasePurchasesAndSales> rows = List.of(line(1), entryRow());

            assertEquals(0, QuickEntryRules.focusedRow(rows, 0));
            assertEquals(1, QuickEntryRules.focusedRow(rows, 1));
            assertEquals(ENTRY_ROW, QuickEntryRules.focusedRow(rows, 2));
            assertEquals(ENTRY_ROW, QuickEntryRules.focusedRow(rows, ENTRY_ROW));
            assertEquals(ENTRY_ROW, QuickEntryRules.focusedRow(List.of(), 0));
        }
    }

    @Nested
    class Delete {

        @Test
        void removesAFocusedLine() {
            assertTrue(QuickEntryRules.canDelete(List.of(line(1), entryRow()), 0));
        }

        /** Deleting it would leave the next scan with nowhere to land. */
        @Test
        void neverRemovesTheEntryRow() {
            assertFalse(QuickEntryRules.canDelete(List.of(line(1), entryRow()), 1));
            assertFalse(QuickEntryRules.canDelete(List.of(entryRow()), 0));
        }

        @Test
        void doesNothingWhenTheFocusIsNowhere() {
            assertFalse(QuickEntryRules.canDelete(List.of(line(1), entryRow()), 5));
            assertFalse(QuickEntryRules.canDelete(List.of(), 0));
        }
    }

    @Nested
    class QuantityNudge {

        /**
         * Ctrl + on the entry row would leave a row reading "1" that still names no
         * item - a line that looks wrong rather than one that is not there.
         */
        @Test
        void isSwallowedOnTheEntryRowAndPassedOnALine() {
            List<BasePurchasesAndSales> rows = List.of(line(1), entryRow());

            assertTrue(QuickEntryRules.swallowsQuantityNudge(rows, 1));
            assertFalse(QuickEntryRules.swallowsQuantityNudge(rows, 0));
            assertFalse(QuickEntryRules.swallowsQuantityNudge(rows, 9));
        }
    }

    @Nested
    class EnterOpensACell {

        @Test
        void opensTheFocusedColumnWhenItCanBeTypedInto() {
            List<BasePurchasesAndSales> rows = List.of(line(1), entryRow());

            assertEquals(5, QuickEntryRules.columnToEditOnEnter(rows, 0, 5, true,
                    BARCODE, QUANTITY));
        }

        /**
         * A read-only column - the total, or the price for a user who may not change
         * it - would otherwise swallow the Enter and the operator would be stuck on it.
         */
        @Test
        void fallsBackToTheQuantityOnALine() {
            List<BasePurchasesAndSales> rows = List.of(line(1), entryRow());

            assertEquals(QUANTITY, QuickEntryRules.columnToEditOnEnter(rows, 0, 5, false,
                    BARCODE, QUANTITY));
            assertEquals(QUANTITY, QuickEntryRules.columnToEditOnEnter(rows, 0, -1, true,
                    BARCODE, QUANTITY));
        }

        /** On the entry row the fallback is the barcode: that is what a scan fills. */
        @Test
        void fallsBackToTheBarcodeOnTheEntryRow() {
            List<BasePurchasesAndSales> rows = List.of(line(1), entryRow());

            assertEquals(BARCODE, QuickEntryRules.columnToEditOnEnter(rows, 1, 5, false,
                    BARCODE, QUANTITY));
            assertEquals(BARCODE, QuickEntryRules.columnToEditOnEnter(rows, ENTRY_ROW, 5, true,
                    BARCODE, QUANTITY));
            assertEquals(BARCODE, QuickEntryRules.columnToEditOnEnter(List.of(), 0, 5, true,
                    BARCODE, QUANTITY));
        }
    }

    @Nested
    class AfterALineIsAdded {

        /** The caret lands on the new line's quantity, ready to be typed over. */
        @Test
        void landsOnTheNewLinesQuantity() {
            BasePurchasesAndSales added = line(7);
            List<BasePurchasesAndSales> rows = List.of(line(1), added, entryRow());

            assertEquals(1, QuickEntryRules.rowToEditAfterAdd(rows, added));
        }

        /**
         * null is the cancelled expiry dialog - the one case the pipeline adds nothing -
         * and a row the table is not holding is a scan merged into somewhere else. Both
         * leave the screen waiting for the next scan rather than pointing at a line.
         */
        @Test
        void goesBackToTheEntryRowWhenThereIsNoNewLine() {
            List<BasePurchasesAndSales> rows = List.of(line(1), entryRow());

            assertEquals(ENTRY_ROW, QuickEntryRules.rowToEditAfterAdd(rows, null));
            assertEquals(ENTRY_ROW, QuickEntryRules.rowToEditAfterAdd(rows, line(7)));
            assertEquals(ENTRY_ROW, QuickEntryRules.rowToEditAfterAdd(null, line(7)));
        }
    }

    /**
     * The sequence the screen actually runs: scan, scan, delete the first line, empty
     * the table. The entry row survives all of it and stays last, which is the single
     * invariant every rule above is written to keep.
     */
    @Test
    void theEntryRowSurvivesAWholeInvoice() {
        List<BasePurchasesAndSales> rows = new ArrayList<>(List.of(entryRow()));

        for (int itemId : new int[]{1, 2}) {
            rows.add(rows.size() - 1, line(itemId));
            assertFalse(QuickEntryRules.needsEntryRow(rows));
        }
        assertEquals(2, QuickEntryRules.entryRowIndex(rows));

        assertTrue(QuickEntryRules.canDelete(rows, 0));
        rows.remove(0);
        assertFalse(QuickEntryRules.needsEntryRow(rows));

        rows.clear();
        assertTrue(QuickEntryRules.needsEntryRow(rows));
        assertEquals(ENTRY_ROW, QuickEntryRules.entryRowIndex(rows));
    }

    private static BasePurchasesAndSales entryRow() {
        Purchase row = new Purchase();
        row.setItems(new ItemsModel());
        return row;
    }

    private static BasePurchasesAndSales line(int itemId) {
        Purchase row = new Purchase();
        ItemsModel item = new ItemsModel();
        item.setId(itemId);
        row.setItems(item);
        row.setQuantity(1);
        row.setPrice(10);
        row.setTotal(10);
        return row;
    }
}
