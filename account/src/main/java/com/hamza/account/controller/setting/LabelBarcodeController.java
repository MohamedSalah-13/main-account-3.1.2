package com.hamza.account.controller.setting;

import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;

import java.net.URL;
import java.util.ResourceBundle;

public class LabelBarcodeController implements Initializable {

    private final BooleanProperty showName = new SimpleBooleanProperty();
    private final BooleanProperty showBarcode = new SimpleBooleanProperty();
    private final BooleanProperty showPrice = new SimpleBooleanProperty();
    @FXML
    private Label labelName, labelBarcode, labelPrice;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        labelName.setText(Setting_Language.NAME_ITEM);
        labelBarcode.setText(Setting_Language.WORD_BARCODE);
        labelPrice.setText(Setting_Language.PRICE);

        labelName.disableProperty().bind(showName);
        labelBarcode.disableProperty().bind(showBarcode);
        labelPrice.disableProperty().bind(showPrice);
    }


    public BooleanProperty showNameProperty() {
        return showName;
    }

    public BooleanProperty showBarcodeProperty() {
        return showBarcode;
    }

    public BooleanProperty showPriceProperty() {
        return showPrice;
    }
}
