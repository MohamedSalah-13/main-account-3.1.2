package com.hamza.account.controller.invoice;

import com.hamza.account.features.invoice.InvoiceEditorViewModel;
import com.hamza.account.features.invoice.InvoiceItemSelection;
import com.hamza.account.features.invoice.InvoiceItemSelectionService;
import com.hamza.account.features.invoice.InvoiceLineDraft;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.service.ItemUnits;
import com.hamza.controlsfx.others.DoubleSetting;
import com.hamza.controlsfx.others.Utils;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

/** Owns JavaFX event wiring for the item-entry section of an invoice form. */
public final class InvoiceItemEntryCoordinator {

    private final Controls controls;
    private final InvoiceEditorViewModel<?> editor;
    private final InvoiceItemSelectionService selectionService;
    private final StringProperty searchText;
    private final int stockId;
    private final CheckedIntSupplier priceTier;
    private final Supplier<InvoiceItemSelectionService.ScaleBarcodeSettings> scaleSettings;
    private final BooleanSupplier addDirectly;
    private final Runnable addLine;
    private final IntConsumer openItem;
    private final ErrorHandler errorHandler;
    private boolean applyingSelection;
    private int currentPriceTier = 1;

    public InvoiceItemEntryCoordinator(
            Controls controls,
            InvoiceEditorViewModel<?> editor,
            InvoiceItemSelectionService selectionService,
            StringProperty searchText,
            int stockId,
            CheckedIntSupplier priceTier,
            Supplier<InvoiceItemSelectionService.ScaleBarcodeSettings> scaleSettings,
            BooleanSupplier addDirectly,
            Runnable addLine,
            IntConsumer openItem,
            ErrorHandler errorHandler) {
        this.controls = Objects.requireNonNull(controls, "controls");
        this.editor = Objects.requireNonNull(editor, "editor");
        this.selectionService = Objects.requireNonNull(selectionService, "selectionService");
        this.searchText = Objects.requireNonNull(searchText, "searchText");
        this.stockId = stockId;
        this.priceTier = Objects.requireNonNull(priceTier, "priceTier");
        this.scaleSettings = Objects.requireNonNull(scaleSettings, "scaleSettings");
        this.addDirectly = Objects.requireNonNull(addDirectly, "addDirectly");
        this.addLine = Objects.requireNonNull(addLine, "addLine");
        this.openItem = Objects.requireNonNull(openItem, "openItem");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

    public void configure() {
        controls.updateItem().setOnAction(event -> openItem.accept(
                controls.barcode().getText().isBlank() ? 0 : editor.selectedItem().getId()));
        controls.add().setOnAction(event -> addLine.run());
        controls.barcode().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && !controls.barcode().getText().isBlank()) {
                selectByBarcode();
            }
        });
        controls.price().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
                controls.quantity().requestFocus();
            }
        });
        controls.quantity().textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.isEmpty() || newValue.equals("0")) {
                controls.quantity().setText("1");
            }
            updateTotal();
        });
        controls.price().textProperty().addListener(observable -> updateTotal());
        controls.unit().getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> selectUnit(newValue));
        searchText.addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !applyingSelection) {
                selectByName(newValue);
            }
        });
    }

    public InvoiceLineDraft draft() {
        ItemsModel item = editor.selectedItem();
        UnitsModel unit = ItemUnits.unitByName(
                item, controls.unit().getSelectionModel().getSelectedItem());
        return new InvoiceLineDraft(
                item,
                unit,
                DoubleSetting.parseDoubleOrDefault(controls.quantity().getText()),
                DoubleSetting.parseDoubleOrDefault(controls.price().getText()),
                0,
                null);
    }

    public void setPriceTier(int priceTier) {
        currentPriceTier = priceTier;
    }

    public void clear() {
        applyingSelection = true;
        try {
            searchText.set(null);
            clearSelectionFields();
            controls.barcode().requestFocus();
        } finally {
            applyingSelection = false;
        }
    }

    private void selectByBarcode() {
        String barcode = controls.barcode().getText();
        InvoiceItemSelectionService.ScaleBarcodeSettings settings = scaleSettings.get();
        try {
            InvoiceItemSelection selection = selectionService.selectByBarcode(
                    barcode, stockId, resolvePriceTier(), settings);
            apply(selection, true);
            if (addDirectly.getAsBoolean()) {
                addLine.run();
            } else {
                controls.price().requestFocus();
            }
        } catch (Exception e) {
            clearSelectionFieldsKeepingBarcode();
            errorHandler.handle(e, settings.matches(barcode));
            controls.barcode().requestFocus();
        }
    }

    private void selectByName(String itemName) {
        try {
            InvoiceItemSelection selection = selectionService.selectByName(
                    itemName, stockId, resolvePriceTier());
            apply(selection, false);
            controls.price().requestFocus();
        } catch (Exception e) {
            clearSelectionFields();
            errorHandler.handle(e, false);
        }
    }

    private void selectUnit(String unitName) {
        if (unitName == null || applyingSelection) {
            return;
        }
        ItemsModel item = editor.selectedItem();
        if (item == null || item.getId() <= 0) {
            return;
        }
        try {
            InvoiceItemSelectionService.UnitSelection selection =
                    selectionService.selectUnit(item, unitName, currentPriceTier);
            controls.balance().setText(String.valueOf(
                    roundToTwoDecimalPlaces(selection.balance())));
            controls.price().setText(String.valueOf(selection.price()));
        } catch (Exception e) {
            errorHandler.handle(e, false);
        }
    }

    private void apply(InvoiceItemSelection selection, boolean updateSearchName) {
        applyingSelection = true;
        try {
            editor.selectItem(selection.item());
            controls.barcode().setText(selection.barcode());
            if (updateSearchName) {
                searchText.set(selection.item().getNameItem());
            }

            List<String> unitNames = selection.units().stream()
                    .map(UnitsModel::getUnit_name)
                    .toList();
            controls.unit().setItems(FXCollections.observableArrayList(unitNames));
            controls.unit().getSelectionModel().select(selection.selectedUnit().getUnit_name());
            controls.unit().setDisable(unitNames.size() < 2);
            controls.balance().setText(String.valueOf(
                    roundToTwoDecimalPlaces(selection.balance())));
            controls.price().setText(String.valueOf(selection.price()));
            controls.quantity().setText(String.valueOf(selection.quantity()));
            controls.total().setText(String.valueOf(selection.total()));
        } finally {
            applyingSelection = false;
        }
    }

    private void clearSelectionFields() {
        editor.selectItem(null);
        controls.unit().setDisable(false);
        controls.unit().getItems().clear();
        Utils.clearAll(controls.balance(), controls.price(), controls.quantity(),
                controls.total(), controls.barcode());
    }

    private void clearSelectionFieldsKeepingBarcode() {
        editor.selectItem(null);
        controls.unit().setDisable(false);
        controls.unit().getItems().clear();
        Utils.clearAll(controls.balance(), controls.price(), controls.quantity(),
                controls.total());
    }

    private int resolvePriceTier() throws Exception {
        currentPriceTier = priceTier.getAsInt();
        return currentPriceTier;
    }

    private void updateTotal() {
        double price = DoubleSetting.parseDoubleOrDefault(controls.price().getText());
        double quantity = DoubleSetting.parseDoubleOrDefault(controls.quantity().getText());
        controls.total().setText(MoneyMath.text(MoneyMath.multiply(price, quantity)));
    }

    public record Controls(TextField barcode, TextField price, TextField quantity,
                           TextField balance, TextField total, ComboBox<String> unit,
                           Button add, Button updateItem) {
        public Controls {
            Objects.requireNonNull(barcode, "barcode");
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(balance, "balance");
            Objects.requireNonNull(total, "total");
            Objects.requireNonNull(unit, "unit");
            Objects.requireNonNull(add, "add");
            Objects.requireNonNull(updateItem, "updateItem");
        }
    }

    @FunctionalInterface
    public interface CheckedIntSupplier {
        int getAsInt() throws Exception;
    }

    @FunctionalInterface
    public interface ErrorHandler {
        void handle(Exception error, boolean scaleBarcode);
    }
}
