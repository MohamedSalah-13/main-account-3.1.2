package com.hamza.account.view;

import com.hamza.account.controller.items.PrintBarcode;
import com.hamza.account.controller.model.PrintBarcodeModel;
import javafx.collections.ObservableList;

public class PrintBarcodeApp {

    public PrintBarcodeApp(ObservableList<PrintBarcodeModel> observableList) throws Exception {
        new OpenApplication<>(new PrintBarcode(observableList));
    }
}
