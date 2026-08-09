package com.hamza.account.view.barcode;

import com.hamza.account.view.OpenApplication;
import javafx.collections.ObservableList;

public class PrintBarcodeApp {

    public PrintBarcodeApp(ObservableList<PrintBarcodeModel> observableList) throws Exception {
        new OpenApplication<>(new PrintBarcode(observableList));
    }
}
