package com.hamza.account.controller.items;

import com.codejava.commons.fx.validation.InputValidator;
import com.hamza.account.features.items.UnitEntryRules;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.UnitsModel;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * The units tab: the item's own unit as row 0, and every other unit it is bought or
 * sold in below it, each with the factor and the prices that unit carries.
 * <p>
 * Built the way {@link ExtraBarcodesTabController} already was - a tab handed its own
 * controls, so {@code AddItemController} no longer holds nine {@code @FXML} fields and
 * five handlers that only that tab ever touches. What is left in the controller is the
 * part that is genuinely shared: the base unit is chosen on the main form
 * ({@code comboType}) while the row it maintains lives in this table, and the item's
 * barcode is typed on the main form while row 0 carries it.
 * <p>
 * <b>Row 0 is not one of the item's unit rows.</b> It is {@code items.unit_id} shown in
 * the table so the operator can see what the factors are counted against;
 * {@code ItemsDao.saveUnits} drops it by matching it against that column. That is why it
 * cannot be deleted or edited here, and why {@link #setBaseUnit} exists at all.
 */
public class UnitsTabController {

    private final TableView<ItemsUnitsModel> tableUnits;
    private final ComboBox<String> comboUnit;
    private final TextField textQuantity;
    private final TextField textBarcode;
    private final TextField textBuyPrice;
    private final TextField textSelPrice;
    private final TextField textSelPrice2;
    private final TextField textSelPrice3;
    private final TableUnitsSetting tableUnitsSetting;
    /** Name to unit, out of the screen's cache - never a query. */
    private final Function<String, UnitsModel> unitByName;
    /** Whether the code in a field is free, marking and reporting it if not. */
    private final Predicate<TextField> barcodeIsFree;

    public UnitsTabController(TableView<ItemsUnitsModel> tableUnits, ComboBox<String> comboUnit,
                              TextField textQuantity, TextField textBarcode, TextField textBuyPrice,
                              TextField textSelPrice, TextField textSelPrice2, TextField textSelPrice3,
                              Button btnAdd, ObservableList<String> unitNames,
                              Function<String, UnitsModel> unitByName, Predicate<TextField> barcodeIsFree) {
        this.tableUnits = tableUnits;
        this.comboUnit = comboUnit;
        this.textQuantity = textQuantity;
        this.textBarcode = textBarcode;
        this.textBuyPrice = textBuyPrice;
        this.textSelPrice = textSelPrice;
        this.textSelPrice2 = textSelPrice2;
        this.textSelPrice3 = textSelPrice3;
        this.unitByName = unitByName;
        this.barcodeIsFree = barcodeIsFree;

        this.tableUnitsSetting = new TableUnitsSetting(unitByName, tableUnits);
        bindEntryFields(btnAdd);
        // The list itself rather than a copy, so a unit added or renamed from the units
        // screen while this dialog is open reaches this combo too.
        comboUnit.setItems(unitNames);
        addListeners(btnAdd);
    }

    private void bindEntryFields(Button btnAdd) {
        tableUnitsSetting.selectedTypeProperty().bind(comboUnit.getSelectionModel().selectedItemProperty());
        tableUnitsSetting.textUnitBarcodeProperty().bindBidirectional(textBarcode.textProperty());
        // A barcode is digits, not a number - setTextFormatter's numeric converter would
        // be the wrong tool here (it would happily reformat the text and drop a leading
        // zero), so this is filtered the way ExtraBarcodesTabController filters its own.
        InputValidator.makeNumericOnly(textBarcode);
        // The factor belongs to the item, not to the unit: picking a unit fills this in
        // with its default, and the field is where that gets corrected to what a carton
        // of *this* item actually holds.
        tableUnitsSetting.textUnitQuantityProperty().bindBidirectional(textQuantity.textProperty());

        // A unit may be priced outright - a carton is sold cheaper than twelve pieces on
        // purpose. Left blank, it is priced from the item as before.
        tableUnitsSetting.textUnitBuyPriceProperty().bindBidirectional(textBuyPrice.textProperty());
        tableUnitsSetting.textUnitSelPriceProperty().bindBidirectional(textSelPrice.textProperty());
        tableUnitsSetting.textUnitSelPrice2Property().bindBidirectional(textSelPrice2.textProperty());
        tableUnitsSetting.textUnitSelPrice3Property().bindBidirectional(textSelPrice3.textProperty());

        setTextFormatter(textQuantity, textBuyPrice, textSelPrice, textSelPrice2, textSelPrice3);

        // The order the tab is actually filled, ending on the button that adds the row -
        // rule ق-ل9. This tab had no Enter order at all while it lived in
        // AddItemController: a scanner ends its read with an Enter, so scanning a
        // carton's code left the code sitting in a field the operator had already
        // stopped looking at. Enter does not add the unit the way it does on the
        // extra-barcodes tab, because a unit is not finished until it has a factor.
        whenEnterPressed(comboUnit, textQuantity, textBarcode, textBuyPrice, textSelPrice,
                textSelPrice2, textSelPrice3, btnAdd);
    }

    private void addListeners(Button btnAdd) {
        comboUnit.valueProperty().addListener((observable, oldName, name) -> {
            // A cleared selection is not a unit. The resolver answers the base unit for
            // null - right where it stands in for a blank comboType, wrong here:
            // refreshing the list clears this combo, and resolving that to unit 1 would
            // push its factor into a field the operator had already filled.
            if (name == null) return;
            // A unit deleted from the units screen while this dialog is open no longer
            // resolves; that is not a reason to throw an NPE out of the FX event loop,
            // where nothing catches it.
            var unit = unitByName.apply(name);
            if (unit == null) return;
            textQuantity.setText(String.valueOf(unit.getValue()));
        });

        btnAdd.setOnAction(event -> {
            // A unit carries a code of its own, so it is the third way a duplicate gets
            // onto this screen - refused here for the same reason the extra-barcode list
            // refuses one.
            if (!barcodeIsFree.test(textBarcode)) return;
            tableUnitsSetting.addUnit();
            // The next unit starts from a clean sheet - a price left in the field would
            // otherwise be charged for a unit nobody priced, and a barcode left there is
            // a code two units of the item both claim.
            clearEntryFields();
        });

        // DELETE used to fire btnAdd, which added a unit rather than removing one.
        tableUnits.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.DELETE) {
                tableUnitsSetting.removeSelectedUnit();
            }
        });

        tableUnits.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                copyToEntryFields(tableUnits.getSelectionModel().getSelectedItem());
            }
        });
    }

    /** Puts a row back in the entry fields so it can be re-entered with a change. */
    private void copyToEntryFields(ItemsUnitsModel row) {
        // A double-click below the last row selects nothing.
        if (row == null || row.getUnitsModel() == null) return;
        comboUnit.getSelectionModel().select(row.getUnitsModel().getUnit_name());
        textQuantity.setText(String.valueOf(row.getQuantityForUnit()));
        textBarcode.setText(row.getItemsBarcode());
        showPrice(textBuyPrice, row.getBuyPrice());
        showPrice(textSelPrice, row.getSelPrice());
        showPrice(textSelPrice2, row.getSelPrice2());
        showPrice(textSelPrice3, row.getSelPrice3());
    }

    /**
     * Zero is not a price, it is the absence of one - show it as an empty field so the
     * unit reads as priced from the item, which is what it is.
     */
    private void showPrice(TextField field, double price) {
        field.setText(price > 0 ? String.valueOf(price) : "");
    }

    private void clearEntryFields() {
        textBarcode.clear();
        textBuyPrice.clear();
        textSelPrice.clear();
        textSelPrice2.clear();
        textSelPrice3.clear();
    }

    /**
     * The rows as they stand, base unit included - what the save writes and what the
     * item's own unit combo is checked against.
     * <p>
     * Read through {@code TableUnitsSetting} on every call rather than kept: loading a
     * saved item replaces the table's list outright, so a held reference would be the
     * previous item's rows.
     */
    public ObservableList<ItemsUnitsModel> units() {
        return tableUnitsSetting.getItemsUnitsModelList();
    }

    /** Shows a saved item's units. */
    public void load(ItemsModel item) {
        tableUnitsSetting.selectTable(item);
    }

    public void clear() {
        units().clear();
    }

    /**
     * Whether some unit other than the base one is already {@code unitName} - the check
     * behind refusing to make the item's own unit one it is also sold by, which would be
     * two rows meaning the same thing with different factors.
     */
    public boolean isUnitAddedBesidesBase(String unitName) {
        return UnitEntryRules.holdsUnitBesidesBase(units(), unitName);
    }

    /** Points row 0 at the item's unit. Does nothing while there is no row 0. */
    public void setBaseUnit(UnitsModel baseUnit) {
        if (baseUnit == null || units().isEmpty()) return;
        units().getFirst().unitsModelProperty().set(baseUnit);
        tableUnits.refresh();
    }

    /**
     * Carries the item's own barcode onto row 0, creating that row the first time there
     * is a unit to create it with.
     * <p>
     * {@code baseUnit} is a supplier because it is only needed when the row is missing -
     * this runs on every keystroke in the barcode field. It answering null means the
     * units are not loaded (their query failed), and then there is no row: one carrying
     * no unit threw out of the table's cell factory on its way to being drawn, and the
     * save refuses an empty list with a message that is true in that state.
     */
    public void applyItemBarcode(String barcode, Supplier<UnitsModel> baseUnit) {
        if (!units().isEmpty()) {
            units().getFirst().setItemsBarcode(barcode);
            return;
        }

        UnitsModel unit = baseUnit.get();
        if (unit == null) return;

        var row = new ItemsUnitsModel();
        row.setItemsBarcode(barcode);
        row.setUnitsModel(unit);
        row.setQuantityForUnit(1);
        units().add(row);
    }
}
