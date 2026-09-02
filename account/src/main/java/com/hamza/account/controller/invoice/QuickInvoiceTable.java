package com.hamza.account.controller.invoice;

import com.hamza.account.controller.search.ItemSuggestionField;
import com.hamza.account.features.invoice.InvoiceItemSelection;
import com.hamza.account.features.invoice.InvoiceLineDraft;
import com.hamza.account.features.invoice.InvoiceLineTotals;
import com.hamza.account.features.invoice.QuickEntryRules;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.ItemsModel;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.util.converter.DefaultStringConverter;
import javafx.util.converter.DoubleStringConverter;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The quick invoice's single entry surface: the lines table itself.
 *
 * <p>There is no barcode/name/price/quantity form above the table on this screen. The
 * table keeps one trailing <b>entry row</b> - a placeholder that names no item - and the
 * operator scans or types straight into it. Committing it turns it into a real line and
 * a fresh entry row takes its place, so a hundred items are a hundred scans and nothing
 * else.
 *
 * <p>Two rules make that safe, and both were missing when the screen was first written:
 *
 * <ul>
 *   <li>The entry row is a control, not a sale. {@link InvoiceLineTotals#isPlaceholder}
 *       is what every total, count and validation filters it out by - the row is never
 *       counted, never saved and never makes the invoice look invalid.</li>
 *   <li>A line is added through the same pipeline the standard screen uses
 *       ({@link Host#addLine}), not by setting fields on the placeholder. That pipeline
 *       is where the expiry-batch dialog, the repeated-item merge, the sell-below-cost
 *       refusal, the stock check and the low-stock alert live; writing the row directly
 *       skipped every one of them.</li>
 * </ul>
 *
 * <h2>Keyboard</h2>
 * The screen is meant to be usable without a mouse at all:
 * <table border="1">
 *   <caption>Quick invoice keys</caption>
 *   <tr><td>Type / scan + Enter in الباركود</td><td>resolve the item, add the line, land on its quantity</td></tr>
 *   <tr><td>Type in الاسم</td><td>live suggestions; Up/Down to move, Enter to pick, F4 to create</td></tr>
 *   <tr><td>Enter on the quantity</td><td>commit, open a fresh entry row and focus its barcode</td></tr>
 *   <tr><td>Enter on any other cell</td><td>start editing it</td></tr>
 *   <tr><td>Insert</td><td>jump back to the entry row's barcode from anywhere in the window</td></tr>
 *   <tr><td>F3</td><td>jump to the entry row's name and search by name, from anywhere</td></tr>
 *   <tr><td>Delete</td><td>remove the focused line (never the entry row)</td></tr>
 *   <tr><td>Ctrl + / Ctrl -</td><td>quantity up and down (from {@code UpdateQuantity})</td></tr>
 *   <tr><td>Alt + Up/Down</td><td>move the line (from {@code InvoiceTableCoordinator})</td></tr>
 * </table>
 */
public final class QuickInvoiceTable {

    /** What the quick table needs from the screen that owns it. */
    public interface Host {
        /** Resolves a scanned or typed code, honouring the scale-barcode settings. */
        InvoiceItemSelection selectByBarcode(String barcode) throws Exception;

        /** Resolves an item chosen by name, for the current warehouse and price tier. */
        InvoiceItemSelection selectByName(String itemName) throws Exception;

        /**
         * Adds one line through the screen's shared pipeline. Returns the resulting
         * row, or null when the user cancelled (the expiry dialog is the only case).
         */
        BasePurchasesAndSales addLine(InvoiceLineDraft draft) throws Exception;

        /** Applies an edited quantity, with the same validation the standard table uses. */
        void editQuantity(BasePurchasesAndSales line, double quantity) throws Exception;

        /** Candidate items for the name column's suggestions. */
        List<ItemsModel> searchItems(String text) throws Exception;

        /** The price to show beside a suggestion, on this screen's tier. */
        double priceOf(ItemsModel item);

        /** Opens the item screen so a missing item can be created. */
        void createItem(String typedText);

        /** An empty row of the concrete line type this document family uses. */
        BasePurchasesAndSales newEntryRow();

        void handleError(Exception error, boolean scaleBarcode);

        void totalsChanged();
    }

    /**
     * The two keys that mean "go back to entering items" are scene accelerators, not
     * table handlers. A handler on the table only fires while the table already has the
     * focus - so pressing Insert after touching the warehouse combo did nothing, which
     * is the one moment the operator most needs it.
     */
    private static final KeyCombination JUMP_TO_ENTRY_ROW = new KeyCodeCombination(KeyCode.INSERT);
    private static final KeyCombination SEARCH_ENTRY_ROW_BY_NAME = new KeyCodeCombination(KeyCode.F3);

    private final TableView<BasePurchasesAndSales> table;
    private final Host host;

    public QuickInvoiceTable(TableView<BasePurchasesAndSales> table, Host host) {
        this.table = Objects.requireNonNull(table, "table");
        this.host = Objects.requireNonNull(host, "host");
    }

    @SuppressWarnings("unchecked")
    public void configure() {
        TableColumn<BasePurchasesAndSales, String> barcodeColumn = column(
                InvoiceTableCoordinator.BARCODE_COLUMN);
        TableColumn<BasePurchasesAndSales, String> nameColumn = column(
                InvoiceTableCoordinator.NAME_COLUMN);
        TableColumn<BasePurchasesAndSales, Double> quantityColumn =
                (TableColumn<BasePurchasesAndSales, Double>) (TableColumn<?, ?>)
                        table.getColumns().get(InvoiceTableCoordinator.QUANTITY_COLUMN);

        // Only the entry row takes an item: an existing line's item is changed by
        // deleting the line and scanning again, which keeps one code path for adding
        // and leaves the merge and expiry rules with nothing to undo.
        barcodeColumn.setCellFactory(view -> new EntryRowOnlyTextCell());
        barcodeColumn.setOnEditCommit(event -> selectByBarcode(event.getNewValue()));

        nameColumn.setCellFactory(view -> new ItemSuggestionCell(host, this::selectByName));

        quantityColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        quantityColumn.setOnEditCommit(event -> {
            BasePurchasesAndSales line = event.getRowValue();
            try {
                host.editQuantity(line, event.getNewValue() == null ? 1 : event.getNewValue());
            } catch (Exception e) {
                table.refresh();
                host.handleError(e, false);
                editCell(event.getTablePosition().getRow(), InvoiceTableCoordinator.QUANTITY_COLUMN);
                return;
            }
            table.refresh();
            host.totalsChanged();
            focusEntryRow();
        });

        // The entry row reads differently from a line of the invoice, so it looks
        // different: without that the operator cannot tell at a glance whether the
        // caret is on the row being typed or on one already entered.
        table.setRowFactory(view -> new javafx.scene.control.TableRow<>() {
            @Override
            protected void updateItem(BasePurchasesAndSales line, boolean empty) {
                super.updateItem(line, empty);
                getStyleClass().remove("quick-entry-row");
                if (!empty && isEntryRow(line)) {
                    getStyleClass().add("quick-entry-row");
                }
            }
        });

        ensureEntryRow();
        table.getItems().addListener((javafx.collections.ListChangeListener<BasePurchasesAndSales>) change -> {
            if (QuickEntryRules.needsEntryRow(table.getItems())) {
                // Either the table was emptied, or the entry row itself was just removed
                // (the row delete button) while real lines remain.
                Platform.runLater(this::ensureEntryRow);
            }
        });
        configureKeys();
        installAccelerators();
        Platform.runLater(this::focusEntryRow);
    }

    /** Adds the trailing entry row back if it is missing. */
    public void ensureEntryRow() {
        if (QuickEntryRules.needsEntryRow(table.getItems())) {
            table.getItems().add(host.newEntryRow());
        }
    }

    /** Puts the caret in the entry row's barcode cell, ready for the next scan. */
    public void focusEntryRow() {
        ensureEntryRow();
        editCell(table.getItems().size() - 1, InvoiceTableCoordinator.BARCODE_COLUMN);
    }

    /** Opens the entry row's name cell, ready to search for an item by name. */
    public void searchEntryRowByName() {
        ensureEntryRow();
        editCell(table.getItems().size() - 1, InvoiceTableCoordinator.NAME_COLUMN);
    }

    /**
     * Binds Insert and F3 to the window rather than to the table. The scene is not
     * there when this class is configured - the screen is still being built - so they
     * are installed when it arrives, and removed again if the table leaves it.
     */
    private void installAccelerators() {
        table.sceneProperty().addListener((observable, oldScene, scene) -> {
            if (oldScene != null) {
                oldScene.getAccelerators().remove(JUMP_TO_ENTRY_ROW);
                oldScene.getAccelerators().remove(SEARCH_ENTRY_ROW_BY_NAME);
            }
            bindTo(scene);
        });
        bindTo(table.getScene());
    }

    private void bindTo(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.getAccelerators().put(JUMP_TO_ENTRY_ROW, this::focusEntryRow);
        scene.getAccelerators().put(SEARCH_ENTRY_ROW_BY_NAME, this::searchEntryRowByName);
    }

    /** Whether this row is the trailing entry row rather than a line of the invoice. */
    public static boolean isEntryRow(BasePurchasesAndSales line) {
        return QuickEntryRules.isEntryRow(line);
    }

    private void configureKeys() {
        // A filter, so these keys are decided before the table's own navigation - but
        // never while a cell editor is open, where every keystroke belongs to the editor.
        table.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (table.getEditingCell() != null) {
                return;
            }
            switch (event.getCode()) {
                case ENTER -> {
                    editFocusedCell();
                    event.consume();
                }
                case DELETE -> {
                    deleteFocusedLine();
                    event.consume();
                }
                case ADD, SUBTRACT -> consumeOnEntryRow(event);
                case PLUS, MINUS, EQUALS -> {
                    if (event.isControlDown()) {
                        consumeOnEntryRow(event);
                    }
                }
                default -> {
                }
            }
        });
    }

    /**
     * The entry row has no item, so nudging its quantity would leave a row reading
     * "1" that still cannot be saved. {@code UpdateQuantity} does not know about the
     * entry row, so the key is stopped here instead.
     */
    private void consumeOnEntryRow(KeyEvent event) {
        if (QuickEntryRules.swallowsQuantityNudge(table.getItems(), focusedRow())) {
            event.consume();
        }
    }

    private void editFocusedCell() {
        int row = focusedRow();
        if (row == QuickEntryRules.ENTRY_ROW) {
            focusEntryRow();
            return;
        }
        TableColumn<BasePurchasesAndSales, ?> column =
                table.getFocusModel().getFocusedCell().getTableColumn();
        int index = column == null ? -1 : table.getColumns().indexOf(column);
        editCell(row, QuickEntryRules.columnToEditOnEnter(table.getItems(), row, index,
                column != null && column.isEditable(),
                InvoiceTableCoordinator.BARCODE_COLUMN,
                InvoiceTableCoordinator.QUANTITY_COLUMN));
    }

    private void deleteFocusedLine() {
        int row = focusedRow();
        if (!QuickEntryRules.canDelete(table.getItems(), row)) {
            return;
        }
        table.getItems().remove(row);
        table.refresh();
        host.totalsChanged();
    }

    /** The focused row, or {@link QuickEntryRules#ENTRY_ROW} when the focus is nowhere. */
    private int focusedRow() {
        var focused = table.getFocusModel().getFocusedCell();
        return QuickEntryRules.focusedRow(table.getItems(),
                focused == null ? QuickEntryRules.ENTRY_ROW : focused.getRow());
    }

    private void selectByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            focusEntryRow();
            return;
        }
        try {
            commit(host.selectByBarcode(barcode));
        } catch (Exception e) {
            host.handleError(e, false);
            focusEntryRow();
        }
    }

    private void selectByName(ItemsModel item) {
        if (item == null) {
            focusEntryRow();
            return;
        }
        try {
            commit(host.selectByName(item.getNameItem()));
        } catch (Exception e) {
            host.handleError(e, false);
            focusEntryRow();
        }
    }

    /**
     * Turns a resolved item into a line. The entry row is taken out first so the new
     * line is appended after the last real one rather than after the placeholder, and
     * put back afterwards - which is also what makes a merge land on the right row.
     */
    private void commit(InvoiceItemSelection selection) throws Exception {
        BasePurchasesAndSales entryRow = removeEntryRow();
        BasePurchasesAndSales added;
        try {
            added = host.addLine(new InvoiceLineDraft(selection.item(), selection.selectedUnit(),
                    selection.quantity(), selection.price(), 0, null));
        } catch (Exception e) {
            restoreEntryRow(entryRow);
            throw e;
        }
        restoreEntryRow(entryRow);
        host.totalsChanged();
        int row = QuickEntryRules.rowToEditAfterAdd(table.getItems(), added);
        if (row == QuickEntryRules.ENTRY_ROW) {
            focusEntryRow();
        } else {
            editCell(row, InvoiceTableCoordinator.QUANTITY_COLUMN);
        }
    }

    private BasePurchasesAndSales removeEntryRow() {
        int row = QuickEntryRules.entryRowIndex(table.getItems());
        return row == QuickEntryRules.ENTRY_ROW ? null : table.getItems().remove(row);
    }

    private void restoreEntryRow(BasePurchasesAndSales entryRow) {
        if (entryRow != null && !table.getItems().contains(entryRow)) {
            table.getItems().add(entryRow);
        } else {
            ensureEntryRow();
        }
    }

    /**
     * Starts editing one cell. The two nested {@code runLater}s are not superstition:
     * {@code InvoiceTableCoordinator} restores focus to the table when a cell editor
     * closes, and an edit queued before that restoration is silently dropped.
     */
    private void editCell(int row, int columnIndex) {
        if (row < 0 || columnIndex < 0 || columnIndex >= table.getColumns().size()) {
            return;
        }
        Platform.runLater(() -> {
            if (row >= table.getItems().size()) {
                return;
            }
            TableColumn<BasePurchasesAndSales, ?> column = table.getColumns().get(columnIndex);
            table.requestFocus();
            table.getSelectionModel().clearSelection();
            table.getSelectionModel().select(row, column);
            table.getFocusModel().focus(row, column);
            table.scrollTo(row);
            Platform.runLater(() -> {
                if (row >= table.getItems().size()) {
                    return;
                }
                table.getFocusModel().focus(row, column);
                table.edit(row, column);
            });
        });
    }

    @SuppressWarnings("unchecked")
    private TableColumn<BasePurchasesAndSales, String> column(int index) {
        return (TableColumn<BasePurchasesAndSales, String>) (TableColumn<?, ?>)
                table.getColumns().get(index);
    }

    /** A text cell that refuses to open on anything but the entry row. */
    private static final class EntryRowOnlyTextCell extends TextFieldTableCell<BasePurchasesAndSales, String> {
        private EntryRowOnlyTextCell() {
            super(new DefaultStringConverter());
        }

        @Override
        public void startEdit() {
            if (getTableRow() == null || !isEntryRow(getTableRow().getItem())) {
                return;
            }
            super.startEdit();
        }
    }

    /**
     * The name cell of the entry row: an {@link ItemSuggestionField} shown in place,
     * so picking an item by name never leaves the table or the keyboard. It replaced a
     * modal picker that opened with an empty search box and threw away whatever had
     * just been typed into the cell.
     */
    private static final class ItemSuggestionCell extends TableCell<BasePurchasesAndSales, String> {
        private final Host host;
        private final Consumer<ItemsModel> onChosen;
        private ItemSuggestionField field;

        private ItemSuggestionCell(Host host, Consumer<ItemsModel> onChosen) {
            this.host = host;
            this.onChosen = onChosen;
        }

        @Override
        public void startEdit() {
            if (getTableRow() == null || !isEntryRow(getTableRow().getItem())) {
                return;
            }
            super.startEdit();
            setText(null);
            setGraphic(editor());
            field.setText("");
            Platform.runLater(field::requestFocus);
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setGraphic(null);
            setText(getItem());
        }

        @Override
        protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else if (isEditing()) {
                setText(null);
                setGraphic(editor());
            } else {
                setText(value);
                setGraphic(null);
            }
        }

        private ItemSuggestionField editor() {
            if (field == null) {
                field = new ItemSuggestionField(host::searchItems);
                field.setPriceResolver(host::priceOf);
                field.setOnCreateRequested(host::createItem);
                field.chosenItemProperty().addListener((observable, oldItem, item) -> {
                    if (item == null) {
                        return;
                    }
                    // cancelEdit, not commitEdit: the row is replaced by the pipeline,
                    // so there is no string for the table to write back.
                    cancelEdit();
                    onChosen.accept(item);
                });
            }
            return field;
        }
    }
}
