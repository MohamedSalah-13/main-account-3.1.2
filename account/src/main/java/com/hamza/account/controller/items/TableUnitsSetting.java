package com.hamza.account.controller.items;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.otherSetting.ButtonDeleteRow;
import com.hamza.account.service.UnitsService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.button_column.ButtonColumn;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.util.function.ObjDoubleConsumer;

@Log4j2
@Setter
public class TableUnitsSetting extends TableUnitsSettingProperty {

    // The unit-name column is inserted at 2, after the annotated columns of
    // ItemsUnitsModel have been added in field order.
    private static final int QUANTITY_COLUMN = 3;
    private static final int BUY_PRICE_COLUMN = 4;
    private static final int SEL_PRICE_COLUMN = 5;
    private static final int SEL_PRICE_2_COLUMN = 6;
    private static final int SEL_PRICE_3_COLUMN = 7;

    private final TableView<ItemsUnitsModel> tableUnits;
    private UnitsService unitsService;
    private int itemId;
    private String itemBarcode;

    public TableUnitsSetting(UnitsService unitsService, TableView<ItemsUnitsModel> tableUnits) {
        this.unitsService = unitsService;
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

    public void addUnit() {
        try {

            if (isUnitTypeExists(getSelectedType())) {
                throw new RuntimeException("هذه الوحدة مضافة بالفعل لهذا الصنف");
            }

            // Two units of an item may well hold the same number of base units -
            // a roll and a carton of twelve are different things to sell. The
            // unit itself must not repeat; its factor may.
            double quantityForUnit = parseQuantity();

            UnitsModel unitsModelByName = unitsService.getUnitsByName(getSelectedType());

            var e = new ItemsUnitsModel();
            e.setUnitsModel(unitsModelByName);
            e.setId(0);
            e.setItemsId(itemId);
            e.setItemsBarcode(getTextUnitBarcode());
            e.setQuantityForUnit(quantityForUnit);
            // Left blank, these stay zero and the unit is priced from the item.
            e.setBuyPrice(parsePrice(getTextUnitBuyPrice(), "سعر الشراء"));
            e.setSelPrice(parsePrice(getTextUnitSelPrice(), "سعر البيع"));
            e.setSelPrice2(parsePrice(getTextUnitSelPrice2(), "سعر البيع 2"));
            e.setSelPrice3(parsePrice(getTextUnitSelPrice3(), "سعر البيع 3"));
            tableUnits.getItems().add(e);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            AllAlerts.alertError(e.getMessage());
        }
    }

    /**
     * How many of the item's base unit this one holds - the number the invoice
     * multiplies by, and the reason a carton is not the same size for every item.
     */
    private double parseQuantity() {
        String text = getTextUnitQuantity();
        double quantity;
        try {
            quantity = text == null ? 0 : Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new RuntimeException("ادخل عدد الوحدات الأساسية داخل هذه الوحدة");
        }
        if (quantity <= 0) {
            throw new RuntimeException("عدد الوحدات الأساسية يجب أن يكون أكبر من صفر");
        }
        return quantity;
    }

    /**
     * A price the user left blank, which is zero - "this unit has no price of
     * its own". Anything typed has to be a number, and a negative one would read
     * as a price and be charged.
     */
    private double parsePrice(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        double price;
        try {
            price = Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new RuntimeException(fieldName + ": ادخل رقماً صحيحاً");
        }
        if (price < 0) {
            throw new RuntimeException(fieldName + ": لا يمكن أن يكون بالسالب");
        }
        return price;
    }

    private void getTable() {
        new TableColumnAnnotation().getTable(tableUnits, ItemsUnitsModel.class);
        TableColumn<ItemsUnitsModel, String> columnActiveName = new TableColumn<>(Setting_Language.Unit);
        columnActiveName.setCellValueFactory(f -> f.getValue().unitsModelProperty().get().unit_nameProperty());
        tableUnits.getColumns().add(2, columnActiveName);

        // The quantity and the prices are edited in place: a saved unit whose
        // carton price changed should not have to be deleted and re-added.
        tableUnits.setEditable(true);
        var columnSetting = new ColumnSetting();
        columnSetting.enableDoubleEditing(QUANTITY_COLUMN, event ->
                editRow(event, (row, value) -> row.setQuantityForUnit(value > 0 ? value : row.getQuantityForUnit())), tableUnits);
        columnSetting.enableDoubleEditing(BUY_PRICE_COLUMN, event ->
                editRow(event, ItemsUnitsModel::setBuyPrice), tableUnits);
        columnSetting.enableDoubleEditing(SEL_PRICE_COLUMN, event ->
                editRow(event, ItemsUnitsModel::setSelPrice), tableUnits);
        columnSetting.enableDoubleEditing(SEL_PRICE_2_COLUMN, event ->
                editRow(event, ItemsUnitsModel::setSelPrice2), tableUnits);
        columnSetting.enableDoubleEditing(SEL_PRICE_3_COLUMN, event ->
                editRow(event, ItemsUnitsModel::setSelPrice3), tableUnits);

        tableUnits.getColumns().add(new ButtonColumn<>(new ButtonDeleteRow() {
            @Override
            public void action(int i) {
                if (i <= 0) {
                    return;
                }
                tableUnits.getItems().remove(i);
                tableUnits.refresh();
            }

            @Override
            public boolean isButtonDisabled(int index) {
                // Row 0 is the base unit, which is not stored here.
                return index == 0;
            }
        }));
    }


    /**
     * Applies an edited cell to its row. The first row is the item's base unit -
     * it lives in {@code items.unit_id} and is not stored here, so its factor is
     * 1 and its price is the item's own; there is nothing to edit.
     */
    private void editRow(TableColumn.CellEditEvent<ItemsUnitsModel, Double> event,
                         ObjDoubleConsumer<ItemsUnitsModel> apply) {
        Double value = event.getNewValue();
        if (event.getTablePosition().getRow() == 0 || value == null || value < 0) {
            tableUnits.refresh();
            return;
        }
        apply.accept(event.getRowValue(), value);
        tableUnits.refresh();
    }

    private boolean isUnitTypeExists(String unitType) {
        return tableUnits.getItems().stream()
                .anyMatch(item -> item.getUnitsModel().getUnit_name().equals(unitType));
    }

    /**
     * Removes the selected unit. The first row is the item's base unit and is
     * not one of the item's rows at all - it is {@code items.unit_id} - so it
     * cannot be removed here; change it with the item's own unit combo.
     */
    public void removeSelectedUnit() {
        int index = tableUnits.getSelectionModel().getSelectedIndex();
        if (index <= 0) {
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
