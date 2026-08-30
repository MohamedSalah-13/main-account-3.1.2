package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.convert_treasury.TreasuryCashController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

/** Opens the screen that records a deposit into a treasury, or a withdrawal from one. */
@RequiredArgsConstructor
public class OpenTreasuryCashApplication extends Application {

    private final DaoFactory daoFactory;

    public static String title() {
        return LanguageManager.getInstance().getString("treasury.cash.title");
    }

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(new OpenFxmlApplication(new TreasuryCashController(daoFactory)).getPane());
        stage.setScene(scene);
        stage.setTitle(title());
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().tools));
        stage.setResizable(true);
        stage.show();
    }
}
