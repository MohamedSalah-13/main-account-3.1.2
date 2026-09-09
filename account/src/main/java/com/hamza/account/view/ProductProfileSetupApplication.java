package com.hamza.account.view;

import com.hamza.account.Main;
import com.hamza.account.config.ThemeManager;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Standalone technician utility for authoring and applying signed client editions. */
public final class ProductProfileSetupApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        LanguageManager language = LanguageManager.getInstance();
        FXMLLoader loader = new FXMLLoader(
                Main.class.getResource("view/product-profile-setup.fxml"), language.getResourceBundle());
        Scene scene = new Scene(loader.load(), 780, 720);
        ThemeManager.apply(scene);
        ChangeOrientation.sceneOrientation(scene);
        stage.setTitle(language.getString("product.profile.setup.title"));
        stage.setMinWidth(700);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
    }
}
