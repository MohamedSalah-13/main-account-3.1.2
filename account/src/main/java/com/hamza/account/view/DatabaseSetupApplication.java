package com.hamza.account.view;

import com.hamza.account.Main;
import com.hamza.account.config.ThemeManager;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

/** Standalone launcher used by the technician before the accounting application starts. */
public final class DatabaseSetupApplication extends Application {

    /**
     * The same picture as {@code database-setup.ico}, which jpackage puts on the executable.
     * Without it the window and its taskbar button show the stock Java icon, so a technician
     * with the tool and the program open at once cannot tell the two apart.
     */
    static final String WINDOW_ICON = "/database-setup-icon.png";

    @Override
    public void start(Stage stage) throws Exception {
        LanguageManager language = LanguageManager.getInstance();
        FXMLLoader loader = new FXMLLoader(
                Main.class.getResource("view/database-setup.fxml"), language.getResourceBundle());
        Scene scene = new Scene(loader.load(), 720, 670);
        ThemeManager.apply(scene);
        ChangeOrientation.sceneOrientation(scene);
        stage.setTitle(language.getString("dbsetup.title"));
        try (InputStream icon = DatabaseSetupApplication.class.getResourceAsStream(WINDOW_ICON)) {
            if (icon != null) {
                stage.getIcons().add(new Image(icon));
            }
        }
        stage.setMinWidth(660);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
