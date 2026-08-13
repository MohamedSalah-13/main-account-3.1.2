package com.hamza.account.controller.invoice;

import com.hamza.account.features.invoice.InvoiceLineEditService;
import com.hamza.account.features.invoice.InvoiceLineService;
import com.hamza.account.features.key_setting.MoveRow;
import com.hamza.account.features.key_setting.UpdateInterface;
import com.hamza.account.features.key_setting.UpdateQuantity;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.otherSetting.ButtonDeleteRow;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.button.button_column.ButtonColumn;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import static com.hamza.controlsfx.table.columnEdit.ColumnSetting.addColumn;

/** Owns the JavaFX wiring for the editable invoice-lines table. */
public final class InvoiceTableCoordinator<T extends BasePurchasesAndSales> {

    private final TableView<T> table;
    private final ObservableList<T> lines;
    private final InvoiceLineEditService editService;
    private final IntSupplier priceTier;
    private final BooleanSupplier updateCatalogPrice;
    private final Runnable totalsChanged;
    private final Class<?> menuOwner;
    private final boolean showAdminMenu;

    public InvoiceTableCoordinator(TableView<T> table, ObservableList<T> lines,
                                   InvoiceLineEditService editService,
                                   IntSupplier priceTier,
                                   BooleanSupplier updateCatalogPrice,
                                   Runnable totalsChanged,
                                   Class<?> menuOwner,
                                   boolean showAdminMenu) {
        this.table = Objects.requireNonNull(table, "table");
        this.lines = Objects.requireNonNull(lines, "lines");
        this.editService = Objects.requireNonNull(editService, "editService");
        this.priceTier = Objects.requireNonNull(priceTier, "priceTier");
        this.updateCatalogPrice = Objects.requireNonNull(updateCatalogPrice, "updateCatalogPrice");
        this.totalsChanged = Objects.requireNonNull(totalsChanged, "totalsChanged");
        this.menuOwner = Objects.requireNonNull(menuOwner, "menuOwner");
        this.showAdminMenu = showAdminMenu;
    }

    public void configure() {
        new TableColumnAnnotation().getTable(table, BasePurchasesAndSales.class);
        addIdentityColumns();
        addDeleteColumn();
        table.setItems(lines);
        configureEdits();
        configureSelectionAndKeys();
        configureTotalsRefresh();

        if (showAdminMenu) {
            TableSetting.tableMenuSetting(menuOwner, table);
        }
    }

    private void addIdentityColumns() {
        addColumn(table, Setting_Language.WORD_BARCODE, 0,
                (Callback<TableColumn.CellDataFeatures<T, String>, ObservableValue<String>>)
                        features -> features.getValue().getItems().barcodeProperty());
        addColumn(table, Setting_Language.WORD_NAME, 1,
                (Callback<TableColumn.CellDataFeatures<T, String>, ObservableValue<String>>)
                        features -> features.getValue().getItems().nameItemProperty());
        addColumn(table, Setting_Language.WORD_TYPE, 2,
                (Callback<TableColumn.CellDataFeatures<T, String>, ObservableValue<String>>)
                        features -> features.getValue().getUnitsType().unit_nameProperty());
    }

    private void addDeleteColumn() {
        table.getColumns().add(new ButtonColumn<>(new ButtonDeleteRow() {
            @Override
            public void action(int index) {
                table.getItems().remove(index);
                table.refresh();
            }
        }));
    }

    private void configureEdits() {
        ColumnSetting columns = new ColumnSetting();
        columns.enableStringEditing(1, event -> withRefreshOnFailure(() ->
                editService.editName(rowAt(event.getTablePosition().getRow()),
                        event.getNewValue())), table);
        columns.enableDoubleEditing(3, event -> withRefreshOnFailure(() ->
                editService.editQuantity(rowAt(event.getTablePosition().getRow()),
                        event.getNewValue())), table);
        columns.enableDoubleEditing(4, event -> withRefreshOnFailure(() ->
                editService.editPrice(rowAt(event.getTablePosition().getRow()),
                        event.getNewValue(), updateCatalogPrice.getAsBoolean(),
                        priceTier.getAsInt())), table);
        columns.enableDoubleEditing(6, event -> withRefreshOnFailure(() ->
                editService.editDiscount(rowAt(event.getTablePosition().getRow()),
                        event.getNewValue())), table);
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

    private T rowAt(int row) {
        return table.getItems().get(row);
    }

    private void withRefreshOnFailure(DaoEdit edit) throws DaoException {
        try {
            edit.run();
        } catch (DaoException e) {
            table.refresh();
            throw e;
        }
    }

    @FunctionalInterface
    private interface DaoEdit {
        void run() throws DaoException;
    }
}
