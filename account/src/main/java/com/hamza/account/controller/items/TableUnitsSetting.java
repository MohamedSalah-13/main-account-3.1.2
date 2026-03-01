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
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Setter
public class TableUnitsSetting extends TableUnitsSettingProperty {
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
                throw new RuntimeException("This unit type already exists!");
            }

            UnitsModel unitsModelByName = unitsService.getUnitsByName(getSelectedType());

            var itemsUnitsModelStream = tableUnits.getItems().stream()
                    .anyMatch(itemsUnitsModel -> itemsUnitsModel.getUnitsModel().getValue() == unitsModelByName.getValue());
            if (itemsUnitsModelStream) {
                throw new RuntimeException("This unit type already exists!");
            }


            var e = new ItemsUnitsModel();
            e.setUnitsModel(unitsModelByName);
            e.setId(0);
            e.setItemsId(itemId);
            e.setItemsBarcode(getTextUnitBarcode());
            e.setQuantityForUnit(unitsModelByName.getValue());
            e.setBuyPrice(0.0);
            e.setSelPrice(0.0);
            tableUnits.getItems().add(e);

        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    private void getTable() {
        new TableColumnAnnotation().getTable(tableUnits, ItemsUnitsModel.class);
        TableColumn<ItemsUnitsModel, String> columnActiveName = new TableColumn<>(Setting_Language.Unit);
        columnActiveName.setCellValueFactory(f -> f.getValue().unitsModelProperty().get().unit_nameProperty());
        tableUnits.getColumns().add(2, columnActiveName);

        tableUnits.getColumns().add(new ButtonColumn<>(new ButtonDeleteRow() {
            @Override
            public void action(int i) {
                tableUnits.getItems().remove(i);
                tableUnits.refresh();
            }

            @Override
            public boolean isButtonDisabled(int index) {
                return index == 0;
            }
        }));
    }


    private boolean isUnitTypeExists(String unitType) {
        return tableUnits.getItems().stream()
                .anyMatch(item -> item.getUnitsModel().getUnit_name().equals(unitType));
    }

    public void selectTable(ItemsModel itemsModel) {
        tableUnits.getItems().clear();
        tableUnits.setItems(FXCollections.observableArrayList(itemsModel.getItemsUnitsModelList()));
    }

}
