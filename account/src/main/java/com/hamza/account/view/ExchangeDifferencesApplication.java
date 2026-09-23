package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.reports.ExchangeDifferencesController;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * The exchange differences report's window (docs/currency-plan.md §16): a stage of its own, so the screen it
 * was opened from - the currencies, the profit and loss, the reports hub - stays usable beside it.
 */
public class ExchangeDifferencesApplication extends Application {

    public static String title() {
        return LanguageManager.getInstance().getString("currency.difference.title");
    }

    @Override
    public void start(Stage stage) {
        Scene scene = new SceneAll(ExchangeDifferencesController.standard().pane());
        stage.setScene(scene);
        stage.setTitle(title());
        stage.getIcons().add(new Image(new Image_Setting().reports));
        stage.setResizable(true);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
    }
}
