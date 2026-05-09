package com.hamza.account.config;

import com.hamza.account.Main;
import javafx.fxml.FXMLLoader;

public class FxmlConstants {

    public final FXMLLoader ADMIN_APP = fxmlLoader("setting-admin.fxml");


    //----------------------------------- include------------------------------------------//
    public final FXMLLoader rightPane = fxmlLoader("include/mainRightPane-view.fxml");
    public final FXMLLoader menuBar = fxmlLoader("include/main-menu.fxml");
    public final FXMLLoader mainToolbar = fxmlLoader("include/mainToolbar-view.fxml");
    public final FXMLLoader labelBarcode = fxmlLoader("include/labelBarcode.fxml");
    public final FXMLLoader toolbarReports = fxmlLoader("include/toolbar-reports.fxml");


    /**
     * Creates and returns an FXMLLoader instance for loading the specified FXML resource.
     *
     * @param s the name of the FXML file to load.
     * @return an FXMLLoader instance configured to load the specified FXML resource.
     */
    private FXMLLoader fxmlLoader(String s) {
        return new FXMLLoader(Main.class.getResource("view/" + s));
    }

}
