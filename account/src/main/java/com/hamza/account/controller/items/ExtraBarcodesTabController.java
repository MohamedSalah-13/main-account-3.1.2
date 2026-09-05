package com.hamza.account.controller.items;

import com.codejava.commons.fx.validation.InputValidator;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import java.util.List;
import java.util.function.Supplier;

/**
 * The "extra barcodes" tab: an item may answer to more than one code besides
 * its own, and none of them may repeat here or belong to another item.
 * <p>
 * {@code itemBarcode} is a supplier rather than the item's own barcode field,
 * so this class does not need to know {@link ItemForm} exists - only that
 * something can tell it the current barcode when it is asked, which is all a
 * duplicate-of-the-item-itself check needs.
 * <p>
 * A code that belongs to some <em>other</em> item is refused here too, by
 * {@link BarcodeAvailability}, rather than only at save: the list is filled in
 * one code at a time, and a refusal at the end names a code without saying
 * which of the several on the screen it came from.
 */
public class ExtraBarcodesTabController {

    private final ListView<String> listExtraBarcodes;
    private final TextField textExtraBarcode;
    private final Supplier<String> itemBarcode;
    private final BarcodeAvailability availability;

    public ExtraBarcodesTabController(ListView<String> listExtraBarcodes, TextField textExtraBarcode,
                                       Button btnAdd, Button btnRemove, Supplier<String> itemBarcode,
                                       BarcodeAvailability availability) {
        this.listExtraBarcodes = listExtraBarcodes;
        this.textExtraBarcode = textExtraBarcode;
        this.itemBarcode = itemBarcode;
        this.availability = availability;

        listExtraBarcodes.setItems(FXCollections.observableArrayList());
        InputValidator.makeNumericOnly(textExtraBarcode);

        btnAdd.setOnAction(actionEvent -> add());
        textExtraBarcode.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.ENTER) add();
        });
        btnRemove.setOnAction(actionEvent -> removeSelected());
    }

    private void add() {
        try {
            String barcode = textExtraBarcode.getText().trim();
            if (barcode.isEmpty()) {
                return;
            }
            if (barcode.length() > BarcodeAvailability.MAX_LENGTH) {
                throw new UserValidationException(LanguageManager.getInstance().getString("item.error.barcode.too.long"));
            }
            if (barcode.equals(itemBarcode.get()) || listExtraBarcodes.getItems().contains(barcode)) {
                throw new UserValidationException(LanguageManager.getInstance().getString("item.error.barcode.duplicate.same.item"));
            }

            // The database is asked before the code reaches the list, so a code
            // another item already answers to never gets typed in and forgotten.
            availability.requireFree(barcode);

            listExtraBarcodes.getItems().add(barcode);
            textExtraBarcode.clear();
            textExtraBarcode.requestFocus();
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("item.dialog.save.title"), e);
        }
    }

    private void removeSelected() {
        var selected = listExtraBarcodes.getSelectionModel().getSelectedItem();
        if (selected != null) {
            listExtraBarcodes.getItems().remove(selected);
        }
    }

    public ObservableList<String> getItems() {
        return listExtraBarcodes.getItems();
    }

    public void setItems(List<String> barcodes) {
        listExtraBarcodes.setItems(FXCollections.observableArrayList(barcodes));
    }

    public void clear() {
        listExtraBarcodes.getItems().clear();
    }
}
