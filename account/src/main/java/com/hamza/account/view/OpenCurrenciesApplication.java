package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.convert_treasury.CurrenciesController;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Opens the currencies screen in a window of its own - the sidebar's "open in a window" road. */
public class OpenCurrenciesApplication extends Application {

    public static String title() {
        return LanguageManager.getInstance().getString("currency.title");
    }

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(new OpenFxmlApplication(new CurrenciesController()).getPane());
        stage.setScene(scene);
        stage.setTitle(title());
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().tools));
        stage.setResizable(true);
        stage.show();
    }
}
