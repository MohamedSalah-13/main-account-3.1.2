package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.MainScreenController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ChangeOrientation;
import com.hamza.controlsfx.util.Screen_Size;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Properties;

import static com.hamza.account.config.PropertiesName.setAppLastRunVersion;

/** Builds the main scene on the primary stage; it is not a second JavaFX Application. */
public final class MainScreenApplication {

    private final DaoFactory daoFactory;
    private final ApplicationNavigator navigator;

    public MainScreenApplication(DaoFactory daoFactory, ApplicationNavigator navigator) {
        this.daoFactory = daoFactory;
        this.navigator = navigator;
    }

    private static String getAppVersion() {
        try (var input = MainScreenApplication.class.getResourceAsStream("/version.properties")) {
            if (input != null) {
                Properties properties = new Properties();
                properties.load(input);
                String version = properties.getProperty("app.version");
                if (version != null && !version.isBlank()) return version;
            }
        } catch (Exception ignored) {
        }
        return "dev";
    }

    public void show(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("mainScreen-view.fxml"),
                LanguageManager.getInstance().getResourceBundle());
        loader.setController(new MainScreenController(daoFactory));
        Parent root = loader.load();
        Scene scene = new SceneAll(root);
        ChangeOrientation.sceneOrientation(scene);

        Screen_Size.adjustStageToFullScreen(stage);
        String version = getAppVersion();
        stage.setTitle(LanguageManager.getInstance().getString("app.title.with.version", version));
        if (stage.getIcons().isEmpty()) stage.getIcons().add(new Image(new Image_Setting().tools));
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            event.consume();
            navigator.requestExit();
        });
        stage.show();
        setAppLastRunVersion(version);
    }
}
