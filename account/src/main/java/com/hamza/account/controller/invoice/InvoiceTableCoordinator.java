package com.hamza.account.controller.invoice;

import com.hamza.account.features.invoice.InvoiceItemSelectionService;
import com.hamza.account.features.invoice.InvoiceLineEditService;
import com.hamza.account.features.invoice.InvoiceLineService;
import com.hamza.account.features.key_setting.MoveRow;
import com.hamza.account.features.key_setting.UpdateInterface;
import com.hamza.account.features.key_setting.UpdateQuantity;
import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.otherSetting.ButtonDeleteRow;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.button.button_column.ButtonColumn;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.table.columnEdit.NumberTextConverter;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import static com.hamza.controlsfx.table.columnEdit.ColumnSetting.addColumn;

/** Owns the JavaFX wiring for the editable invoice-lines table. */
public final class InvoiceTableCoordinator<T extends BasePurchasesAndSales> {

    /** Column order as built by {@link #configure()} — other screens that reach into the
     * table by index (e.g. the quick-entry mode) must stay in sync with this layout. */
    public static final int BARCODE_COLUMN = 0;
    public static final int NAME_COLUMN = 1;
    public static final int TYPE_COLUMN = 2;
    public static final int QUANTITY_COLUMN = 3;
    public static final int PRICE_COLUMN = 4;
    public static final int TOTAL_COLUMN = 5;
    public static final int DISCOUNT_COLUMN = 6;
    public static final int TOTAL_AFTER_COLUMN = 7;

    private final TableView<T> table;
    private final ObservableList<T> lines;
    private final InvoiceLineEditService editService;
    private final IntSupplier priceTier;
    private final BooleanSupplier updateCatalogPrice;
    private final Runnable totalsChanged;
    private final Class<?> menuOwner;
    private final boolean showAdminMenu;
    private final boolean mayEditCatalog;
    private final UnitRepricer unitRepricer;

    /**
     * The unit a line may be switched to and the price it carries on this screen's tier - what
     * {@link InvoiceItemSelectionService#selectUnit} answers for the form above the table.
     */
    @FunctionalInterface
    public interface UnitRepricer {
        InvoiceItemSelectionService.UnitSelection select(ItemsModel item, String unitName, int priceTier)
                throws Exception;
    }

    /**
     * @param mayEditCatalog whether this user may write the item behind a line - the name
     *                       cell and the "update the item's price as you type" option both
     *                       hang off it. {@code InvoiceItemCatalogService} refuses either
     *                       way; this is so the screen does not offer what it will refuse.
     *                       A permission, so it does not change while the screen is open.
     */
    public InvoiceTableCoordinator(TableView<T> table, ObservableList<T> lines,
                                   InvoiceLineEditService editService,
                                   IntSupplier priceTier,
                                   BooleanSupplier updateCatalogPrice,
                                   Runnable totalsChanged,
                                   Class<?> menuOwner,
                                   boolean showAdminMenu,
                                   boolean mayEditCatalog,
                                   UnitRepricer unitRepricer) {
        this.table = Objects.requireNonNull(table, "table");
        this.lines = Objects.requireNonNull(lines, "lines");
        this.editService = Objects.requireNonNull(editService, "editService");
        this.priceTier = Objects.requireNonNull(priceTier, "priceTier");
        this.updateCatalogPrice = Objects.requireNonNull(updateCatalogPrice, "updateCatalogPrice");
        this.totalsChanged = Objects.requireNonNull(totalsChanged, "totalsChanged");
        this.menuOwner = Objects.requireNonNull(menuOwner, "menuOwner");
        this.showAdminMenu = showAdminMenu;
        this.mayEditCatalog = mayEditCatalog;
        this.unitRepricer = Objects.requireNonNull(unitRepricer, "unitRepricer");
    }

    public void configure() {
        table.getColumns().addAll(InvoiceTableCoordinator.<T>amountColumns());
        addIdentityColumns();
        addDeleteColumn();
        applyDefaultWidths();
        // Only the standard screen ever shows this: the quick screen keeps a trailing
        // entry row, so its table is never empty. See QuickInvoiceTable.
        table.setPlaceholder(new Label(LanguageManager.getInstance().getString("invoice.lines.empty")));
        table.setItems(lines);
        configureEdits();
        configureSelectionAndKeys();
        configureTotalsRefresh();

        if (showAdminMenu) {
            TableSetting.tableMenuSetting(menuOwner, table);
        }
    }

    /**
     * Quantity, price, total, discount, total after discount - in that order, from
     * {@link #QUANTITY_COLUMN} on once the identity columns are inserted in front.
     * <p>
     * <b>Bound to the line's properties, not read off its getters.</b> An edit to the
     * quantity, the price or the discount - from a cell, from the +/- keys, or from a
     * repeated scan merged into an existing line - recalculates the total on the model and
     * refreshes nothing. A snapshot column went on showing the old total and total after
     * discount while the footer, which sums the model, showed the new one.
     * <p>
     * <b>Written as money and as a quantity</b>, the way every other screen writes them:
     * {@code 7.50} and {@code 1,050.00}, not {@code 7.5} and {@code 1050.0}. The three that
     * are edited get their cell from {@link #configureEdits()}, whose converter writes the
     * same way and reads it back.
     */
    static <L extends BasePurchasesAndSales> List<TableColumn<L, Number>> amountColumns() {
        return List.of(
                Columns.asQuantity(Columns.<L, Number>observable(NamesTables.QUANTITY,
                        BasePurchasesAndSales::quantityProperty)),
                Columns.asMoney(Columns.<L, Number>observable(NamesTables.PRICE,
                        BasePurchasesAndSales::priceProperty)),
                Columns.asMoney(Columns.<L, Number>observable(NamesTables.TOTAL,
                        BasePurchasesAndSales::totalProperty)),
                Columns.asMoney(Columns.<L, Number>observable(NamesTables.DISCOUNT,
                        BasePurchasesAndSales::discountProperty)),
                Columns.asMoney(Columns.<L, Number>observable(NamesTables.TOTAL_AFTER,
                        BasePurchasesAndSales::total_after_discountProperty)));
    }

    private void addIdentityColumns() {
        addColumn(table, LanguageManager.getInstance().getString("barcode"), BARCODE_COLUMN,
                (Callback<TableColumn.CellDataFeatures<T, String>, ObservableValue<String>>)
                        features -> features.getValue().getItems().barcodeProperty());
        addColumn(table, LanguageManager.getInstance().getString("name"), NAME_COLUMN,
                (Callback<TableColumn.CellDataFeatures<T, String>, ObservableValue<String>>)
                        features -> features.getValue().getItems().nameItemProperty());
        addColumn(table, LanguageManager.getInstance().getString("type"), TYPE_COLUMN,
                (Callback<TableColumn.CellDataFeatures<T, String>, ObservableValue<String>>)
                        features -> features.getValue().getUnitsType().unit_nameProperty());
    }

    /**
     * Widths the table opens with, and the name takes what is left. Without them every column
     * opened at JavaFX's 80 points with the rest of the window empty beside them, so an item's
     * name read "زي..." on a screen with half its width unused - on the standard screen as much as
     * the quick one, for everybody but the administrator, whose column menu stores widths of its
     * own and is applied after this, so a width somebody chose still wins. The other columns may
     * grow a little and no more, which is what leaves the spare room to the name.
     * <p>
     * Whether the columns stretch to the table's width at all is not decided here: that is the
     * "fill the available width" choice in the settings, which {@code ThemeManager} applies to
     * every table once the screen is on its scene, over any policy set now.
     */
    private void applyDefaultWidths() {
        double[] widths = {135, 300, 100, 80, 95, 105, 80, 115};
        for (int i = 0; i < widths.length; i++) {
            TableColumn<T, ?> column = table.getColumns().get(i);
            column.setPrefWidth(widths[i]);
            if (i != NAME_COLUMN) {
                column.setMaxWidth(widths[i] * 1.3);
            }
        }
        table.getColumns().get(NAME_COLUMN).setMinWidth(160);
        TableColumn<T, ?> delete = table.getColumns().get(TOTAL_AFTER_COLUMN + 1);
        delete.setPrefWidth(64);
        delete.setMinWidth(64);
        delete.setMaxWidth(64);
    }

    private void addDeleteColumn() {
        table.getColumns().add(new ButtonColumn<>(new ButtonDeleteRow() {
            @Override
            public void action(int index) {
                if (!isLineAt(index)) {
                    return;
                }
                table.getItems().remove(index);
                table.refresh();
            }

            /** The quick screen's entry row is not a line, so it offers nothing to delete. */
            @Override
            public boolean isButtonDisabled(int index) {
                return !isLineAt(index);
            }
        }));
    }

    private void configureEdits() {
        // Renaming an item from a line writes the item, so without items.update the cell
        // simply does not open. The quick screen replaces this column's cell factory with
        // its own item search and never commits an edit through it, so it is unaffected.
        if (mayEditCatalog) {
            enable(NAME_COLUMN, new DefaultStringConverter(), (line, value) ->
                    editService.editName(line, value));
        }
        enable(QUANTITY_COLUMN, NumberTextConverter.quantity(), (line, value) ->
                editService.editQuantity(line, value));
        // The price of this line is always editable; carrying it back to the item is what
        // needs the permission. Dropping the flag here rather than refusing the whole edit
        // keeps the ordinary "sell this one cheaper" working for a cashier.
        enable(PRICE_COLUMN, NumberTextConverter.money(), (line, value) ->
                editService.editPrice(line, value, mayEditCatalog && updateCatalogPrice.getAsBoolean(),
                        priceTier.getAsInt()));
        enable(DISCOUNT_COLUMN, NumberTextConverter.money(), (line, value) ->
                editService.editDiscount(line, value));
        configureUnitEdits();
    }

    /**
     * The unit is chosen on the line itself, from that item's own units. It was the one field of
     * a line that could only be set on the form above the table - so on the quick screen, which
     * has no form, an item whose carton carries no barcode could not be sold by the carton at all.
     */
    private void configureUnitEdits() {
        TableColumn<T, String> column = column(TYPE_COLUMN);
        column.setCellFactory(InvoiceLineCells.unit());
        column.setOnEditCommit(event -> commitEdit(event.getRowValue(), event.getNewValue(),
                (line, unitName) -> editService.editUnit(line,
                        unitRepricer.select(line.getItems(), unitName, priceTier.getAsInt()))));
    }

    private <V> void enable(int columnIndex, StringConverter<V> converter, LineEdit<T, V> edit) {
        TableColumn<T, V> column = column(columnIndex);
        column.setCellFactory(InvoiceLineCells.text(converter));
        column.setOnEditCommit(event -> commitEdit(event.getRowValue(), event.getNewValue(), edit));
    }

    /**
     * Applies one committed edit to its own row - {@code getRowValue()}, never the row at the
     * edited index, which a reload may have moved - and redraws the table whichever way it went,
     * so a refused edit never leaves the typed value on screen.
     */
    private <V> void commitEdit(T line, V value, LineEdit<T, V> edit) {
        try {
            edit.apply(line, value);
            totalsChanged.run();
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("error.operation.table.update"), e);
        } finally {
            table.refresh();
        }
    }

    @SuppressWarnings("unchecked")
    private <V> TableColumn<T, V> column(int index) {
        return (TableColumn<T, V>) table.getColumns().get(index);
    }

    private boolean isLineAt(int index) {
        return index >= 0 && index < table.getItems().size()
                && InvoiceLineCells.isLine(table.getItems().get(index));
    }

    @FunctionalInterface
    private interface LineEdit<L, V> {
        void apply(L line, V value) throws Exception;
    }

    private void configureSelectionAndKeys() {
        table.setEditable(true);
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setOnKeyPressed(createKeyHandler());
    }

    private void configureTotalsRefresh() {
        table.editingCellProperty().addListener((observable, oldPosition, newPosition) -> {
            totalsChanged.run();
            if (newPosition == null) {
                Platform.runLater(table::requestFocus);
            }
        });
        lines.addListener((ListChangeListener<T>) change -> totalsChanged.run());
    }

    private EventHandler<KeyEvent> createKeyHandler() {
        MoveRow<T> moveRow = new MoveRow<>(table, lines);
        EventHandler<KeyEvent> quantityHandler = quantityKeyHandler();
        return event -> {
            if (event.isAltDown()) {
                switch (event.getCode()) {
                    case UP -> {
                        moveRow.moveSelectedRowsUp();
                        event.consume();
                        return;
                    }
                    case DOWN -> {
                        moveRow.moveSelectedRowsDown();
                        event.consume();
                        return;
                    }
                    default -> { }
                }
            }
            if (!event.isConsumed()) {
                quantityHandler.handle(event);
            }
        };
    }

    private EventHandler<KeyEvent> quantityKeyHandler() {
        return new UpdateQuantity(new UpdateInterface() {
            @Override
            public TableView<? extends BasePurchasesAndSales> getTable() {
                return table;
            }

            @Override
            public void update(BasePurchasesAndSales line) {
                InvoiceLineService.recalculate(line);
            }

            @Override
            public void sum() {
                totalsChanged.run();
            }
        }).tableKeyPressed();
    }
}
