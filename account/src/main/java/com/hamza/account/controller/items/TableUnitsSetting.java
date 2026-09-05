package com.hamza.account.controller.items;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.config.NamesTables;
import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.features.items.UnitEntryRules;
import com.hamza.account.otherSetting.ButtonDeleteRow;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.button_column.ButtonColumn;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import lombok.Setter;

import java.util.function.Function;
import java.util.function.ObjDoubleConsumer;

@Setter
public class TableUnitsSetting extends TableUnitsSettingProperty {

    private final TableView<ItemsUnitsModel> tableUnits;
    /**
     * Name to unit. It was {@code UnitsService.getUnitsByName} - a query to the database
     * on every press of the add button, for a row the screen's own units cache already
     * held. The cache is what {@code AddItemController.getUnitsModelByName} reads, and
     * {@link UnitsTabController} passes it straight through.
     */
    private final Function<String, UnitsModel> unitByName;
    private int itemId;
    private String itemBarcode;

    public TableUnitsSetting(Function<String, UnitsModel> unitByName, TableView<ItemsUnitsModel> tableUnits) {
        this.unitByName = unitByName;
        this.tableUnits = tableUnits;
        unitsSetting();
        getTable();
    }

    private void unitsSetting() {
        itemsUnitsModelListProperty().bind(tableUnits.itemsProperty());

        textUnitBarcodeProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                setTextUnitBarcode(oldValue);
            }
        });

    }

    /**
     * Adds the unit named in the entry fields to the item.
     * <p>
     * <b>Every refusal here is a {@link UserValidationException}, never a plain
     * {@code RuntimeException}.</b> {@code AllAlerts.handleError} classifies what it is
     * given: anything that is not a {@code UserFacingException} takes the technical path,
     * so the Arabic sentence built here would be logged and the user shown the generic
     * "unexpected error" and a reference code instead. All five refusals in this class
     * were written that way and read as crashes to the operator - someone who typed a
     * factor of zero was told a reference number rather than that a factor has to be
     * above zero.
     */
    public void addUnit() {
        try {
            var lm = LanguageManager.getInstance();

            if (isUnitTypeExists(getSelectedType())) {
                throw new UserValidationException(lm.getString("item.error.unit.already.added"));
            }

            // Two units of an item may well hold the same number of base units -
            // a roll and a carton of twelve are different things to sell. The
            // unit itself must not repeat; its factor may.
            double quantityForUnit = UnitEntryRules.factor(getTextUnitQuantity());

            UnitsModel unitsModelByName = unitByName.apply(getSelectedType());
            // Nothing selected, or a unit deleted from the units screen since this combo
            // was filled. A row carrying no unit is not a unit of anything: it renders
            // blank and reaches ItemsDao.saveUnits, which reads the unit's id off it.
            if (unitsModelByName == null) {
                throw new UserValidationException(lm.getString("item.error.units.required"));
            }

            var e = new ItemsUnitsModel();
            e.setUnitsModel(unitsModelByName);
            e.setId(0);
            e.setItemsId(itemId);
            e.setItemsBarcode(getTextUnitBarcode());
            e.setQuantityForUnit(quantityForUnit);
            // Left blank, these stay zero and the unit is priced from the item.
            e.setBuyPrice(UnitEntryRules.price(getTextUnitBuyPrice(), lm.getString("item.buy.price")));
            e.setSelPrice(UnitEntryRules.price(getTextUnitSelPrice(), lm.getString("item.sel.price")));
            e.setSelPrice2(UnitEntryRules.price(getTextUnitSelPrice2(), lm.getString("item.sel.price2")));
            e.setSelPrice3(UnitEntryRules.price(getTextUnitSelPrice3(), lm.getString("item.sel.price3")));
            tableUnits.getItems().add(e);

        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("item.dialog.update.units.title"), e);
        }
    }

    private void getTable() {
        var quantity = Columns.number(NamesTables.QUANTITY, ItemsUnitsModel::getQuantityForUnit);
        var buyPrice = Columns.number(NamesTables.BUY_PRICE, ItemsUnitsModel::getBuyPrice);
        var selPrice = Columns.number(NamesTables.SEL_PRICE, ItemsUnitsModel::getSelPrice);
        var selPrice2 = Columns.number(NamesTables.SEL_PRICE + "2", ItemsUnitsModel::getSelPrice2);
        var selPrice3 = Columns.number(NamesTables.SEL_PRICE + "3", ItemsUnitsModel::getSelPrice3);

        tableUnits.getColumns().addAll(
                Columns.number(NamesTables.CODE, ItemsUnitsModel::getId),
                Columns.text(NamesTables.BARCODE, ItemsUnitsModel::getItemsBarcode),
                quantity, buyPrice, selPrice, selPrice2, selPrice3
        );
        TableColumn<ItemsUnitsModel, String> columnActiveName = new TableColumn<>(LanguageManager.getInstance().getString("item.column.unit"));
        // A row whose unit could not be resolved - the units table failed to load, so
        // AddItemController.getUnitsModelByName answered null - still has to render. This
        // used to dereference the null and throw out of the cell factory, which takes the
        // whole table down rather than showing one blank cell.
        columnActiveName.setCellValueFactory(f -> {
            UnitsModel unit = f.getValue().unitsModelProperty().get();
            return unit == null ? new SimpleStringProperty("") : unit.unit_nameProperty();
        });
        tableUnits.getColumns().add(2, columnActiveName);

        // The quantity and the prices are edited in place: a saved unit whose
        // carton price changed should not have to be deleted and re-added.
        tableUnits.setEditable(true);
        var columnSetting = new ColumnSetting();
        // Asked of the table rather than written as 3..7. The five numbers were a
        // restatement of the order the columns happen to be added in, plus the fact that
        // the unit-name column is inserted at 2 - so adding a column, or moving the
        // insert, silently pointed each editor at its neighbour, which reads as "editing
        // the buy price changes the factor".
        columnSetting.enableDoubleEditing(indexOf(quantity), event ->
                editRow(event, (row, value) -> row.setQuantityForUnit(value > 0 ? value : row.getQuantityForUnit())), tableUnits);
        columnSetting.enableDoubleEditing(indexOf(buyPrice), event ->
                editRow(event, ItemsUnitsModel::setBuyPrice), tableUnits);
        columnSetting.enableDoubleEditing(indexOf(selPrice), event ->
                editRow(event, ItemsUnitsModel::setSelPrice), tableUnits);
        columnSetting.enableDoubleEditing(indexOf(selPrice2), event ->
                editRow(event, ItemsUnitsModel::setSelPrice2), tableUnits);
        columnSetting.enableDoubleEditing(indexOf(selPrice3), event ->
                editRow(event, ItemsUnitsModel::setSelPrice3), tableUnits);

        tableUnits.getColumns().add(new ButtonColumn<>(new ButtonDeleteRow() {
            @Override
            public void action(int i) {
                if (!UnitEntryRules.mayDeleteRow(i)) {
                    return;
                }
                tableUnits.getItems().remove(i);
                tableUnits.refresh();
            }

            @Override
            public boolean isButtonDisabled(int index) {
                // Row 0 is the base unit, which is not stored here.
                return UnitEntryRules.isBaseRow(index);
            }
        }));
    }


    /** Where a column ended up, so no caller has to know the order they were added in. */
    private int indexOf(TableColumn<ItemsUnitsModel, ?> column) {
        return tableUnits.getColumns().indexOf(column);
    }

    /**
     * Applies an edited cell to its row. The first row is the item's base unit -
     * it lives in {@code items.unit_id} and is not stored here, so its factor is
     * 1 and its price is the item's own; there is nothing to edit.
     */
    private void editRow(TableColumn.CellEditEvent<ItemsUnitsModel, Double> event,
                         ObjDoubleConsumer<ItemsUnitsModel> apply) {
        Double value = event.getNewValue();
        if (!UnitEntryRules.mayEditRow(event.getTablePosition().getRow()) || value == null || value < 0) {
            tableUnits.refresh();
            return;
        }
        apply.accept(event.getRowValue(), value);
        tableUnits.refresh();
    }

    private boolean isUnitTypeExists(String unitType) {
        return UnitEntryRules.holdsUnit(tableUnits.getItems(), unitType);
    }

    /**
     * Removes the selected unit. The first row is the item's base unit and is
     * not one of the item's rows at all - it is {@code items.unit_id} - so it
     * cannot be removed here; change it with the item's own unit combo.
     */
    public void removeSelectedUnit() {
        int index = tableUnits.getSelectionModel().getSelectedIndex();
        if (!UnitEntryRules.mayDeleteRow(index)) {
            return;
        }
        tableUnits.getItems().remove(index);
        tableUnits.refresh();
    }

    public void selectTable(ItemsModel itemsModel) {
        tableUnits.getItems().clear();
        tableUnits.setItems(FXCollections.observableArrayList(itemsModel.getItemsUnitsModelList()));
    }

}
