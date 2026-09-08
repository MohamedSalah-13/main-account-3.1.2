package com.hamza.account.view;

import com.hamza.account.Main;
import com.hamza.account.config.ThemeManager;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Standalone launcher used by the technician before the accounting application starts. */
public final class DatabaseSetupApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        LanguageManager language = LanguageManager.getInstance();
        FXMLLoader loader = new FXMLLoader(
                Main.class.getResource("view/database-setup.fxml"), language.getResourceBundle());
        Scene scene = new Scene(loader.load(), 720, 670);
        ThemeManager.apply(scene);
        ChangeOrientation.sceneOrientation(scene);
        stage.setTitle(language.getString("dbsetup.title"));
        stage.setMinWidth(660);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
