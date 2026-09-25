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
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

/**
 * Owns JavaFX event wiring for the item-entry section of an invoice form.
 */
public final class InvoiceItemEntryCoordinator {

    private final Controls controls;
    private final InvoiceEditorViewModel<?> editor;
    private final InvoiceItemSelectionService selectionService;
    private final StringProperty searchText;
    private final IntSupplier stockId;
    private final CheckedIntSupplier priceTier;
    private final Supplier<InvoiceItemSelectionService.ScaleBarcodeSettings> scaleSettings;
    private final BooleanSupplier addDirectly;
    private final Runnable addLine;
    private final IntConsumer openItem;
    private final ErrorHandler errorHandler;
    private boolean applyingSelection;
    private int currentPriceTier = 1;
    /** A bundle's barcode is tried before an item's (V87): none by default. */
    private BundleEntry bundleEntry = barcode -> false;
    /** The list price the last item or unit chosen was offered at, or null on a purchase (V84). */
    private com.hamza.account.features.invoice.InvoiceLineDraft.Listed offeredListed;

    public InvoiceItemEntryCoordinator(Controls controls, InvoiceEditorViewModel<?> editor,
                                       InvoiceItemSelectionService selectionService, StringProperty searchText, int stockId,
                                       CheckedIntSupplier priceTier, Supplier<InvoiceItemSelectionService.ScaleBarcodeSettings> scaleSettings,
                                       BooleanSupplier addDirectly, Runnable addLine, IntConsumer openItem, ErrorHandler errorHandler) {
        this(controls, editor, selectionService, searchText, () -> stockId, priceTier,
                scaleSettings, addDirectly, addLine, openItem, errorHandler);
    }

    public InvoiceItemEntryCoordinator(
            Controls controls,
            InvoiceEditorViewModel<?> editor,
            InvoiceItemSelectionService selectionService,
            StringProperty searchText,
            IntSupplier stockId,
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
        this.stockId = Objects.requireNonNull(stockId, "stockId");
        this.priceTier = Objects.requireNonNull(priceTier, "priceTier");
        this.scaleSettings = Objects.requireNonNull(scaleSettings, "scaleSettings");
        this.addDirectly = Objects.requireNonNull(addDirectly, "addDirectly");
        this.addLine = Objects.requireNonNull(addLine, "addLine");
        this.openItem = Objects.requireNonNull(openItem, "openItem");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

    /**
     * Opens the item on the form for editing, or a new item when the form names none.
     * Reached by F4 alone: the toolbar button it used to sit behind is gone.
     */
    public void openCurrentItem() {
        openItem.accept(controls.barcode().getText().isBlank() || editor.selectedItem() == null
                ? 0 : editor.selectedItem().getId());
    }

    public void configure() {
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

    /**
     * The line the form describes. Its price is whatever is in the price box - typed over or not - and
     * the tier's list price behind it is the one the last item or unit chosen was offered at (V84), so a
     * price typed under it is known to be under it.
     */
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
                null,
                offeredListed);
    }

    /**
     * The invoice's tier changed. An item already on the form is offered again at the new tier's
     * price, as a line already on the invoice is repriced - otherwise the next line added would be at
     * the tier the invoice has just left.
     */
    public void setPriceTier(int priceTier) {
        currentPriceTier = priceTier;
        ItemsModel item = editor.selectedItem();
        if (item != null && item.getId() > 0) {
            selectUnit(controls.unit().getSelectionModel().getSelectedItem());
        }
    }

    /** What a scanned code is asked first: whether a bundle answers to it and has put its lines on. */
    public void setBundleEntry(BundleEntry bundleEntry) {
        this.bundleEntry = Objects.requireNonNull(bundleEntry, "bundleEntry");
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
            if (bundleEntry.add(barcode)) {
                clear();
                return;
            }
            InvoiceItemSelection selection = selectionService.selectByBarcode(
                    barcode, stockId.getAsInt(), resolvePriceTier(), settings);
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
                    itemName, stockId.getAsInt(), resolvePriceTier());
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
            offeredListed = selection.listed();
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
            offeredListed = selection.listed();
        } finally {
            applyingSelection = false;
        }
    }

    private void clearSelectionFields() {
        editor.selectItem(null);
        offeredListed = null;
        controls.unit().setDisable(false);
        controls.unit().getItems().clear();
        Utils.clearAll(controls.balance(), controls.price(), controls.quantity(),
                controls.total(), controls.barcode());
    }

    private void clearSelectionFieldsKeepingBarcode() {
        editor.selectItem(null);
        offeredListed = null;
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

    @FunctionalInterface
    public interface CheckedIntSupplier {
        int getAsInt() throws Exception;
    }

    /** Puts a bundle's components on the invoice when one answers to the code; false when none does. */
    @FunctionalInterface
    public interface BundleEntry {
        boolean add(String barcode) throws Exception;
    }

    @FunctionalInterface
    public interface ErrorHandler {
        void handle(Exception error, boolean scaleBarcode);
    }

    public record Controls(TextField barcode, TextField price, TextField quantity,
                           TextField balance, TextField total, ComboBox<String> unit,
                           Button add) {
        public Controls {
            Objects.requireNonNull(barcode, "barcode");
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(balance, "balance");
            Objects.requireNonNull(total, "total");
            Objects.requireNonNull(unit, "unit");
            Objects.requireNonNull(add, "add");
        }
    }
}
