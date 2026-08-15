package com.hamza.account.config;

import com.hamza.account.Main;
import javafx.fxml.FXMLLoader;

public class FxmlConstants {

    public final FXMLLoader rightPane = fxmlLoader("include/mainRightPane-view.fxml");

    private FXMLLoader fxmlLoader(String s) {
        return new FXMLLoader(Main.class.getResource("view/" + s));
    }

}
