package com.hamza.account.config;

import com.hamza.account.Main;
import javafx.fxml.FXMLLoader;

public class FxmlConstants {


    public final FXMLLoader labelBarcode = fxmlLoader("setting/labelBarcode.fxml");

    private FXMLLoader fxmlLoader(String s) {
        return new FXMLLoader(Main.class.getResource("view/" + s));
    }

}
